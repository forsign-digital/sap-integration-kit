package com.sap.gateway.ip.core.customdev.util

/*
 * Mock da Message do CPI para testes locais dos scripts Groovy.
 * Implementa apenas a superfície usada pelos scripts do kit.
 */
class Message {
    Object body
    Map<String, Object> headers = [:]
    Map<String, Object> properties = [:]

    Object getBody() { body }

    Object getBody(Class clazz) {
        if (body == null) return null
        if (clazz == String) return body.toString()
        return body
    }

    void setBody(Object value) { body = value }

    Map<String, Object> getHeaders() { headers }

    void setHeader(String name, Object value) { headers[name] = value }

    void removeHeader(String name) { headers.remove(name) }

    Object getProperty(String name) { properties[name] }

    void setProperty(String name, Object value) { properties[name] = value }
}
