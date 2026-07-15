/*
 * ForSign SAP Integration Kit — suite de testes dos scripts Groovy.
 *
 * Execução (ver tests/run_tests.sh):
 *   groovy -cp tests/mocks:scripts tests/run_all_tests.groovy
 *
 * Cada script do kit é carregado com GroovyShell (mesmo mecanismo do CPI:
 * um script por step, compilado isoladamente) e o processData é exercitado
 * com payloads reais da documentação oficial (/docs/operacoes/criar).
 */
import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

def scriptsDir = new File(getClass().protectionDomain.codeSource.location.path).parentFile.parent
def SCRIPTS = new File(scriptsDir, 'scripts')
assert SCRIPTS.isDirectory(), "Pasta de scripts nao encontrada: ${SCRIPTS}"

int passed = 0, failed = 0
def failures = []

def runScript = { String name, Message message ->
    def shell = new GroovyShell(getClass().classLoader)
    def script = shell.parse(new File(SCRIPTS, name))
    script.invokeMethod('processData', message)
}

def test = { String description, Closure body ->
    try {
        body()
        passed++
        println "  PASS  ${description}"
    } catch (AssertionError | Exception e) {
        failed++
        failures << "${description}: ${e.message?.split('\n')?.head()}"
        println "  FAIL  ${description} -> ${e.message?.split('\n')?.head()}"
    }
}

def slurper = new JsonSlurper()

def canonicalRequest = [
    externalId       : 'S4-4500001234',
    name             : 'Contrato 4500001234',
    language         : 'pt-br',
    sequentialSigning: true,
    mergeIfExists    : true,
    document         : [fileName: 'contrato.pdf', contentBase64: 'JVBERi0='],
    signers          : [
        [name: 'Maria', email: 'maria@x.com', order: 1, signatureType: 'Click'],
        [name : 'Joao', email: 'joao@x.com', phone: '5511999999999', order: 2,
         signatureType: 'Draw', notificationChannel: 'Whatsapp',
         signatures   : [[printSignature: true,
                          positions     : [[page: 1, coordenateX: '10.5%', coordenateY: '80.0%']]]]],
    ],
    metadata         : [[key: 'SAP_SYSTEM', value: 'S4PRD']],
]

println '\n== create_operation_prepare_upload.groovy =='

test('payload valido gera body de upload e guarda canonicalRequest') {
    def msg = new Message(body: JsonOutput.toJson(canonicalRequest))
    runScript('create_operation_prepare_upload.groovy', msg)
    def upload = slurper.parseText(msg.getBody(String))
    assert upload.fileName == 'contrato.pdf'
    assert upload.content == 'JVBERi0='
    assert msg.getProperty('canonicalRequest') != null
    assert msg.getHeaders()['X-Api-Key'] == '11111111-2222-3333-4444-555555555555'
    assert !msg.getHeaders().containsKey('Authorization')
}

test('Draw sem signatures[].positions e rejeitado') {
    def bad = slurper.parseText(JsonOutput.toJson(canonicalRequest))
    bad.signers[1].remove('signatures')
    def msg = new Message(body: JsonOutput.toJson(bad))
    try {
        runScript('create_operation_prepare_upload.groovy', msg)
        assert false, 'deveria ter lancado IllegalArgumentException'
    } catch (IllegalArgumentException e) {
        assert e.message.contains('signatures[].positions')
    }
}

test('SMS/Whatsapp sem phone e rejeitado') {
    def bad = slurper.parseText(JsonOutput.toJson(canonicalRequest))
    bad.signers[1].remove('phone')
    def msg = new Message(body: JsonOutput.toJson(bad))
    try {
        runScript('create_operation_prepare_upload.groovy', msg)
        assert false, 'deveria ter lancado IllegalArgumentException'
    } catch (IllegalArgumentException e) {
        assert e.message.contains('phone')
    }
}

test('campos obrigatorios ausentes sao listados') {
    def msg = new Message(body: JsonOutput.toJson([signers: []]))
    try {
        runScript('create_operation_prepare_upload.groovy', msg)
        assert false, 'deveria ter lancado IllegalArgumentException'
    } catch (IllegalArgumentException e) {
        assert e.message.contains('name')
        assert e.message.contains('document.fileName')
    }
}

println '\n== create_operation_build_request.groovy =='

