/*
 * ForSign SAP Integration Kit — iFlow ForSign_CreateOperation, step 1.
 *
 * Valida o payload canônico recebido do SAP, guarda-o como property e
 * transforma o body na requisição de upload do ForSign:
 *   POST {{ForSign_BaseUrl}}/api/v2/document/upload-base64
 *   { "fileName": "...", "content": "<base64>" }
 *
 * Payload canônico (ver samples/create-operation-request.json e
 * https://docs.forsign.digital/docs/operacoes/criar):
 *   {
 *     "externalId": "S4-4500001234",
 *     "name": "Contrato 4500001234",
 *     "language": "pt-br",                        // pt-br | en | es
 *     "sequentialSigning": true,                  // -> order
 *     "manualFinish": { "hasManualFinish": false, "date": "2026-08-15T23:59:59-03:00" },
 *     "document": { "fileName": "contrato.pdf", "contentBase64": "..." },
 *     "signers": [ {
 *        "name", "email", "order" (-> orderPosition), "signatureType",
 *        "signatures": [ { "printSignature": true,
 *                          "positions": [ { "page": 1, "coordenateX": "4.93%", "coordenateY": "26.46%" } ] } ],
 *        "attachments": [...], "formFields": [...], "observer": false, ...
 *     } ],
 *     "metadata": [ { "key": "SAP_SYSTEM", "value": "S4PRD" } ]
 *   }
 *
 * Regras validadas aqui (espelham o comportamento documentado da API):
 * - signatureType visual (Draw, Text, Stamp, UserChoice, AutomaticStamp)
 *   exige signatures[].positions com page/coordenateX/coordenateY;
 * - notificationChannel/authenticationChannel SMS/Whatsapp exige phone;
 * - o documentId dentro de signatures/rubrics/formFields NAO precisa ser
 *   enviado pelo SAP — o step 2 injeta o id retornado pelo upload.
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

def Message processData(Message message) {
    // Tipos que imprimem uma marca visual no PDF e por isso exigem posição.
    def visualSignatureTypes = ['Draw', 'Text', 'Stamp', 'UserChoice', 'AutomaticStamp']

    def body = message.getBody(String)
    if (!body?.trim()) {
        throw new IllegalArgumentException('Body vazio: envie o payload canonico de criacao de operacao.')
    }

    def request = new JsonSlurper().parseText(body)

    def missing = []
    if (!request.name) missing << 'name'
    if (!request.document?.fileName) missing << 'document.fileName'
    if (!request.document?.contentBase64) missing << 'document.contentBase64'
    if (!(request.signers instanceof List) || request.signers.isEmpty()) missing << 'signers'

    request.signers?.eachWithIndex { signer, i ->
        if (!signer.name) missing << "signers[${i}].name"
        if (!signer.email) missing << "signers[${i}].email"

        def isObserver = signer.observer == true
        def signatureType = signer.signatureType as String

        // Tipos visuais exigem posicao da assinatura no documento.
        if (!isObserver && signatureType in visualSignatureTypes) {
            def hasPosition = (signer.signatures instanceof List) && signer.signatures.any { sig ->
                (sig.positions instanceof List) && sig.positions.any { pos ->
                    pos.page != null && pos.coordenateX && pos.coordenateY
                }
            }
            if (!hasPosition) {
                missing << "signers[${i}].signatures[].positions (obrigatorio para signatureType=${signatureType}: page, coordenateX e coordenateY em %)"
            }
        }

        // SMS/Whatsapp exigem telefone.
        def channels = [signer.notificationChannel, signer.authenticationChannel]
        if (channels.any { it in ['SMS', 'Whatsapp'] } && !signer.phone) {
            missing << "signers[${i}].phone (obrigatorio para notificationChannel/authenticationChannel SMS ou Whatsapp)"
        }
    }

    if (missing) {
        throw new IllegalArgumentException("Payload invalido: ${missing.join('; ')}")
    }

    // Guarda o payload canônico para os próximos steps do iFlow.
    message.setProperty('canonicalRequest', body)

    // Body do upload-base64 do ForSign.
    message.setBody(JsonOutput.toJson([
        fileName: request.document.fileName,
        content : request.document.contentBase64,
    ]))

    applyForSignHeaders(message)
    return message
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
    // Nao propagar a credencial SAP->CPI para a API ForSign.
    message.removeHeader('Authorization')
}
