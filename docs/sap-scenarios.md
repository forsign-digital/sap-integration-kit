# Cenários SAP — S/4HANA/ECC e SuccessFactors

Como cada sistema SAP consome os endpoints do kit. Os iFlows são agnósticos —
o que muda por cenário é quem chama, com qual payload e como o retorno é tratado.

## S/4HANA / ECC

**Casos típicos:** contratos de compra/venda vinculados a pedidos (ME21N/VA01),
aditivos contratuais, cartas de anuência, documentos fiscais e de RH on-premise.

### Chamada de saída (ABAP → CPI)

- **S/4HANA Cloud/on-premise moderno:** Communication Arrangement/Outbound Service
  apontando para a URL do iFlow, ou `cl_web_http_client` (S/4) / `cl_http_client` (ECC).
- **ECC:** destino RFC tipo G (SM59) para o host do CPI + `cl_http_client`.

Esqueleto ABAP (ECC/S4 on-premise):

```abap
DATA(lo_dest) = cl_http_destination_provider=>create_by_url(
  'https://<runtime>.../http/forsign/v1/operations' ).
DATA(lo_client) = cl_web_http_client_manager=>create_by_http_destination( lo_dest ).
DATA(lo_req) = lo_client->get_http_request( ).
lo_req->set_header_field( i_name = 'Content-Type' i_value = 'application/json' ).
" Basic auth da service key do CPI (ou OAuth2 via /UI2/CL_OAUTH2...)
lo_req->set_authorization_basic( i_username = lv_client_id i_password = lv_secret ).
lo_req->set_text( lv_json_payload ).  " ver samples/create-operation-request.json
DATA(lo_resp) = lo_client->execute( if_web_http_client=>post ).
```

Convenções de payload para o ERP:

- `externalId`: chave do objeto SAP — ex. `S4-<EBELN>` (pedido) ou `<VBELN>` (ordem
  de venda). Volta no webhook para correlação.
- `metadata`: inclua `SAP_SYSTEM` (SID), `SAP_OBJECT_TYPE` e `SAP_OBJECT_KEY` para
  reconciliação e auditoria.
- O PDF pode vir de smartform/Adobe Forms convertido com `cl_bcs_convert` para base64
  (`document.contentBase64`).

### Retorno (webhook → ERP)

Configure `SAP_Target_Endpoint` do `ForSign_WebhookReceiver` para um serviço ICF/REST
(SICF handler ou OData) que:

1. Localiza o objeto SAP pelo `externalId`/`metadata`.
2. Atualiza o status do documento (ex.: libera o pedido, muda status do contrato).
3. Opcionalmente chama o iFlow de download (`documents[].documentId` vem no evento)
   e arquiva o PDF assinado via ArchiveLink/GOS/DMS.

## SuccessFactors

**Casos típicos:** documentos de admissão (Onboarding), contratos de trabalho,
aditivos, termos de política interna, desligamento (Offboarding).

### Disparo (SFSF → CPI)

- **Onboarding/Offboarding 2.0:** Intelligent Services Center (ISC) — evento
  (ex. *Employee Hire*) com destino Integration = iFlow adicional que busca os dados
  do colaborador (OData `PerPerson`/`EmpEmployment`), gera/busca o PDF e chama
  `POST /http/forsign/v1/operations` deste kit.
- **Integration Center:** integração de saída agendada/por evento chamando o
  endpoint do kit diretamente (destino REST) quando o PDF já existe como anexo.

Convenções de payload para RH:

- `externalId`: `SFSF-<userId>-<templateId>` ou o ID do processo de onboarding.
- `signers`: o colaborador (email pessoal durante o onboarding!) e o representante
  de RH — use `sequentialSigning: true` com RH por último quando a política exigir.
- `signatureType`: `Click` costuma bastar para documentos internos; use
  `CloudCertificate`/`Certificate` para contratos que exijam ICP-Brasil.
- `expirationDate`: alinhe ao prazo de admissão.

### Retorno (webhook → SFSF)

Aponte `SAP_Target_Endpoint` para um iFlow/endpoint que, ao receber
`DocumentReady` (PDFs finais selados):

1. Baixa o PDF assinado (`ForSign_DownloadDocument`).
2. Anexa ao perfil do colaborador via OData `Attachment` (módulo Document
   Management) ou atualiza a tarefa de onboarding correspondente.

## Matriz rápida

| | S/4HANA / ECC | SuccessFactors |
|---|---|---|
| Disparo | ABAP outbound / Communication Arrangement | ISC / Integration Center |
| `externalId` | chave do documento/pedido (EBELN, VBELN...) | userId + processo |
| Retorno | ICF/OData → atualiza objeto + ArchiveLink | OData Attachment / tarefa de onboarding |
| Assinatura típica | `Click`/`Certificate` (ICP-Brasil p/ contratos) | `Click`; `CloudCertificate` p/ contratos de trabalho |
