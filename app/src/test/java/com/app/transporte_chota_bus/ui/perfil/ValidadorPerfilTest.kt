package com.app.transporte_chota_bus.ui.perfil

import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import com.app.transporte_chota_bus.domain.validation.ValidadorPerfil
import org.junit.Assert.*
import org.junit.Test

class ValidadorPerfilTest {
    @Test fun aceptaNombreConTildesYDniConCeroInicial() {
        assertTrue(ValidadorPerfil.validar(" María Muñoz ", "01234567", "987654321").isEmpty())
    }

    @Test fun exigeTodosLosDatos() {
        assertEquals(CampoPerfil.entries.toSet(), ValidadorPerfil.validar(" ", "", "").keys)
    }

    @Test fun limitaElNombreSegunLasReglasDelPerfil() {
        assertTrue(ValidadorPerfil.validar("a".repeat(201), "01234567", "987654321").containsKey(CampoPerfil.NOMBRE))
        assertTrue(ValidadorPerfil.validar("a".repeat(200), "01234567", "987654321").isEmpty())
    }

    @Test fun rechazaLetrasYLongitudesIncorrectasEnDatosNumericos() {
        assertEquals(setOf(CampoPerfil.DNI, CampoPerfil.CELULAR),
            ValidadorPerfil.validar("María", "1234567A", "12345678").keys)
    }
}
