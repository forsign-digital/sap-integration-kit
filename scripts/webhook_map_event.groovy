/*
 * ForSign SAP Integration Kit — iFlow ForSign_WebhookReceiver, step 2.
 *
 * Converte o payload de webhook do ForSign (WebhookData) no evento canônico
 * encaminhado ao endpoint SAP configurado ({{SAP_Target_Endpoint}}):
 *
 *   {
 *     "event": "DocumentReady",
 *     "success": true,
 *     "operationId": "...",           // operationCompanyId quando disponível
 *     "externalId": "S4-4500001234",  // a chave do SAP volta aqui
 *     "operationName": "...",
 *     "status": 5,
 *     "occurredAt": "...",
 *     "documents": [ { "documentId", "fileName", "totalPages" } ],
 *     "members": [ { "name", "email" } ],
 *     "metadata": [ { "key", "value" } ],
 *     "forsign": { ...payload original... }
 *   }
 *
 * O tipo do evento também é exposto na property "webhookAction" para permitir
 * roteamento no iFlow (ex.: só encaminhar CompletedOperation).
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def raw = message.getBody(String)
    def webhook = new JsonSlurper().parseText(raw)
    def data = webhook.data ?: [:]

    def canonical = [
        event        : webhook.actionDescription ?: String.valueOf(webhook.action),
        success      : webhook.success,
        operationId  : data.operationCompanyId ?: webhook.operationId ?: data.id,
        externalId   : data.externalId,
        operationName: data.name,
        status       : data.status,
        occurredAt   : webhook.createdAt,
        documents    : (data.files ?: []).collect { file ->
            [documentId: file.id, fileName: file.originalName ?: file.name, totalPages: file.totalPages]
        },
        members      : (data.members ?: []).collect { member ->
            [name: member.name, email: member.email]
        },
        metadata     : data.metadata ?: [],
        forsign      : webhook,
    ]

    message.setProperty('webhookAction', canonical.event)
    message.setBody(JsonOutput.toJson(canonical))
    message.setHeader('Content-Type', 'application/json')
    // Headers de entrada do ForSign não devem vazar para a chamada ao SAP.
    message.removeHeader('X-ForSign-Signature')
    message.removeHeader('X-ForSign-Timestamp')
    message.removeHeader('X-Api-Key')
    return message
}
