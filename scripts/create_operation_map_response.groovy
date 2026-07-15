/*
 * ForSign SAP Integration Kit — iFlow ForSign_CreateOperation, step 3.
 *
 * Executa após o Request-Reply de criação (POST /api/v1/operation, 201).
 * ATENÇÃO ao envelope: a resposta da criação vem embrulhada DUAS vezes —
 * a operação está em data.data (documentação oficial, /docs/operacoes/criar).
 *
 * Resposta canônica devolvida ao SAP:
 *   {
 *     "operationId": 4821,               // id numérico — usar em status/download/cancel/complete
 *     "externalId": "S4-4500001234",
 *     "status": "InProgress",
 *     "members": [ { "id", "name", "email", "order", "signUrl", "shortSignUrl" } ],
 *     "documents": [ { "documentId": 1, "definitiveDocumentId": "9f2e...", "name": "contrato.pdf" } ],
 *     "forsign": { ...operação completa retornada pelo ForSign... }
 *   }
 *
 * - documents[].documentId é o id numérico usado no download
 *   (GET /api/v2/operations/{operationId}/documents/{documentId}/download-base64).
 * - documents[].definitiveDocumentId (files[].documentId, API 2.23+) é o GUID
 *   definitivo, usado p.ex. ao adicionar assinante em operação em andamento.
 * - members[].id é o memberId usado nos endpoints de anexos.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def raw = message.getBody(String)
    def operation = unwrapEnvelope(new JsonSlurper().parseText(raw))

    if (!(operation instanceof Map) || operation.id == null) {
        throw new IllegalStateException(
            "Criacao nao retornou a operacao esperada. Resposta: ${raw?.take(500)}")
    }

    def allMembers = ((operation.members ?: []) + (operation.observers ?: []))

    def canonical = [
        operationId: operation.id,
        externalId : operation.externalId,
        status     : operation.status,
        members    : allMembers.collect { member ->
            [
                id          : member.id,
                name        : member.name,
                email       : member.email,
                order       : member.order,
                observer    : member.observer,
                signUrl     : member.uri,
                shortSignUrl: member.shortenedSignatureUrl,
            ]
        },
        documents  : (operation.files ?: []).findAll { it.type == null || it.type == 'Document' }.collect { f ->
            [
                documentId          : f.id,
                definitiveDocumentId: f.documentId,
                name                : f.name,
                description         : f.description,
            ]
        },
        forsign    : operation,
    ]

    message.setBody(JsonOutput.toJson(canonical))
    message.setHeader('Content-Type', 'application/json')
    return message
}

private Object unwrapEnvelope(Object parsed) {
    def current = parsed
    while (current instanceof Map
            && (current.containsKey('statusCode') || current.containsKey('success'))
            && (current.data != null || current.value != null)) {
        current = current.data != null ? current.data : current.value
    }
    return current
}
