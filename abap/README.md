# Cliente ABAP — ForSign SAP Integration Kit

Cliente ABAP pronto para ECC 7.40+ e S/4HANA on-premise que consome os
endpoints do kit no SAP Integration Suite. Reduz a integração no ERP a
**3 chamadas de método**.

## Conteúdo

| Objeto | Tipo | O que é |
|---|---|---|
| `ZCL_FORSIGN_CLIENT` | Classe | Cliente do contrato canônico: `create_operation`, `get_status`, `download_document`, `download_document_binary` (xstring pronto p/ ArchiveLink/GOS) |
| `ZCX_FORSIGN_ERROR` | Classe de exceção | Carrega o HTTP status e o erro canônico do kit |
| `ZFORSIGN_DEMO` | Programa | Demo end-to-end: seleciona PDF local → cria operação → mostra links e status |

## Instalação

**Via abapGit (recomendado):** crie um repositório online no abapGit apontando
para `https://github.com/forsign-digital/sap-integration-kit` num pacote Z
(ex. `ZFORSIGN`). O `.abapgit.xml` na raiz do repositório já aponta para
`/abap/src/` — o pull instala as classes e o programa de demo.

**Manual:** crie as duas classes e o programa via SE24/SE38 colando os fontes
de `src/`.

Dependência: `/ui2/cl_json` (padrão em qualquer NetWeaver ≥ 7.40 com UI add-on).

## Configuração (SM59)

Destino RFC **tipo G** chamado `FORSIGN_CPI`:

| Campo | Valor |
|---|---|
| Target Host | `<runtime>.it-cpi<NNN>.cfapps.<region>.hana.ondemand.com` |
| Service No. | `443` |
| Path Prefix | *(vazio — o cliente envia `/http/forsign/v1/...`)* |
| SSL | Ativo (importe a cadeia de certificados no STRUST, nó SSL Client Standard) |
| Logon | Basic — usuário/senha da service key da instância de runtime do CPI |

## Uso

```abap
DATA(lo_forsign) = NEW zcl_forsign_client( ). " default FORSIGN_CPI

" Criar operação (upload + criação em uma chamada)
DATA(ls_response) = lo_forsign->create_operation( VALUE #(
  external_id        = |S4-{ lv_ebeln }|
  name               = |Contrato { lv_ebeln }|
  language           = 'pt-br'
  merge_if_exists    = abap_true          " idempotência em retry
  document           = VALUE #( file_name = 'contrato.pdf' content_base64 = lv_b64 )
  signers            = VALUE #(
    ( name = 'Fornecedor' email = lv_email order = 1
      signature_type = 'Draw'
      signatures = VALUE #( ( print_signature = abap_true
        positions = VALUE #( ( page = 1 coordenate_x = '10.5%' coordenate_y = '80.0%' ) ) ) ) ) )
  metadata           = VALUE #( ( key = 'SAP_OBJECT_KEY' value = lv_ebeln ) ) ) ).

" ls_response-operation_id  -> guarde no seu documento SAP
" ls_response-members[]-sign_url -> links de assinatura

" Status
DATA(ls_status) = lo_forsign->get_status( ls_response-operation_id ).

" Download (após webhook DocumentReady) direto em xstring
lo_forsign->download_document_binary(
  EXPORTING iv_operation_id = ls_response-operation_id
            iv_document_id  = ls_response-documents[ 1 ]-document_id
  IMPORTING ev_file_name    = DATA(lv_name)
            ev_content      = DATA(lv_pdf) ).  " -> ArchiveLink/GOS/DMS
```

Regras do contrato (validadas pelo iFlow): tipos visuais (`Draw`, `Text`,
`UserChoice`) exigem `signatures[]-positions`; `SMS`/`Whatsapp` exigem `phone`.
Referência completa: [`../openapi.yaml`](../openapi.yaml).

## Teste rápido

Execute `ZFORSIGN_DEMO` (SE38): informe o destino, um PDF local e o e-mail do
assinante de teste. O programa cria a operação e imprime `operationId`, links
de assinatura e o status.
