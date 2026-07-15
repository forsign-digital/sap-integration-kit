"! Exceção do cliente ForSign SAP Integration Kit.
CLASS zcx_forsign_error DEFINITION
  PUBLIC
  INHERITING FROM cx_static_check
  FINAL
  CREATE PUBLIC.

  PUBLIC SECTION.
    INTERFACES if_t100_dyn_msg.

    DATA mv_http_status TYPE i READ-ONLY.
    DATA mv_error_text  TYPE string READ-ONLY.

    METHODS constructor
      IMPORTING
        !iv_http_status TYPE i OPTIONAL
        !iv_error_text  TYPE string OPTIONAL
        !previous       TYPE REF TO cx_root OPTIONAL.

    METHODS get_text REDEFINITION.
ENDCLASS.


CLASS zcx_forsign_error IMPLEMENTATION.

  METHOD constructor.
    super->constructor( previous = previous ).
    mv_http_status = iv_http_status.
    mv_error_text  = iv_error_text.
  ENDMETHOD.

  METHOD get_text.
    IF mv_error_text IS NOT INITIAL.
      IF mv_http_status IS NOT INITIAL.
        result = |ForSign HTTP { mv_http_status }: { mv_error_text }|.
      ELSE.
        result = mv_error_text.
      ENDIF.
    ELSE.
      result = super->get_text( ).
    ENDIF.
  ENDMETHOD.

ENDCLASS.
