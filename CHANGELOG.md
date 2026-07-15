# Changelog

All notable changes to the ForSign SAP Integration Kit are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and
the project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-07-15

### Added

- **CPI iFlows** (SAP Integration Suite / Cloud Integration):
  - `ForSign_CreateOperation` — document upload (base64) + operation creation
    in a single call, with pre-flight payload validation (visual signature
    types require positions; SMS/WhatsApp require phone) and automatic
    `documentId` injection.
  - `ForSign_OperationStatus` — operation and per-signer status.
  - `ForSign_DownloadDocument` — signed document download (base64 JSON).
  - `ForSign_WebhookReceiver` — HMAC-SHA256 validation (constant-time) and
    canonical event forwarding to a configurable SAP endpoint.
  - Exception subprocess in every iFlow: structured JSON errors
    (`ValidationError` 400, `SecurityError` 401, `ForSignApiError` with the
    original status, timeouts 504) instead of stack traces.
  - Externalized parameters (`ForSign_BaseUrl`, `ForSign_RequestTimeout`,
    `SAP_Target_Endpoint`, `SAP_Target_CredentialName`); credentials via
    Security Material only.
- **ABAP client** (`abap/`, abapGit-ready): `ZCL_FORSIGN_CLIENT`
  (create/status/download + binary download for ArchiveLink/GOS),
  `ZCX_FORSIGN_ERROR`, and the `ZFORSIGN_DEMO` end-to-end report.
- **Fiori cockpit** (`fiori/`): SAPUI5 app with operation status, signer
  progress table, and signed-PDF download; BTP approuter routing included.
- **OpenAPI 3.0 spec** of the canonical contract (`openapi.yaml`).
- **Postman collection** with automatic `operationId`/`documentId` chaining.
- **Test suite**: 19 Groovy unit tests with CPI mocks (`tests/`), structural
  validation (`ci/validate_kit.py`), GitHub Actions CI, and release packaging.
- **Documentation** (Portuguese): setup guide, S/4HANA/ECC and SuccessFactors
  scenarios, and hardening guide (OAuth2, JMS, monitoring, transport, LGPD).
