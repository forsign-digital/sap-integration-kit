#!/usr/bin/env bash
#
# ForSign SAP Integration Kit — monta os artefatos de iFlow importáveis
# no SAP Integration Suite (Cloud Integration).
#
# Uso: ./build.sh
# Saída: dist/<NomeDoIFlow>.zip (um por iFlow, importável via
#        Design > <pacote> > Add > Integration Flow > Upload)
#
set -euo pipefail

KIT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DIST_DIR="$KIT_DIR/dist"
SCRIPTS_DIR="$KIT_DIR/scripts"

# Scripts Groovy usados por cada iFlow (cada script é compilado por step,
# por isso são copiados para dentro de cada pacote).
scripts_for() {
    case "$1" in
        ForSign_CreateOperation)
            echo "create_operation_prepare_upload.groovy create_operation_build_request.groovy create_operation_map_response.groovy error_response.groovy" ;;
        ForSign_OperationStatus)
            echo "status_prepare_request.groovy unwrap_response.groovy error_response.groovy" ;;
        ForSign_DownloadDocument)
            echo "download_prepare_request.groovy unwrap_response.groovy error_response.groovy" ;;
        ForSign_WebhookReceiver)
            echo "webhook_validate_hmac.groovy webhook_map_event.groovy error_response.groovy" ;;
        *)
            echo "" ;;
    esac
}

rm -rf "$DIST_DIR"
mkdir -p "$DIST_DIR"

for iflow_dir in "$KIT_DIR"/iflows/*/; do
    name="$(basename "$iflow_dir")"
    staging="$(mktemp -d)"
    trap 'rm -rf "$staging"' EXIT

    cp -R "$iflow_dir/." "$staging/"

    mkdir -p "$staging/src/main/resources/script"
    for script in $(scripts_for "$name"); do
        if [[ ! -f "$SCRIPTS_DIR/$script" ]]; then
            echo "ERRO: script $script nao encontrado em $SCRIPTS_DIR" >&2
            exit 1
        fi
        cp "$SCRIPTS_DIR/$script" "$staging/src/main/resources/script/"
    done

    (cd "$staging" && zip -q -r -X "$DIST_DIR/$name.zip" .)
    rm -rf "$staging"
    echo "OK  $DIST_DIR/$name.zip"
done

echo
echo "Artefatos prontos em $DIST_DIR/. Importe cada zip no Integration Suite:"
echo "  Design > (seu pacote) > Add > Integration Flow > Upload"
