# ForSign SAP Integration Kit

> **[English documentation →](README.md)**

Kit de integração para o **SAP Integration Suite (Cloud Integration / CPI)** que conecta
sistemas SAP (S/4HANA, ECC, SuccessFactors) à API pública do ForSign para o fluxo
completo de assinatura digital:

```
S/4HANA ──┐                        ┌──> POST /api/v2/document/upload-base64
ECC ──────┤──> SAP Integration ────┤──> POST /api/v1/operation
SFSF ─────┘    Suite (iFlows)      ├──> GET  /api/v2/operation/{id}/status
                   ▲               └──> GET  /api/v2/operations/{id}/documents/{docId}/download-base64
                   │
              webhook HMAC
            (DocumentReady)
                   ▲
               ForSign API
```

## Conteúdo

| iFlow | Endpoint exposto no CPI | O que faz |
|---|---|---|
| `ForSign_CreateOperation` | `POST /http/forsign/v1/operations` | Recebe payload canônico do SAP, faz upload do documento (base64) e cria a operação de assinatura. Retorna `operationId` e links de assinatura. |
| `ForSign_OperationStatus` | `GET /http/forsign/v1/operations/status?operationId=...` | Consulta o status da operação e o progresso de cada signatário. |
| `ForSign_DownloadDocument` | `GET /http/forsign/v1/operations/download?operationId=...&documentId=...` | Baixa o documento final assinado como `{ fileName, contentType, base64Content }`. |
| `ForSign_WebhookReceiver` | `POST /http/forsign/v1/webhook` | Recebe webhooks do ForSign, **valida a assinatura HMAC-SHA256** (`X-ForSign-Signature`) e encaminha um evento canônico ao endpoint SAP configurado. |

```
sap-integration-kit/
├── build.sh                # gera dist/*.zip importáveis no Integration Suite
├── .github/workflows/      # CI: validação estrutural + testes + release
├── openapi.yaml            # OpenAPI 3.0 do contrato canônico
├── iflows/                 # fonte dos 4 iFlows (BPMN + metadados + parâmetros)
├── scripts/                # scripts Groovy (copiados p/ cada pacote pelo build)
├── tests/                  # testes unitários dos scripts (mocks do CPI incluídos)
├── ci/validate_kit.py      # validação estrutural (XML, params, zips)
├── samples/                # payloads de exemplo (request/response/webhook)
├── postman/                # coleção Postman dos endpoints do kit
├── abap/                   # cliente ABAP (ZCL_FORSIGN_CLIENT) + demo, via abapGit
├── fiori/                  # ForSign Signature Cockpit (SAPUI5)
└── docs/
    ├── setup-guide.md      # import, Security Material, webhook e teste e2e
    ├── sap-scenarios.md    # cenários S/4HANA/ECC e SuccessFactors
    └── hardening.md        # OAuth2, JMS, monitoramento, transporte, LGPD
```

Todos os iFlows têm **Exception Subprocess**: qualquer falha (validação, HTTP
do ForSign, HMAC, timeout) vira `{ "error": { httpStatus, type, message,
forsignMessages } }` com o status HTTP correto — nunca stacktrace.

Qualidade: `tests/run_tests.sh` roda a suite dos scripts Groovy (19 testes,
usa groovy local ou Docker); `ci/validate_kit.py` valida a estrutura completa;
o GitHub Actions amarra os dois no CI e publica os zips em cada release (tag `v*`).

## Quick start

```bash
./build.sh
# importe cada dist/*.zip em: Integration Suite > Design > (pacote) > Add > Integration Flow > Upload
```

Depois siga o [guia de setup](docs/setup-guide.md): criar os Secure Parameters
(`ForSign_ApiKey`, `ForSign_WebhookSecret`), configurar os parâmetros externalizados
(`ForSign_BaseUrl`, `SAP_Target_Endpoint`, ...) e fazer o deploy.

Do lado SAP, escolha o consumo que preferir:

- **CPI direto** — qualquer HTTP client com a service key (contrato em
  [`openapi.yaml`](openapi.yaml), exemplos na [coleção Postman](postman/)).
- **ABAP** — instale [`abap/`](abap/README.md) via abapGit e chame
  `ZCL_FORSIGN_CLIENT` (3 métodos); `ZFORSIGN_DEMO` faz o fluxo completo.
- **Fiori** — deploy do [`fiori/`](fiori/README.md) para acompanhar operações
  e baixar o PDF assinado no Launchpad.

## Contrato canônico (lado SAP)

