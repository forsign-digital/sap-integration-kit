# ForSign SAP Integration Kit

[![CI](https://github.com/forsign-digital/sap-integration-kit/actions/workflows/ci.yml/badge.svg)](https://github.com/forsign-digital/sap-integration-kit/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Release](https://img.shields.io/github/v/release/forsign-digital/sap-integration-kit)](https://github.com/forsign-digital/sap-integration-kit/releases)

Digital signatures for SAP — ready-to-import **SAP Integration Suite (CPI)
iFlows**, an **ABAP client**, and a **Fiori cockpit** connecting S/4HANA, ECC,
and SuccessFactors to the [ForSign](https://forsign.digital) e-signature API.

**[Documentação em português →](README.pt-BR.md)**

```
S/4HANA ──┐                        ┌──> upload + create operation
ECC ──────┤──> SAP Integration ────┤──> operation status
SFSF ─────┘    Suite (iFlows)      └──> signed document download
                   ▲
            webhook (HMAC-SHA256)
              DocumentReady
                   ▲
               ForSign API
```

## What's inside

| Component | Path | Description |
|---|---|---|
| **CPI iFlows** | [`iflows/`](iflows/) | 4 importable integration flows: create operation (upload + creation in one call), operation status, signed-document download, and a webhook receiver with HMAC-SHA256 validation |
| **ABAP client** | [`abap/`](abap/README.md) | `ZCL_FORSIGN_CLIENT` — 3 methods to consume the kit from ECC 7.40+/S/4HANA, installable via [abapGit](https://abapgit.org); includes the `ZFORSIGN_DEMO` end-to-end report |
| **Fiori cockpit** | [`fiori/`](fiori/README.md) | SAPUI5 app to track signature operations and download signed PDFs from the Launchpad |
| **OpenAPI spec** | [`openapi.yaml`](openapi.yaml) | Canonical contract exposed by the iFlows (4 endpoints + webhook event schema) |
| **Postman collection** | [`postman/`](postman/) | Ready-to-run requests with automatic `operationId` chaining |
| **Tests + CI** | [`tests/`](tests/), [`ci/`](ci/) | 19 unit tests for the Groovy scripts (CPI mocks included) + structural validation |

Every iFlow ships with an **exception subprocess**: any failure (payload
validation, ForSign HTTP error, invalid HMAC, timeout) returns a structured
JSON error with the proper HTTP status — never a stack trace.

## Quick start

1. Download the iFlow zips from the [latest release](https://github.com/forsign-digital/sap-integration-kit/releases)
   (or clone and run `./build.sh`).
2. Import each zip in **Integration Suite → Design → Add → Integration Flow → Upload**.
3. Follow the [setup guide](docs/setup-guide.md): create the Secure Parameters
   (`ForSign_ApiKey`, `ForSign_WebhookSecret`), configure the externalized
   parameters, deploy, and register the `DocumentReady` webhook.
4. Test with the [Postman collection](postman/) — or run `ZFORSIGN_DEMO` in
   your ABAP system.

### Consuming from SAP

- **Any HTTP client** — call the CPI endpoints with the runtime service key
  (contract in [`openapi.yaml`](openapi.yaml)).
- **ABAP** — install [`abap/`](abap/README.md) via abapGit
  (this repository URL, starting folder is pre-configured) and call
  `ZCL_FORSIGN_CLIENT`.
- **Fiori** — deploy [`fiori/`](fiori/README.md) to track operations in the
  Launchpad.

## Contract highlights

- `externalId` carries your SAP key (PO number, employee ID, …) and comes back
  in every webhook for correlation; combine with `mergeIfExists: true` for
  idempotent retries.
- Visual signature types (`Draw`, `Text`, `UserChoice`) require
  `signatures[].positions` (page + X/Y coordinates in %). The kit validates
  this **before** calling the API.
- The `documentId` inside `signatures`/`formFields` is injected automatically
  from the upload — the SAP side never needs to know it.
- Use the **`DocumentReady`** webhook as the download trigger — it fires when
  the final sealed PDFs are ready.

Full documentation (Portuguese): [setup guide](docs/setup-guide.md) ·
[S/4HANA & SuccessFactors scenarios](docs/sap-scenarios.md) ·
[hardening](docs/hardening.md)

## Compatibility

| Kit | ForSign API | SAP Integration Suite | ABAP | SAPUI5 |
|---|---|---|---|---|
| 1.x | Public API (current) | Cloud Integration (any current tenant) | NetWeaver 7.40+ / S/4HANA (needs `/ui2/cl_json`) | 1.108+ |

Breaking changes to the canonical contract only happen in major versions.

## Development

```bash
./build.sh              # builds dist/*.zip (importable artifacts)
./tests/run_tests.sh    # Groovy unit tests (local groovy or Docker)
python3 ci/validate_kit.py   # structural validation
```

## Support

- Bugs and feature requests: [GitHub Issues](https://github.com/forsign-digital/sap-integration-kit/issues)
- ForSign API support: [help@forsign.digital](mailto:help@forsign.digital)
- API documentation: [https://docs.forsign.digital](https://docs.forsign.digital)

## License

[Apache-2.0](LICENSE). The kit is open source; using the ForSign API requires
a ForSign account and API key.
