*&---------------------------------------------------------------------*
*& ZFORSIGN_DEMO — demonstração do ForSign SAP Integration Kit
*&---------------------------------------------------------------------*
*& Fluxo: seleciona um PDF local -> cria operação de assinatura via CPI
*& -> exibe operationId, links de assinatura e status.
*&
*& Pré-requisito: destino SM59 tipo G (default FORSIGN_CPI) apontando
*& para o runtime do Cloud Integration com a service key (Basic).
*&---------------------------------------------------------------------*
REPORT zforsign_demo.

PARAMETERS: p_dest  TYPE rfcdest  DEFAULT 'FORSIGN_CPI' OBLIGATORY,
            p_file  TYPE string   LOWER CASE OBLIGATORY,
            p_name1 TYPE string   LOWER CASE DEFAULT 'Assinante Demo' OBLIGATORY,
            p_mail1 TYPE string   LOWER CASE OBLIGATORY,
            p_extid TYPE string   LOWER CASE DEFAULT 'DEMO-0001'.

AT SELECTION-SCREEN ON VALUE-REQUEST FOR p_file.
  DATA lt_files TYPE filetable.
  DATA lv_rc    TYPE i.
  cl_gui_frontend_services=>file_open_dialog(
    EXPORTING file_filter = 'PDF (*.pdf)|*.pdf'
    CHANGING  file_table  = lt_files
              rc          = lv_rc ).
  READ TABLE lt_files INDEX 1 INTO DATA(ls_file).
  IF sy-subrc = 0.
    p_file = ls_file-filename.
  ENDIF.

START-OF-SELECTION.
  PERFORM main.

FORM main.
  " 1. Lê o PDF do frontend e converte para base64
  DATA lt_bin    TYPE STANDARD TABLE OF x255.
  DATA lv_length TYPE i.
  cl_gui_frontend_services=>gui_upload(
    EXPORTING filename   = p_file
              filetype   = 'BIN'
    IMPORTING filelength = lv_length
    CHANGING  data_tab   = lt_bin
    EXCEPTIONS OTHERS    = 1 ).
  IF sy-subrc <> 0.
    MESSAGE 'Falha ao ler o arquivo PDF.' TYPE 'E'.
  ENDIF.

  DATA lv_xstring TYPE xstring.
  CALL FUNCTION 'SCMS_BINARY_TO_XSTRING'
    EXPORTING input_length = lv_length
    IMPORTING buffer       = lv_xstring
    TABLES    binary_tab   = lt_bin
    EXCEPTIONS OTHERS      = 1.

  DATA lv_base64 TYPE string.
  CALL FUNCTION 'SSFC_BASE64_ENCODE'
    EXPORTING bindata = lv_xstring
    IMPORTING b64data = lv_base64
    EXCEPTIONS OTHERS = 1.

  " 2. Monta o payload canônico
  DATA(lo_client) = NEW zcl_forsign_client( iv_rfc_destination = p_dest ).

  DATA(lv_filename) = p_file.
  IF lv_filename CA '\/'.
    " Extrai apenas o nome do arquivo
    DATA(lv_offset) = find( val = reverse( lv_filename ) regex = '[\\/]' ).
    lv_filename = substring( val = lv_filename
                             off = strlen( lv_filename ) - lv_offset ).
  ENDIF.

  DATA(ls_request) = VALUE zcl_forsign_client=>ty_create_request(
    external_id        = p_extid
    name               = |Demo ForSign { sy-datum DATE = ISO }|
    language           = 'pt-br'
    sequential_signing = abap_false
    merge_if_exists    = abap_true
    document           = VALUE #( file_name      = lv_filename
                                  content_base64 = lv_base64 )
    signers            = VALUE #(
      ( name           = p_name1
        email          = p_mail1
        order          = 1
        signature_type = 'Click'
        notification_channel = 'Email' ) )
    metadata           = VALUE #(
      ( key = 'SAP_SYSTEM' value = sy-sysid )
      ( key = 'SAP_USER'   value = sy-uname ) ) ).

  " 3. Cria a operação e mostra os resultados
  TRY.
      DATA(ls_response) = lo_client->create_operation( ls_request ).

      WRITE: / 'Operacao criada com sucesso!'.
      WRITE: / 'operationId:', ls_response-operation_id.
      WRITE: / 'status:     ', ls_response-status.
      ULINE.
      WRITE: / 'Assinantes:'.
      LOOP AT ls_response-members INTO DATA(ls_member).
        WRITE: / '  ', ls_member-name, '<', ls_member-email, '>'.
        IF ls_member-sign_url IS NOT INITIAL.
          WRITE: / '    Link:', ls_member-sign_url.
        ENDIF.
      ENDLOOP.
      ULINE.
      WRITE: / 'Documentos:'.
      LOOP AT ls_response-documents INTO DATA(ls_doc).
        WRITE: / '  documentId:', ls_doc-document_id, ls_doc-name.
      ENDLOOP.

      " 4. Consulta o status logo em seguida
      DATA(ls_status) = lo_client->get_status( ls_response-operation_id ).
      ULINE.
      WRITE: / 'Status atual:', ls_status-operation-status,
             '(', ls_status-operation-progress_current, '/',
             ls_status-operation-progress_total, 'assinaturas )'.

    CATCH zcx_forsign_error INTO DATA(lx_error).
      WRITE: / 'ERRO:', lx_error->get_text( ).
  ENDTRY.
ENDFORM.
