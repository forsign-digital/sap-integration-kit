"! Cliente ABAP do ForSign SAP Integration Kit.
"!
"! Consome os endpoints expostos pelos iFlows do kit no SAP Integration Suite
"! (contrato canônico — ver openapi.yaml do kit). Compatível com ECC 7.40+ e
"! S/4HANA on-premise (cl_http_client + /ui2/cl_json).
"!
"! Pré-requisito: destino RFC tipo G (SM59) apontando para o runtime do
"! Cloud Integration, com Basic auth da service key e SSL ativo.
"! O path prefix /http fica no destino ou no iv_base_path.
"!
"! Exemplo mínimo (ver ZFORSIGN_DEMO):
"!   DATA(lo_client) = NEW zcl_forsign_client( iv_rfc_destination = 'FORSIGN_CPI' ).
"!   DATA(ls_response) = lo_client->create_operation( ls_request ).
CLASS zcl_forsign_client DEFINITION
  PUBLIC
  FINAL
  CREATE PUBLIC.

  PUBLIC SECTION.
    TYPES:
      BEGIN OF ty_position,
        page         TYPE i,
        coordenate_x TYPE string,
        coordenate_y TYPE string,
      END OF ty_position,
      ty_positions TYPE STANDARD TABLE OF ty_position WITH DEFAULT KEY,

      BEGIN OF ty_signature,
        document_id     TYPE string,       " opcional — o iFlow injeta o id do upload
        print_signature TYPE abap_bool,
        positions       TYPE ty_positions,
      END OF ty_signature,
      ty_signatures TYPE STANDARD TABLE OF ty_signature WITH DEFAULT KEY,

      BEGIN OF ty_attachment,
        name             TYPE string,
        description      TYPE string,
        file_type        TYPE string_table,
        files_allowed    TYPE i,
        input_attachment TYPE string_table,
        required         TYPE abap_bool,
      END OF ty_attachment,
      ty_attachments TYPE STANDARD TABLE OF ty_attachment WITH DEFAULT KEY,

      BEGIN OF ty_form_field,
        type        TYPE string,           " Others / Name / Email / DateNow
        name        TYPE string,
        field_type  TYPE string,           " Text / ...
        description TYPE string,
        required    TYPE abap_bool,
        max         TYPE i,
        order       TYPE i,
        document_id TYPE string,
        positions   TYPE ty_positions,
      END OF ty_form_field,
      ty_form_fields TYPE STANDARD TABLE OF ty_form_field WITH DEFAULT KEY,

      BEGIN OF ty_signer,
        name                   TYPE string,
        email                  TYPE string,
        phone                  TYPE string, " 5511... — obrigatório com SMS/Whatsapp
        role                   TYPE string,
        order                  TYPE i,
        observer               TYPE abap_bool,
        notification_channel   TYPE string, " Email / SMS / Whatsapp / None
        authentication_channel TYPE string,
        double_authentication  TYPE abap_bool,
        signature_type         TYPE string, " Click / Draw / Text / UserChoice / Certificate / CloudCertificate
        form_title             TYPE string,
        signatures             TYPE ty_signatures,
        attachments            TYPE ty_attachments,
        form_fields            TYPE ty_form_fields,
      END OF ty_signer,
      ty_signers TYPE STANDARD TABLE OF ty_signer WITH DEFAULT KEY,

      BEGIN OF ty_document,
        file_name      TYPE string,
        description    TYPE string,
        content_base64 TYPE string,
      END OF ty_document,

      BEGIN OF ty_manual_finish,
        has_manual_finish TYPE abap_bool,
        date              TYPE string,     " ISO-8601, ex. 2026-08-15T23:59:59-03:00
      END OF ty_manual_finish,

      BEGIN OF ty_key_value,
        key   TYPE string,
        value TYPE string,
      END OF ty_key_value,
      ty_metadata TYPE STANDARD TABLE OF ty_key_value WITH DEFAULT KEY,

      BEGIN OF ty_create_request,
        external_id        TYPE string,    " chave do objeto SAP (EBELN, VBELN...)
        name               TYPE string,
        language           TYPE string,    " pt-br / en / es
        sequential_signing TYPE abap_bool,
        merge_if_exists    TYPE abap_bool, " idempotência em retry
        manual_finish      TYPE ty_manual_finish,
        optional_message   TYPE string,
        display_cover      TYPE abap_bool,
        document           TYPE ty_document,
        signers            TYPE ty_signers,
        metadata           TYPE ty_metadata,
      END OF ty_create_request,

      BEGIN OF ty_member_result,
        id             TYPE i,
        name           TYPE string,
        email          TYPE string,
        order          TYPE i,
        observer       TYPE abap_bool,
        sign_url       TYPE string,
        short_sign_url TYPE string,
      END OF ty_member_result,
      ty_member_results TYPE STANDARD TABLE OF ty_member_result WITH DEFAULT KEY,

      BEGIN OF ty_document_result,
        document_id            TYPE i,      " id numérico — usado no download
        definitive_document_id TYPE string, " GUID definitivo (API 2.23+)
        name                   TYPE string,
        description            TYPE string,
      END OF ty_document_result,
      ty_document_results TYPE STANDARD TABLE OF ty_document_result WITH DEFAULT KEY,

      BEGIN OF ty_create_response,
        operation_id TYPE i,
        external_id  TYPE string,
        status       TYPE string,
        members      TYPE ty_member_results,
        documents    TYPE ty_document_results,
      END OF ty_create_response,

      BEGIN OF ty_operation_info,
        name             TYPE string,
        status           TYPE string,
        stage            TYPE string,
        created_at       TYPE string,
        expiration_date  TYPE string,
        completed_at     TYPE string,
        progress_current TYPE i,
        progress_total   TYPE i,
      END OF ty_operation_info,

      BEGIN OF ty_member_status,
        name         TYPE string,
        email        TYPE string,
        role         TYPE string,
        order        TYPE i,
        observer     TYPE abap_bool,
        status       TYPE string,
        stage        TYPE string,
        completed_at TYPE string,
      END OF ty_member_status,
      ty_member_statuses TYPE STANDARD TABLE OF ty_member_status WITH DEFAULT KEY,

      BEGIN OF ty_status_response,
        operation TYPE ty_operation_info,
        members   TYPE ty_member_statuses,
      END OF ty_status_response,

      BEGIN OF ty_download_response,
        file_name      TYPE string,
        content_type   TYPE string,
        base64_content TYPE string,
      END OF ty_download_response.

    METHODS constructor
      IMPORTING
        !iv_rfc_destination TYPE rfcdest DEFAULT 'FORSIGN_CPI'
        !iv_base_path       TYPE string DEFAULT '/http'.

    "! Upload + criação da operação em uma chamada (POST /forsign/v1/operations).
    METHODS create_operation
      IMPORTING !is_request        TYPE ty_create_request
      RETURNING VALUE(rs_response) TYPE ty_create_response
      RAISING   zcx_forsign_error.

    "! Status da operação (GET /forsign/v1/operations/status).
    METHODS get_status
      IMPORTING !iv_operation_id   TYPE i
      RETURNING VALUE(rs_response) TYPE ty_status_response
      RAISING   zcx_forsign_error.

    "! Documento assinado em base64 (GET /forsign/v1/operations/download).
    "! Chamar após o webhook DocumentReady.
    METHODS download_document
      IMPORTING !iv_operation_id   TYPE i
                !iv_document_id    TYPE i
      RETURNING VALUE(rs_response) TYPE ty_download_response
      RAISING   zcx_forsign_error.

    "! Conteúdo binário (xstring) do documento assinado, pronto p/ ArchiveLink/GOS.
    METHODS download_document_binary
      IMPORTING !iv_operation_id  TYPE i
                !iv_document_id   TYPE i
      EXPORTING !ev_file_name     TYPE string
                !ev_content       TYPE xstring
      RAISING   zcx_forsign_error.

  PRIVATE SECTION.
    DATA mv_destination TYPE rfcdest.
    DATA mv_base_path   TYPE string.

    METHODS execute
      IMPORTING !iv_method         TYPE string
                !iv_path           TYPE string
                !iv_json_body      TYPE string OPTIONAL
      RETURNING VALUE(rv_response) TYPE string
      RAISING   zcx_forsign_error.
