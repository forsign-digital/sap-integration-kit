/*
 * ForSign SAP Integration Kit — iFlow ForSign_WebhookReceiver, step 1.
 *
 * Valida a assinatura HMAC-SHA256 dos webhooks enviados pelo ForSign.
 * O ForSign envia:
 *   X-ForSign-Signature: sha256=<hmac-sha256 hex do body>
 *   X-ForSign-Timestamp: <timestamp>
 *
 * O segredo é o campo "secret" retornado por POST /api/v2/webhooks/register,
 * armazenado no Security Material do tenant como Secure Parameter com alias
 * "ForSign_WebhookSecret".
 *
 * Requisições sem assinatura válida são rejeitadas (exceção → HTTP 500 para o
 * ForSign, que fará retry; a mensagem nunca é encaminhada ao SAP).
 */
import com.sap.gateway.ip.core.customdev.util.Message
import com.sap.it.api.ITApiFactory
import com.sap.it.api.securestore.SecureStoreService

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

def Message processData(Message message) {
    def body = message.getBody(String) ?: ''
    def signatureHeader = (String) message.getHeaders().get('X-ForSign-Signature')

    if (!signatureHeader?.startsWith('sha256=')) {
        throw new SecurityException('Webhook rejeitado: header X-ForSign-Signature ausente ou mal formatado.')
    }
    def receivedSignature = signatureHeader.substring('sha256='.length()).trim()

    def secureStore = ITApiFactory.getService(SecureStoreService.class, null)
    def credential = secureStore.getUserCredential('ForSign_WebhookSecret')
    if (credential == null) {
        throw new IllegalStateException("Secure Parameter 'ForSign_WebhookSecret' nao encontrado no Security Material.")
    }
    def secret = new String(credential.getPassword())

    def mac = Mac.getInstance('HmacSHA256')
    mac.init(new SecretKeySpec(secret.getBytes('UTF-8'), 'HmacSHA256'))
    def expectedSignature = mac.doFinal(body.getBytes('UTF-8')).encodeHex().toString()

    // Comparação em tempo constante.
    def valid = MessageDigest.isEqual(
        expectedSignature.toLowerCase().getBytes('UTF-8'),
        receivedSignature.toLowerCase().getBytes('UTF-8'))
    if (!valid) {
        throw new SecurityException('Webhook rejeitado: assinatura HMAC invalida.')
    }

    return message
}
