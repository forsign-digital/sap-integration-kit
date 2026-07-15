/*
 * ForSign SAP Integration Kit — iFlow ForSign_OperationStatus, step 1.
 *
 * Lê o parâmetro de query "operationId" da chamada do SAP
 * (GET /forsign/v1/operations/status?operationId=...), guarda-o como
 * property (usado na URL do receiver: .../api/v2/operation/${property.operationId}/status)
 * e injeta o header X-Api-Key.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService

def Message processData(Message message) {
    def operationId = extractQueryParam(message, 'operationId')
    if (!operationId) {
        throw new IllegalArgumentException("Parametro de query 'operationId' e obrigatorio.")
    }
    message.setProperty('operationId', operationId)

    // GET não envia body.
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