def uploadEnvelope = JsonOutput.toJson([
    success: true, statusCode: 200,
    data   : [id: 'up-guid-123', fileName: 'up-guid-123.pdf', token: 'tok', totalPages: 2],
])

test('monta OperationViewModel e injeta documentId do upload nas signatures') {
    def msg = new Message(body: uploadEnvelope)
    msg.setProperty('canonicalRequest', JsonOutput.toJson(canonicalRequest))
    runScript('create_operation_build_request.groovy', msg)
    def op = slurper.parseText(msg.getBody(String))
    assert op.name == 'Contrato 4500001234'
    assert op.order == true
    assert op.externalId == 'S4-4500001234'
    assert op.mergeIfExists == true
    assert op.files[0].id == 'up-guid-123'
    assert op.members.size() == 2
    assert op.members[0].orderPosition == 1
    assert op.members[1].signatures[0].documentId == 'up-guid-123'
    assert op.members[1].signatures[0].positions[0].coordenateX == '10.5%'
}

test('upload sem id no envelope falha com mensagem clara') {
    def msg = new Message(body: JsonOutput.toJson([success: false, statusCode: 400, data: null]))
    msg.setProperty('canonicalRequest', JsonOutput.toJson(canonicalRequest))
    try {
        runScript('create_operation_build_request.groovy', msg)
        assert false, 'deveria ter lancado IllegalStateException'
    } catch (IllegalStateException e) {
        assert e.message.contains('Upload nao retornou o id')
    }
}

println '\n== create_operation_map_response.groovy =='

def createEnvelope = JsonOutput.toJson([
    success: true, statusCode: 201,
    data   : [
        success: true, statusCode: 200,
        data   : [
            id        : 4821, name: 'Contrato 4500001234', status: 'InProgress',
            externalId: 'S4-4500001234',
            members   : [[id: 99201, name: 'Maria', email: 'maria@x.com', order: 1, observer: false,
                          uri: 'https://app.forsign.digital/sign/abc',
                          shortenedSignatureUrl: 'https://fsign.app/abc']],
            observers : [[id: 99200, name: 'Ana', email: 'ana@x.com', order: 0, observer: true]],
            files     : [[id: 1, name: 'contrato.pdf', description: 'contrato.pdf', type: 'Document',
                          documentId: '9f2e51d0-7a44-4c3a-b1e6-2d8c0f5a7b31']],
        ],
    ],
])

test('desembrulha envelope duplo (data.data) e mapeia ids corretos') {
    def msg = new Message(body: createEnvelope)
    runScript('create_operation_map_response.groovy', msg)
    def canonical = slurper.parseText(msg.getBody(String))
    assert canonical.operationId == 4821
    assert canonical.status == 'InProgress'
    assert canonical.members.size() == 2
    assert canonical.members[0].signUrl == 'https://app.forsign.digital/sign/abc'
    assert canonical.documents[0].documentId == 1
    assert canonical.documents[0].definitiveDocumentId == '9f2e51d0-7a44-4c3a-b1e6-2d8c0f5a7b31'
}

println '\n== unwrap_response.groovy =='

test('desembrulha envelope simples com data') {
    def msg = new Message(body: JsonOutput.toJson([success: true, statusCode: 200, data: [operation: [status: 'Completed']]]))
    runScript('unwrap_response.groovy', msg)
    assert slurper.parseText(msg.getBody(String)).operation.status == 'Completed'
}

test('body sem envelope passa intacto') {
    def raw = JsonOutput.toJson([fileName: 'x.pdf', base64Content: 'AAA'])
    def msg = new Message(body: raw)
    runScript('unwrap_response.groovy', msg)
    assert msg.getBody(String) == raw
}

println '\n== webhook_validate_hmac.groovy =='

def sign = { String body, String secret ->
    def mac = Mac.getInstance('HmacSHA256')
    mac.init(new SecretKeySpec(secret.getBytes('UTF-8'), 'HmacSHA256'))
    'sha256=' + mac.doFinal(body.getBytes('UTF-8')).encodeHex().toString()
}

test('assinatura HMAC valida passa') {
    def body = '{"action":5,"data":{"externalId":"S4-1"}}'
    def msg = new Message(body: body)
    msg.setHeader('X-ForSign-Signature', sign(body, 'test-webhook-secret'))
    runScript('webhook_validate_hmac.groovy', msg)
}

