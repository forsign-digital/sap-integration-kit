# Hardening — recomendações enterprise

O que o kit já faz por padrão e o que ativar/estender em produção.

## Já embutido no kit

| Proteção | Como funciona |
|---|---|
| Erros estruturados | Todos os iFlows têm Exception Subprocess: qualquer falha vira JSON canônico `{ "error": { httpStatus, type, message, forsignMessages } }` com o HTTP status correto (400 validação, 401 HMAC, status original do ForSign, 504 timeout) — nunca stacktrace |
| Validação pré-voo | O payload é validado **antes** de chamar o ForSign (posições obrigatórias p/ tipos visuais, phone p/ SMS/Whatsapp, campos obrigatórios) — erros de contrato não gastam chamada nem crédito |
| Credenciais isoladas | API Key e webhook secret só existem no Security Material (Secure Parameter); o header `Authorization` da chamada SAP→CPI é removido antes de chamar o ForSign |
| Webhook autenticado | HMAC-SHA256 com comparação em tempo constante; payload sem assinatura válida nunca chega ao SAP |
| Idempotência (opt-in) | Envie `mergeIfExists: true` + `externalId` na criação: um retry do SAP não cria operação duplicada |

## Recomendado ativar em produção

### OAuth2 client credentials (SAP → CPI)

Os endpoints usam a autenticação role-based padrão do CPI, que aceita **Basic e
OAuth2** com a mesma service key. Para OAuth2 (recomendado): obtenha o token em
`https://<subdomain>.authentication.<region>.hana.ondemand.com/oauth/token`
(grant `client_credentials`) e envie como `Authorization: Bearer`. No ECC sem
suporte a OAuth nativo, o Basic da service key continua válido.

### Entrega garantida do webhook (JMS)

O `ForSign_WebhookReceiver` encaminha o evento ao SAP **sincronamente** — se o
endpoint SAP estiver fora, o ForSign recebe 500 e refaz o retry dele. Para
desacoplar (exige a feature JMS habilitada no tenant):

1. No `ForSign_WebhookReceiver`, troque o Request-Reply final por um **Send**
   com adapter **JMS** (queue `FORSIGN_EVENTS`) — o ForSign recebe 200 assim
   que o HMAC é validado.
2. Crie um iFlow `ForSign_WebhookDispatcher` com **sender JMS** na mesma queue
   e o Request-Reply HTTP para `{{SAP_Target_Endpoint}}`, com retry configurado
   na queue (JMS retry com backoff exponencial é nativo).

### Monitoramento e alertas

- **Correlação pela chave SAP:** nos scripts, o `externalId` está disponível —
  para busca no monitor, adicione um step Content Modifier gravando
  `SAP_ApplicationID = ${property.externalId}` (vira "Application Message ID"
  pesquisável em Monitor Message Processing).
- **Alertas:** configure Alert Rules (Monitor → Manage Alerts) para falhas dos
  4 iFlows; integre com e-mail/Teams via Alert Notification Service.
- Retenção de MPL: em falha recorrente, suba o log level do iFlow para `Debug`
  temporariamente — nunca deixe `Trace` permanente em produção (payloads com
  dados pessoais ficam no MPL).

### Transporte DEV → QA → PRD

Use **Cloud Transport Management (cTMS)** ou o content transport do próprio
Integration Suite: o pacote inteiro (4 iFlows + parâmetros externalizados) é
transportável; os valores de `ForSign_BaseUrl`/`SAP_Target_Endpoint` são
configurados por tenant (é para isso que são externalizados). Security
Material não é transportado — crie os Secure Parameters em cada tenant.

### LGPD / dados pessoais

Os payloads carregam nome, e-mail, telefone e CPF de assinantes:

- Não persista payloads (Data Store/variáveis) sem necessidade e sem TTL.
- Log level `Info` em produção (sem body).
- O ZIP de download inclui trilha de auditoria com IP dos assinantes — trate o
  armazenamento no SAP (ArchiveLink) com as mesmas restrições de acesso.
