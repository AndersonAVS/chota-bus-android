package com.app.transporte_chota_bus.ui.auth

import org.junit.Assert.*
import org.junit.Test

class ValidadorAccesoTest {
    @Test fun loginExigeCorreoYContrasena() {
        assertEquals(CampoAcceso.entries.toSet(), ValidadorAcceso.validarLogin("", "").keys)
    }

    @Test fun loginRechazaCorreoIncorrecto() {
        assertTrue(ValidadorAcceso.validarLogin("maria@", "clave").containsKey(CampoAcceso.CORREO))
    }

    @Test fun loginNoImponeLongitudDeRegistro() {
        assertTrue(ValidadorAcceso.validarLogin("maria@example.com", "123").isEmpty())
    }

    @Test fun loginAceptaCorreoConEspaciosExternos() {
        assertTrue(ValidadorAcceso.validarLogin(" maria@example.com ", "clave").isEmpty())
    }

    @Test fun contrasenaPresenteNoSeRecorta() {
        assertTrue(ValidadorAcceso.validarLogin("maria@example.com", " ").isEmpty())
    }

    @Test fun recuperacionExigeCorreo() {
        assertEquals(setOf(CampoAcceso.CORREO), ValidadorAcceso.validarRecuperacion("   ").keys)
    }

    @Test fun recuperacionRechazaFormatoIncorrecto() {
        for (correo in listOf("maria", "@example.com", "maria @example.com")) {
            assertTrue(ValidadorAcceso.validarRecuperacion(correo).containsKey(CampoAcceso.CORREO))
        }
    }

    @Test fun recuperacionAceptaCorreoValido() {
        assertTrue(ValidadorAcceso.validarRecuperacion(" maria@example.com ").isEmpty())
    }
}
