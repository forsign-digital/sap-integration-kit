/*
 * ForSign SAP Integration Kit — iFlow ForSign_DownloadDocument, step 1.
 *
 * Lê os parâmetros de query "operationId" e "documentId" da chamada do SAP
 * (GET /forsign/v1/operations/download?operationId=...&documentId=...),
 * guarda-os como properties (usados na URL do receiver:
 * .../api/v2/operations/${property.operationId}/documents/${property.documentId}/download-base64)
 * e injeta o header X-Api-Key.
 *
 * A resposta do ForSign é { "fileName", "contentType", "base64Content" } —
 * ideal para o SAP, que recebe o binário como base64 dentro de JSON.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService

def Message processData(Message message) {
    def operationId = extractQueryParam(message, 'operationId')
    def documentId = extractQueryParam(message, 'documentId')
    def missing = []
    if (!operationId) missing << 'operationId'
    if (!documentId) missing << 'documentId'
    if (missing) {
        throw new IllegalArgumentException("Parametros de query obrigatorios ausentes: ${missing.join(', ')}")
    }
    message.setProperty('operationId', operationId)
    message.setProperty('documentId', documentId)

    message.setBody('')
    applyForSignHeaders(message)
    return message
}

private String extractQueryParam(Message message, String name) {
    def query = (String) message.getHeaders().get('CamelHttpQuery')
    if (!query) return null
    def match = query.split('&').collect { it.split('=', 2) }.find { it[0] == name }
    return (match && match.size() == 2) ? java.net.URLDecoder.decode(match[1], 'UTF-8') : null
}

private void applyForSignHeaders(Message message) {
    def secureStore = ITApiFactory.getService(SecureStoreService.class, null)
    def credential = secureStore.getUserCredential('ForSign_ApiKey')
    if (credential == null) {
        throw new IllegalStateException("Secure Parameter 'ForSign_ApiKey' nao encontrado no Security Material.")
    }
    message.setHeader('X-Api-Key', new String(credential.getPassword()))
    message.setHeader('Accept', 'application/json')
    // Nao propagar a credencial SAP->CPI para a API ForSign.
    message.removeHeader('Authorization')
    message.removeHeader('CamelHttpQuery')
    message.removeHeader('CamelHttpPath')
}
