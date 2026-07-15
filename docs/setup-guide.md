# Setup — ForSign SAP Integration Kit no Integration Suite

Passo a passo para colocar os 4 iFlows em produção num tenant do
SAP Integration Suite (Cloud Integration).

## Pré-requisitos

- Tenant do SAP Integration Suite com a capability **Cloud Integration** ativa.
- API Key do ForSign (GUID da company — painel ForSign, configurações de integração).
- Uma **service key** da instância de processo (plano `integration-flow`) para os
  sistemas SAP chamarem os endpoints dos iFlows.

## 1. Importar os iFlows

```bash
./build.sh
```

No Integration Suite: **Design → (crie um pacote, ex.: "ForSign") → Add →
Integration Flow → Upload** — um upload por zip de `dist/`:

- `ForSign_CreateOperation.zip`
- `ForSign_OperationStatus.zip`
- `ForSign_DownloadDocument.zip`
- `ForSign_WebhookReceiver.zip`

Abra cada iFlow no editor e salve uma vez (o editor normaliza o modelo BPMN).

## 2. Security Material

**Monitor → Manage Security → Security Material → Create**:

| Tipo | Nome/Alias | Valor |
|---|---|---|
| Secure Parameter | `ForSign_ApiKey` | API Key (GUID) da company no ForSign |
| Secure Parameter | `ForSign_WebhookSecret` | `secret` retornado no registro do webhook (passo 5) |
| User Credentials | `SAP_Target_Basic` | usuário/senha do endpoint SAP que receberá os eventos de webhook (só p/ `ForSign_WebhookReceiver`) |

Os aliases `ForSign_ApiKey` e `ForSign_WebhookSecret` são referenciados pelos
scripts Groovy — se usar outros nomes, ajuste os scripts.

## 3. Parâmetros externalizados

Em cada iFlow, **Configure**:

| Parâmetro | iFlows | Default | Descrição |
|---|---|---|---|
| `ForSign_BaseUrl` | Create/Status/Download | `https://api.forsign.digital` | URL base da API ForSign (sem barra final) |
| `ForSign_RequestTimeout` | todos | `60000` | Timeout das chamadas HTTP (ms) |
| `SAP_Target_Endpoint` | WebhookReceiver | — | URL do endpoint SAP que recebe o evento canônico (OData/REST/ICF) |
| `SAP_Target_CredentialName` | WebhookReceiver | `SAP_Target_Basic` | Alias da credencial Basic do endpoint SAP |

## 4. Deploy e URLs

Faça o **Deploy** dos 4 iFlows. Em **Monitor → Manage Integration Content**, copie
as URLs geradas (formato `https://<runtime>.it-cpi<NNN>.cfapps.<region>.hana.ondemand.com/http/...`):

- `POST .../http/forsign/v1/operations`
- `GET  .../http/forsign/v1/operations/status?operationId=...`
- `GET  .../http/forsign/v1/operations/download?operationId=...&documentId=...`
- `POST .../http/forsign/v1/webhook`

## 5. Registrar o webhook no ForSign

Com a URL do `ForSign_WebhookReceiver` deployada:

```bash
curl -X POST "https://api.forsign.digital/api/v2/webhooks/register" \
  -H "X-Api-Key: <SUA_API_KEY>" \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://<runtime>.../http/forsign/v1/webhook",
    "subscribed": true,
    "action": "DocumentReady",
    "webHookName": "SAP Integration Kit",
    "headers": []
  }'
```

> **`DocumentReady` é o gatilho certo para o download** — ele dispara quando os
> PDFs finais já estão selados. `CompletedOperation` dispara antes disso; registre-o
> também (segundo webhook) se o SAP precisar do evento de negócio "todos assinaram"
> separado do evento técnico "arquivos prontos".

A resposta contém o campo **`secret`** — salve-o como Secure Parameter
`ForSign_WebhookSecret` (passo 2) e redeploye o `ForSign_WebhookReceiver`.

> Atenção: o endpoint do webhook no CPI exige autenticação role-based. Se o ForSign
> precisar chamar sem credencial do BTP, adicione um header estático de autenticação
> no registro (`headers`) ou exponha o iFlow via API Management. A validação HMAC do
> iFlow protege a integridade do payload em qualquer um dos casos.

