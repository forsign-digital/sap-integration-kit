# ForSign Signature Cockpit (SAPUI5/Fiori)

App SAPUI5 freestyle que acompanha operações de assinatura ForSign **dentro do
SAP** (Fiori Launchpad ou standalone): status da operação, progresso por
assinante e download do documento assinado — tudo via os iFlows do kit.

## Funcionalidades

- Consulta por `operationId` (o id numérico retornado na criação).
- Header com status colorido (Completed = verde, Canceled/Expired = vermelho,
  em andamento = amarelo) e progresso `assinados/total`.
- Tabela de assinantes com ordem, papel, status e data de assinatura.
- Download do PDF assinado direto do browser (habilitado quando `Completed`).

## Como o app fala com o CPI

O app chama `{forsignCpiBaseUrl}/forsign/v1/...`. O default é `/forsign-cpi`,
um caminho relativo resolvido por proxy — nunca exponha a service key do CPI
no browser:

- **BTP (recomendado):** deploy como app HTML5 com o `xs-app.json` incluído.
  Crie a destination `forsign-cpi-runtime` apontando para o runtime do CPI com
  a service key; o approuter injeta a autenticação.
- **Desenvolvimento local:** qualquer proxy reverso (ex.: `ui5 serve` com
  `fiori-tools-proxy`) mapeando `/forsign-cpi` → runtime do CPI.
- **On-premise (Launchpad embarcado):** rota no Web Dispatcher ou SICF proxy.

Para trocar o caminho, ajuste `sap.ui5/config/forsignCpiBaseUrl` no
`manifest.json`.

## Estrutura

```
fiori/
├── xs-app.json                # rotas do approuter (BTP HTML5 repo)
└── webapp/
    ├── index.html             # bootstrap standalone (CDN ui5.sap.com)
    ├── manifest.json
    ├── Component.js
    ├── view/Cockpit.view.xml
    ├── controller/Cockpit.controller.js
    └── i18n/i18n.properties
```

## Evoluções naturais

- Lista de operações (exige um iFlow adicional sobre `GET /api/v2/operation`).
- Seleção de documento no download (hoje baixa o documento 1; os ids vêm em
  `documents[].documentId` na criação).
- Tile no Launchpad com contador de operações pendentes.
