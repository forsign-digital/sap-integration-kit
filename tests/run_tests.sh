#!/usr/bin/env bash
#
# Roda a suite de testes dos scripts Groovy do kit.
# Usa o groovy local se existir; caso contrário, cai para Docker (imagem groovy).
#
set -euo pipefail

KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if command -v groovy >/dev/null 2>&1; then
    exec groovy -cp "$KIT_DIR/tests/mocks" "$KIT_DIR/tests/run_all_tests.groovy"
fi

if command -v docker >/dev/null 2>&1; then
    echo "groovy nao encontrado — usando Docker (groovy:4-jdk17)..."
    exec docker run --rm -v "$KIT_DIR":/kit -w /kit groovy:4-jdk17 \
        groovy -cp /kit/tests/mocks /kit/tests/run_all_tests.groovy
fi

echo "ERRO: instale groovy (brew install groovy) ou docker para rodar os testes." >&2
exit 1