test('assinatura HMAC invalida e rejeitada') {
    def msg = new Message(body: '{"a":1}')
    msg.setHeader('X-ForSign-Signature', 'sha256=deadbeef')
    try {
        runScript('webhook_validate_hmac.groovy', msg)
        assert false, 'deveria ter lancado SecurityException'
    } catch (SecurityException e) {
        assert e.message.toLowerCase().contains('hmac')
    }
}

test('header ausente e rejeitado') {
    def msg = new Message(body: '{}')
    try {
        runScript('webhook_validate_hmac.groovy', msg)
        assert false, 'deveria ter lancado SecurityException'
    } catch (SecurityException expected) {
    }
}

println '\n== webhook_map_event.groovy =='

test('mapeia WebhookData para evento canonico') {
    def webhook = [
        action: 5, actionDescription: 'DocumentReady', success: true,
        createdAt: '2026-07-16T09:30:00', webHookId: 7,
        data  : [
            id: 'op-guid', operationCompanyId: 4821, externalId: 'S4-4500001234',
            name: 'Contrato', status: 5,
            files: [[id: 1, originalName: 'contrato.pdf', totalPages: 12]],
            members: [[name: 'Maria', email: 'maria@x.com']],
            metadata: [[key: 'SAP_OBJECT_KEY', value: '4500001234']],
        ],
    ]
    def msg = new Message(body: JsonOutput.toJson(webhook))
    msg.setHeader('X-ForSign-Signature', 'sha256=x')
    msg.setHeader('Authorization', 'Basic abc')
    runScript('webhook_map_event.groovy', msg)
    def event = slurper.parseText(msg.getBody(String))
    assert event.event == 'DocumentReady'
    assert event.operationId == 4821
    assert event.externalId == 'S4-4500001234'
    assert event.documents[0].documentId == 1
    assert msg.getProperty('webhookAction') == 'DocumentReady'
    assert !msg.getHeaders().containsKey('X-ForSign-Signature')
}

println '\n== status/download prepare =='

test('status: extrai operationId da query e injeta X-Api-Key') {
    def msg = new Message(body: '')
    msg.setHeader('CamelHttpQuery', 'operationId=4821')
    runScript('status_prepare_request.groovy', msg)
    assert msg.getProperty('operationId') == '4821'
    assert msg.getHeaders()['X-Api-Key'] != null
    assert !msg.getHeaders().containsKey('Authorization')
}

test('status: operationId ausente e rejeitado') {
    def msg = new Message(body: '')
    try {
        runScript('status_prepare_request.groovy', msg)
        assert false, 'deveria ter lancado IllegalArgumentException'
    } catch (IllegalArgumentException expected) {
    }
}

test('download: extrai operationId e documentId') {
    def msg = new Message(body: '')
    msg.setHeader('CamelHttpQuery', 'operationId=4821&documentId=1')
    runScript('download_prepare_request.groovy', msg)
    assert msg.getProperty('operationId') == '4821'
    assert msg.getProperty('documentId') == '1'
}

println '\n== error_response.groovy =='

test('IllegalArgumentException vira 400 ValidationError') {
    def msg = new Message(body: 'ignored')
    msg.setProperty('CamelExceptionCaught', new IllegalArgumentException('Campos obrigatorios ausentes: name'))
    runScript('error_response.groovy', msg)
    def error = slurper.parseText(msg.getBody(String)).error
    assert error.httpStatus == 400
    assert error.type == 'ValidationError'
    assert msg.getHeaders()['CamelHttpResponseCode'] == 400
}

test('SecurityException vira 401 SecurityError') {
    def msg = new Message(body: '')
    msg.setProperty('CamelExceptionCaught', new SecurityException('Webhook rejeitado: assinatura HMAC invalida.'))
    runScript('error_response.groovy', msg)
    assert slurper.parseText(msg.getBody(String)).error.httpStatus == 401
}

test('excecao generica vira 500 IntegrationError') {
    def msg = new Message(body: '')
    msg.setProperty('CamelExceptionCaught', new RuntimeException('boom'))
    runScript('error_response.groovy', msg)
    def error = slurper.parseText(msg.getBody(String)).error
    assert error.httpStatus == 500
    assert error.type == 'IntegrationError'
}

println "\n${passed} passed, ${failed} failed"
if (failed > 0) {
    failures.each { println "  - ${it}" }
    System.exit(1)
}