Outros eventos disponíveis (`action`): `UpdateOperation`, `DocumentSigned`,
`CompletedOperation`, `DocumentReady`, `AttachmentFilled`, `FormFilled`, `SelfieCaptured`.

## 6. Teste ponta a ponta

```bash
CPI=https://<runtime>...   # base dos endpoints do CPI
AUTH='-u <clientid>:<clientsecret>'   # service key da instância de runtime

# 1. Criar operação (documento pequeno de teste)
curl $AUTH -X POST "$CPI/http/forsign/v1/operations" \
  -H "Content-Type: application/json" \
  -d @samples/create-operation-request.json
# → guarde "operationId" da resposta

# 2. Consultar status
curl $AUTH "$CPI/http/forsign/v1/operations/status?operationId=<ID>"

# 3. Assinar o documento pelos members[].signUrl retornados na criação
#    (signUrl vazio = ainda não é a vez do assinante, em operação sequencial)

# 4. Após o webhook DocumentReady, baixar o documento assinado.
#    documentId = documents[].documentId da resposta da criação (id numérico
#    do arquivo na operação — também vem no webhook em documents[].documentId)
curl $AUTH "$CPI/http/forsign/v1/operations/download?operationId=<ID>&documentId=<DOC_ID>"
```

Para depurar, use **Monitor → Monitor Message Processing** com log level `Trace`
no iFlow em teste.

## Blueprint manual (fallback)

Se preferir recriar um iFlow no editor em vez de importar o zip, todos seguem o
mesmo esqueleto — sender HTTPS + Groovy Scripts + Request-Reply HTTP:

**ForSign_CreateOperation** — `POST /forsign/v1/operations`
1. Sender **HTTPS** (`/forsign/v1/operations`, user role `ESBMessaging.send`, CSRF off)
2. **Groovy Script** `create_operation_prepare_upload.groovy`
3. **Request Reply** → receiver **HTTP**: `{{ForSign_BaseUrl}}/api/v2/document/upload-base64`, POST, timeout `{{ForSign_RequestTimeout}}`, auth None
4. **Groovy Script** `create_operation_build_request.groovy`
5. **Request Reply** → receiver **HTTP**: `{{ForSign_BaseUrl}}/api/v1/operation`, POST
6. **Groovy Script** `create_operation_map_response.groovy` → End

**ForSign_OperationStatus** — `GET /forsign/v1/operations/status`
1. Sender HTTPS → 2. Script `status_prepare_request.groovy` →
3. Request Reply HTTP GET `{{ForSign_BaseUrl}}/api/v2/operation/${property.operationId}/status` →
4. Script `unwrap_response.groovy` → End

**ForSign_DownloadDocument** — `GET /forsign/v1/operations/download`
1. Sender HTTPS → 2. Script `download_prepare_request.groovy` →
3. Request Reply HTTP GET `{{ForSign_BaseUrl}}/api/v2/operations/${property.operationId}/documents/${property.documentId}/download-base64` →
4. Script `unwrap_response.groovy` → End

**ForSign_WebhookReceiver** — `POST /forsign/v1/webhook`
1. Sender HTTPS → 2. Script `webhook_validate_hmac.groovy` →
3. Script `webhook_map_event.groovy` →
4. Request Reply HTTP POST `{{SAP_Target_Endpoint}}` (Basic, credencial `{{SAP_Target_CredentialName}}`) → End

Todos os scripts estão em [`scripts/`](../scripts/) com comentários de contexto.

## Solução de problemas

| Sintoma | Causa provável |
|---|---|
| 401 do ForSign | `ForSign_ApiKey` ausente/errada no Security Material (precisa ser o GUID exato da company) |
| `Secure Parameter 'ForSign_ApiKey' nao encontrado` | Alias divergente no Security Material |
| Upload OK mas criação falha com 403 | O usuário dono da API Key não tem a permissão de criação de operação (policies `CreateOperationWithoutModel`/`UseOperationModel`) |
| Webhook rejeitado (`assinatura HMAC invalida`) | `ForSign_WebhookSecret` desatualizado — o secret muda ao regenerar (`PUT /api/v2/webhooks/{id}/secret/regenerate`) |
| Timeout no upload | Documentos grandes: aumente `ForSign_RequestTimeout` e o `maximumBodySize` do sender HTTPS (padrão 40 MB) |
