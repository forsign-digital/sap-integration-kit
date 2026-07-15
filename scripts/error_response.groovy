/*
 * ForSign SAP Integration Kit — Exception Subprocess (todos os iFlows).
 *
 * Converte qualquer falha (validação, HTTP do ForSign, HMAC inválido, timeout)
 * em uma resposta JSON canônica em vez de um stacktrace do Camel:
 *
 *   {
 *     "error": {
 *       "httpStatus": 400,
 *       "type": "ForSignApiError | ValidationError | SecurityError | IntegrationError",
 *       "message": "...",
 *       "forsignMessages": [ "..." ]    // messages[] do envelope ForSign, quando houver
 *     }
 *   }
 *
 * Mapeamento de status devolvido ao SAP:
 *   - IllegalArgumentException (validação do payload)        → 400
 *   - SecurityException (HMAC inválido no webhook)           → 401
 *   - Falha HTTP do ForSign (4xx/5xx capturado do adapter)   → status original
 *   - Demais exceções (timeout, conexão, config)             → 500
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def exception = message.getProperty('CamelExceptionCaught')

    int httpStatus = 500
    String type = 'IntegrationError'
    String detail = exception != null ? String.valueOf(exception.getMessage() ?: exception.toString()) : 'Erro desconhecido.'
    List forsignMessages = []

    if (exception != null) {
        def className = exception.getClass().getName()

        if (exception instanceof IllegalArgumentException) {
            httpStatus = 400
            type = 'ValidationError'
        } else if (exception instanceof SecurityException) {
            httpStatus = 401
            type = 'SecurityError'
        } else if (hasMethod(exception, 'getStatusCode') || hasMethod(exception, 'getHttpResponseStatus')) {
            // Falha HTTP do adapter (ex.: AhcOperationFailedException / HttpOperationFailedException).
            type = 'ForSignApiError'
            def status = hasMethod(exception, 'getStatusCode') ? exception.getStatusCode() : exception.getHttpResponseStatus()
            if (status instanceof Number && status.intValue() >= 400) {
                httpStatus = status.intValue()
            }
            if (hasMethod(exception, 'getResponseBody')) {
                forsignMessages = extractForSignMessages(exception.getResponseBody() as String)
            }
        } else if (className.contains('Timeout') || detail.toLowerCase().contains('timed out')) {
            httpStatus = 504
            type = 'IntegrationError'
        }
    }

    def error = [
        httpStatus: httpStatus,
        type      : type,
        message   : detail,
    ]
    if (forsignMessages) {
        error.forsignMessages = forsignMessages
    }

    message.setBody(JsonOutput.toJson([error: error]))
    message.setHeader('Content-Type', 'application/json')
    message.setHeader('CamelHttpResponseCode', httpStatus)
    return message
}

private boolean hasMethod(Object target, String name) {
    try {
        return target.metaClass.respondsTo(target, name)
    } catch (Exception ignored) {
        return false
    }
}

/* Extrai messages[].value do envelope de erro do ForSign, se o body for JSON. */
private List extractForSignMessages(String responseBody) {
    if (!responseBody?.trim()) return []
    try {
        def parsed = new JsonSlurper().parseText(responseBody)
        if (parsed instanceof Map && parsed.messages instanceof List) {
            return parsed.messages.collect { it instanceof Map ? (it.value ?: it.toString()) : it.toString() }
        }
    } catch (Exception ignored) {
        // body de erro não é JSON — devolve truncado como mensagem única
        return [responseBody.take(300)]
    }
    return []
}
