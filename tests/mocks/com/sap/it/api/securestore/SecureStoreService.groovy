package com.sap.it.api.securestore

/*
 * Mock do SecureStoreService do CPI. Os aliases/valores são configurados
 * pelos testes via SecureStoreService.credentials.
 */
class SecureStoreService {
    static Map<String, String> credentials = [
        'ForSign_ApiKey'       : '11111111-2222-3333-4444-555555555555',
        'ForSign_WebhookSecret': 'test-webhook-secret',
    ]

    UserCredential getUserCredential(String alias) {
        def value = credentials[alias]
        return value == null ? null : new UserCredential(value)
    }
}

class UserCredential {
    private final String value

    UserCredential(String value) { this.value = value }

    char[] getPassword() { value.toCharArray() }
}
