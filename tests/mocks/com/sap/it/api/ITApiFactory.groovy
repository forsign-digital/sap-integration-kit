package com.sap.it.api

import com.sap.it.api.securestore.SecureStoreService

/* Mock do ITApiFactory do CPI. */
class ITApiFactory {
    static Object getService(Class clazz, Object context) {
        if (clazz == SecureStoreService) return new SecureStoreService()
        throw new IllegalArgumentException("Servico nao mockado: ${clazz}")
    }
}