O kit define um payload canônico simples para o SAP — os scripts Groovy fazem a
tradução para o `OperationViewModel` do ForSign. Exemplos completos em [`samples/`](samples/).

**Criação** (`samples/create-operation-request.json`): `externalId` (chave do objeto SAP,
ex.: nº do pedido/nº do colaborador — o ForSign devolve esse campo no webhook),
`name`, `language` (`pt-br`/`en`/`es`), `document.{fileName, contentBase64}`,
`signers[]`, `sequentialSigning`, `manualFinish.{hasManualFinish, date}` (prazo),
`metadata[]`.

Regras importantes de `signers[]` (validadas pelo iFlow antes de chamar o ForSign):

- **Tipos visuais de assinatura (`Draw`, `Text`, `Stamp`, `UserChoice`, `AutomaticStamp`)
  exigem posição**: `signatures[].positions[].{page, coordenateX, coordenateY}` com
  coordenadas em % (ex.: `"4.93%"`). O `documentId` dentro de `signatures`/`rubrics`/
  `formFields` **não precisa ser enviado** — o iFlow injeta o id do upload automaticamente.
- `notificationChannel`/`authenticationChannel` `SMS`/`Whatsapp` exigem `phone`
  (formato internacional `5511...`).
- `attachments[]` solicita arquivos ao assinante (`{name, description, fileType[],
  filesAllowed, inputAttachment[], required}`); `formFields[]` define formulários
  (`{type, name, fieldType, required, ...}`); `observer: true` cria um observador.
- Demais campos são repassados sem alteração ao ForSign (`cpf`, `role`,
  `doubleAuthentication`, `antifraudFeatures`, `formTitle`, ...), então qualquer
  recurso documentado em `/docs/operacoes/criar` fica acessível sem mudar o kit.

`signatureType`: `Click`, `Draw`, `Text`, `UserChoice`, `Certificate`,
`CloudCertificate` (os dois últimos consomem crédito de certificado).

**Resposta da criação** (`samples/create-operation-response.json`): `operationId`
**numérico** (usado em status/download/cancelar/completar), `members[].{id, signUrl,
shortSignUrl}` (`signUrl` vazio = ainda não é a vez do assinante) e
`documents[].documentId` — o id numérico que o download exige. A resposta bruta do
ForSign vem duplamente embrulhada (`data.data`); o iFlow já desembrulha.

**Status**: enum `status` da operação — `OperationCreated`, `InProgress`, `WaitingNotify`,
`WaitingSignatures`, `WaitingForms`, `CheckingAttachments`, `Completed`, `Canceled`, `Expired`.

**Webhook**: o ForSign assina cada webhook com HMAC-SHA256
(`X-ForSign-Signature: sha256=<hex>`); o iFlow rejeita payloads com assinatura inválida
e encaminha ao SAP o evento canônico de `samples/webhook-event-canonical.json` —
incluindo o `externalId` para correlação com o objeto SAP de origem.

## Autenticação

- **CPI → ForSign**: header `X-Api-Key` com a API Key da company (GUID, obtida no
  painel ForSign). Armazenada como **Secure Parameter** `ForSign_ApiKey` no
  Security Material — nunca em parâmetro externalizado ou no iFlow.
- **SAP → CPI**: os endpoints HTTPS dos iFlows usam autenticação role-based padrão
  do CPI (`ESBMessaging.send`) — use uma service key da instância de runtime
  (Basic ou OAuth2 client credentials).
- **ForSign → CPI (webhook)**: validação HMAC com o `secret` retornado no registro
  do webhook (`POST /api/v2/webhooks/register`), armazenado como Secure Parameter
  `ForSign_WebhookSecret`.

## Observações

- Os artefatos `.iflw` foram autorados fora do editor do Integration Suite. Após o
  primeiro import, abra cada iFlow no editor, confira os steps e salve — o editor
  normaliza o modelo. Alternativamente, o `docs/setup-guide.md` inclui o blueprint
  passo a passo para recriar qualquer iFlow manualmente em ~15 min.
- Polling de status vs. webhook: prefira webhooks e use o status apenas como
  fallback/reconciliação. **Para disparar o download use `DocumentReady`** —
  `CompletedOperation` dispara antes de o PDF final estar selado.
- O download retorna base64 dentro de JSON (ideal para ABAP/CPI). Para arquivar
  tudo de uma vez (PDFs assinados + anexos + CSV de formulários + trilha de
  auditoria) existe o ZIP:
  `GET /api/v2/operations/{operationId}/files/download/authenticated`.
