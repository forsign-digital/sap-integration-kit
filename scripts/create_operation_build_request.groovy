/*
 * ForSign SAP Integration Kit — iFlow ForSign_CreateOperation, step 2.
 *
 * Executa após o Request-Reply de upload. Lê a resposta do upload
 * (POST /api/v2/document/upload-base64 — envelope { success, statusCode,
 * data: { id, fileName, token, ... }, messages }), extrai o id do documento
 * temporário e monta o body de criação da operação:
 *   POST {{ForSign_BaseUrl}}/api/v1/operation  (OperationViewModel)
 *
 * Mapeamento canônico → ForSign:
 *   sequentialSigning   → order
 *   signers[].order     → members[].orderPosition
 *   signers[].*         → members[].* (passthrough dos demais campos:
 *                         phone, cpf, role, observer, signatureType,
 *                         notificationChannel, authenticationChannel,
 *                         doubleAuthentication, antifraudFeatures,
 *                         signatures, rubrics, formFields, attachments,
 *                         formTitle, formDescription, ...)
 *
 * O SAP não conhece o id do documento antes do upload, então este step
 * injeta o id retornado em signatures[].documentId, rubrics[].documentId e
 * formFields[].documentId quando o campo não veio preenchido.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    def slurper = new JsonSlurper()

    def rawUpload = message.getBody(String)
    def uploadResponse = unwrapEnvelope(slurper.parseText(rawUpload))
    if (!uploadResponse?.id) {
        throw new IllegalStateException(
            "Upload nao retornou o id do documento. Resposta: ${rawUpload?.take(500)}")
    }
    def documentUploadId = uploadResponse.id as String

    def request = slurper.parseText((String) message.getProperty('canonicalRequest'))

    def file = [
        id      : documentUploadId,
        fileName: uploadResponse.fileName ?: request.document.fileName,
    ]
    if (uploadResponse.token) file.token = uploadResponse.token
    file.description = request.document.description ?: request.document.fileName

    def members = request.signers.collect { signer ->
        def member = [:]
        signer.each { key, value ->
            if (value == null) return
            if (key == 'order') {
                member.orderPosition = value
            } else {
                member[key] = value
            }
        }
        // Injeta o id do upload onde o SAP nao informou documentId.
        ['signatures', 'rubrics'].each { listKey ->
            if (member[listKey] instanceof List) {
                member[listKey].each { item ->
                    if (item instanceof Map && !item.documentId) item.documentId = documentUploadId
                }
            }
        }
        if (member.formFields instanceof List) {
            member.formFields.each { field ->
                // Só campos posicionados no documento precisam de documentId.
                if (field instanceof Map && field.positions && !field.documentId) {
                    field.documentId = documentUploadId
                }
            }
        }
        return member
    }

    def operation = [
        name   : request.name,
        files  : [file],
        members: members,
        order  : request.sequentialSigning ?: false,
    ]
    if (request.externalId) operation.externalId = request.externalId
    if (request.language) operation.language = request.language
    if (request.manualFinish) operation.manualFinish = request.manualFinish
    if (request.expirationDate) operation.expirationDate = request.expirationDate
    if (request.optionalMessage) operation.optionalMessage = request.optionalMessage
    if (request.metadata) operation.metadata = request.metadata
    if (request.operationModelId) operation.operationModelId = request.operationModelId
    if (request.displayCover != null) operation.displayCover = request.displayCover
    // Idempotência: envie mergeIfExists=true junto com externalId para que um
    // retry do SAP não crie operação duplicada (ver docs/hardening.md).
    if (request.mergeIfExists != null) operation.mergeIfExists = request.mergeIfExists
    if (request.memberMovementWarning != null) operation.memberMovementWarning = request.memberMovementWarning
    if (request.groups) operation.groups = request.groups

    message.setBody(JsonOutput.toJson(operation))
    applyForSignHeaders(message)
    return message
}

/*
 * As respostas da API vêm no envelope { success, statusCode, data, messages }
 * (endpoints antigos podem usar "value" no lugar de "data"). Alguns endpoints
 * embrulham DUAS vezes (data.data) — desembrulha recursivamente.
 */
private Object unwrapEnvelope(Object parsed) {
    def current = parsed
    while (current instanceof Map
            && (current.containsKey('statusCode') || current.containsKey('success'))
            && (current.data != null || current.value != null)) {
        current = current.data != null ? current.data : current.value
    }
    return current
}

private void applyForSignHeaders(Message message) {
    def secureStore = ITApiFactory.getService(SecureStoreService.class, null)
    def credential = secureStore.getUserCredential('ForSign_ApiKey')
    if (credential == null) {
        throw new IllegalStateException("Secure Parameter 'ForSign_ApiKey' nao encontrado no Security Material.")
    }
    message.setHeader('X-Api-Key', new String(credential.getPassword()))
    message.setHeader('Content-Type', 'application/json')
    message.setHeader('Accept', 'application/json')
    message.removeHeader('Authorization')
}
