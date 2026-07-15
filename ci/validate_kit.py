#!/usr/bin/env python3
"""Validação estrutural do ForSign SAP Integration Kit (roda em CI, sem Groovy).

Verifica:
- XML dos .iflw bem-formado e com steps referenciando scripts existentes;
- todo script Groovy referenciado nos iFlows está no mapeamento do build.sh;
- parameters.prop cobre todos os placeholders {{...}} usados nos .iflw;
- samples JSON válidos;
- build.sh gera os 4 zips com a estrutura esperada.
"""
import json
import pathlib
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

KIT = pathlib.Path(__file__).resolve().parent.parent
ERRORS = []


def check(condition, message):
    if not condition:
        ERRORS.append(message)
        print(f"  FAIL  {message}")
    else:
        print(f"  OK    {message}")


print("== iFlows ==")
iflows = sorted(KIT.glob("iflows/*/src/main/resources/scenarioflows/integrationflow/*.iflw"))
check(len(iflows) == 4, f"4 iFlows encontrados (achou {len(iflows)})")

build_sh = (KIT / "build.sh").read_text()
scripts_dir = {p.name for p in (KIT / "scripts").glob("*.groovy")}

for iflw in iflows:
    name = iflw.stem
    try:
        root = ET.parse(iflw).getroot()
    except ET.ParseError as e:
        check(False, f"{name}: XML bem-formado ({e})")
        continue
    check(True, f"{name}: XML bem-formado")

    text = iflw.read_text()

    # Scripts referenciados existem e estão no build.sh
    referenced = set(re.findall(r"<value>(\w+\.groovy)</value>", text))
    for script in sorted(referenced):
        check(script in scripts_dir, f"{name}: script {script} existe em scripts/")
        check(script in build_sh, f"{name}: script {script} mapeado no build.sh")

    # Exception subprocess presente
    check("SubProcess_Error" in text, f"{name}: Exception Subprocess presente")
    check("error_response.groovy" in referenced, f"{name}: error_response.groovy no subprocess")

    # Placeholders externalizados declarados em parameters.prop
    params_file = iflw.parents[2] / "parameters.prop"
    declared = set(re.findall(r"^(\w+)=", params_file.read_text(), re.MULTILINE))
    used = set(re.findall(r"\{\{(\w+)\}\}", text))
    for param in sorted(used):
        check(param in declared, f"{name}: parametro {{{{{param}}}}} declarado em parameters.prop")

print("== samples ==")
for sample in sorted(KIT.glob("samples/*.json")):
    try:
        json.loads(sample.read_text())
        check(True, f"samples/{sample.name}: JSON valido")
    except json.JSONDecodeError as e:
        check(False, f"samples/{sample.name}: JSON valido ({e})")

print("== openapi/postman ==")
openapi = KIT / "openapi.yaml"
check(openapi.exists(), "openapi.yaml existe")
postman = KIT / "postman" / "ForSign_SAP_Kit.postman_collection.json"
if postman.exists():
    json.loads(postman.read_text())
    check(True, "postman collection: JSON valido")
else:
    check(False, "postman collection existe")

print("== build ==")
result = subprocess.run(["bash", str(KIT / "build.sh")], capture_output=True, text=True)
check(result.returncode == 0, f"build.sh executa sem erro ({result.stderr.strip()[:120]})")
for zip_path in sorted((KIT / "dist").glob("*.zip")):
    with zipfile.ZipFile(zip_path) as zf:
        names = zf.namelist()
        has_iflw = any(n.endswith(".iflw") for n in names)
        has_manifest = "META-INF/MANIFEST.MF" in names
        has_scripts = any(n.startswith("src/main/resources/script/") and n.endswith(".groovy") for n in names)
        check(has_iflw and has_manifest and has_scripts,
              f"dist/{zip_path.name}: estrutura completa (iflw + manifest + scripts)")

print()
if ERRORS:
    print(f"{len(ERRORS)} problema(s) encontrado(s).")
    sys.exit(1)
print("Kit valido.")