ENDCLASS.


CLASS zcl_forsign_client IMPLEMENTATION.

  METHOD constructor.
    mv_destination = iv_rfc_destination.
    mv_base_path   = iv_base_path.
  ENDMETHOD.

  METHOD create_operation.
    DATA(lv_json) = /ui2/cl_json=>serialize(
      data        = is_request
      compress    = abap_true
      pretty_name = /ui2/cl_json=>pretty_mode-camel_case ).

    " O contrato canônico usa sequentialSigning/mergeIfExists — o
    " serializador camel_case já produz esses nomes a partir dos campos ABAP.
    DATA(lv_response) = execute(
      iv_method    = 'POST'
      iv_path      = |{ mv_base_path }/forsign/v1/operations|
      iv_json_body = lv_json ).

    /ui2/cl_json=>deserialize(
      EXPORTING json        = lv_response
                pretty_name = /ui2/cl_json=>pretty_mode-camel_case
      CHANGING  data        = rs_response ).

    IF rs_response-operation_id IS INITIAL.
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_error_text = |Resposta sem operationId: { lv_response(300) }|.
    ENDIF.
  ENDMETHOD.

  METHOD get_status.
    DATA(lv_response) = execute(
      iv_method = 'GET'
      iv_path   = |{ mv_base_path }/forsign/v1/operations/status?operationId={ iv_operation_id }| ).

    /ui2/cl_json=>deserialize(
      EXPORTING json        = lv_response
                pretty_name = /ui2/cl_json=>pretty_mode-camel_case
      CHANGING  data        = rs_response ).
  ENDMETHOD.

  METHOD download_document.
    DATA(lv_response) = execute(
      iv_method = 'GET'
      iv_path   = |{ mv_base_path }/forsign/v1/operations/download| &&
                  |?operationId={ iv_operation_id }&documentId={ iv_document_id }| ).

    /ui2/cl_json=>deserialize(
      EXPORTING json        = lv_response
                pretty_name = /ui2/cl_json=>pretty_mode-camel_case
      CHANGING  data        = rs_response ).

    IF rs_response-base64_content IS INITIAL.
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_error_text = |Download sem conteudo: { lv_response(300) }|.
    ENDIF.
  ENDMETHOD.

  METHOD download_document_binary.
    DATA(ls_download) = download_document(
      iv_operation_id = iv_operation_id
      iv_document_id  = iv_document_id ).

    ev_file_name = ls_download-file_name.
    CALL FUNCTION 'SSFC_BASE64_DECODE'
      EXPORTING
        b64data = ls_download-base64_content
      IMPORTING
        bindata = ev_content
      EXCEPTIONS
        OTHERS  = 1.
    IF sy-subrc <> 0.
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_error_text = 'Falha ao decodificar base64 do documento.'.
    ENDIF.
  ENDMETHOD.

  METHOD execute.
    DATA lo_client TYPE REF TO if_http_client.

    cl_http_client=>create_by_destination(
      EXPORTING destination = mv_destination
      IMPORTING client      = lo_client
      EXCEPTIONS OTHERS     = 1 ).
    IF sy-subrc <> 0.
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_error_text = |Destino RFC { mv_destination } invalido (SM59, tipo G).|.
    ENDIF.

    cl_http_utility=>set_request_uri( request = lo_client->request
                                      uri     = iv_path ).
    lo_client->request->set_method( iv_method ).
    lo_client->request->set_header_field( name  = 'Accept'
                                          value = 'application/json' ).

    IF iv_json_body IS NOT INITIAL.
      lo_client->request->set_content_type( 'application/json' ).
      lo_client->request->set_cdata( iv_json_body ).
    ENDIF.

    lo_client->send( EXCEPTIONS OTHERS = 1 ).
    IF sy-subrc = 0.
      lo_client->receive( EXCEPTIONS OTHERS = 1 ).
    ENDIF.
    IF sy-subrc <> 0.
      DATA lv_comm_error TYPE string.
      lo_client->get_last_error( IMPORTING message = lv_comm_error ).
      lo_client->close( EXCEPTIONS OTHERS = 0 ).
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_error_text = |Erro de comunicacao com o CPI: { lv_comm_error }|.
    ENDIF.

    DATA lv_status TYPE i.
    lo_client->response->get_status( IMPORTING code = lv_status ).
    rv_response = lo_client->response->get_cdata( ).
    lo_client->close( EXCEPTIONS OTHERS = 0 ).

    IF lv_status >= 400.
      " O kit devolve o erro canônico { "error": { httpStatus, type, message } }.
      RAISE EXCEPTION TYPE zcx_forsign_error
        EXPORTING iv_http_status = lv_status
                  iv_error_text  = rv_response.
    ENDIF.
  ENDMETHOD.

ENDCLASS.
