/*
 * ForSign SAP Integration Kit — step final dos iFlows ForSign_OperationStatus
 * e ForSign_DownloadDocument.
 *
 * As respostas da API ForSign vêm no envelope
 * { "success", "statusCode", "data", "messages" } (endpoints antigos podem usar
 * "value" no lugar de "data"; alguns embrulham duas vezes — data.data).
 * Este step devolve ao SAP apenas o conteúdo útil, desembrulhando
 * recursivamente; se o body não for JSON ou não tiver envelope, passa direto.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def raw = message.getBody(String)
    try {
        def current = new JsonSlurper().parseText(raw)
        def unwrapped = false
        while (current instanceof Map
                && (current.containsKey('statusCode') || current.containsKey('success'))
                && (current.data != null || current.value != null)) {
            current = current.data != null ? current.data : current.value
            unwrapped = true
        }
        if (unwrapped) {
            message.setBody(JsonOutput.toJson(current))
        }
    } catch (Exception ignored) {
        // Body não é JSON — devolve como está.
    }
    message.setHeader('Content-Type', 'application/json')
    return message
}
