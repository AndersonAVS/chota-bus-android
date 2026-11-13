package com.app.transporte_chota_bus.ui.auth

import org.junit.Assert.*
import org.junit.Test

class ValidadorRegistroTest {
    private fun validar(
        nombre: String = "María Torres",
        dni: String = "01234567",
        celular: String = "987654321",
        correo: String = "maria@example.com",
        contrasena: String = "Clave123",
        confirmacion: String = contrasena,
        terminos: Boolean = true
    ) = ValidadorRegistro.validar(
        nombre, dni, celular, correo, contrasena, confirmacion, terminos
    )

    @Test fun datosValidosIncluyenDniConCeroInicial() {
        assertTrue(validar().isEmpty())
    }

    @Test fun camposVaciosProducenTodosLosErrores() {
        val errores = validar("", "", "", "", "", "", false)
        assertEquals(CampoRegistro.entries.toSet(), errores.keys)
    }

    @Test fun documentosYTelefonosRechazanLetrasYLongitudesIncorrectas() {
        assertTrue(validar(dni = "1234567").containsKey(CampoRegistro.DNI))
        assertTrue(validar(dni = "1234567A").containsKey(CampoRegistro.DNI))
        assertTrue(validar(celular = "98765432A").containsKey(CampoRegistro.CELULAR))
        assertTrue(validar(celular = "98765432").containsKey(CampoRegistro.CELULAR))
    }

    @Test fun correoRechazaFormatoIncorrectoYPermiteEspaciosExternos() {
        assertTrue(validar(correo = "maria@").containsKey(CampoRegistro.CORREO))
        assertTrue(validar(correo = "maria @example.com").containsKey(CampoRegistro.CORREO))
        assertTrue(validar(correo = " maria@example.com ").isEmpty())
    }

    @Test fun contrasenaRespetaLongitudYComparacionExacta() {
        assertTrue(validar(contrasena = "1234567").containsKey(CampoRegistro.CONTRASENA))
        assertTrue(validar(contrasena = "        ").containsKey(CampoRegistro.CONTRASENA))
        assertTrue(validar(confirmacion = "Clave123 ").containsKey(CampoRegistro.CONFIRMACION))
        assertTrue(validar(contrasena = " Clave123 ").isEmpty())
    }

    @Test fun terminosSonObligatorios() {
        assertEquals(setOf(CampoRegistro.TERMINOS), validar(terminos = false).keys)
    }
}
