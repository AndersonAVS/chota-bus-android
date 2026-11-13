package com.app.transporte_chota_bus.domain.model

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

// Comprueba fechas reales y los limites del formulario sin depender del reloj.
class FiltrosViajeTest {
    private val hoy = LocalDate.of(2026, 11, 13)
    private fun filtros(fecha: String = "2026-11-14", pasajeros: Int = 1) = FiltrosViaje("Chota", "Cajamarca", fecha, pasajeros)
    @Test fun aceptaHoyYFuturo() {
        assertNull(filtros("2026-11-13").error(hoy))
        assertNull(filtros().error(hoy))
    }
    @Test fun rechazaFechaPasada() { assertNotNull(filtros("2026-11-12").error(hoy)) }
    @Test fun rechazaFechaInexistente() { assertNotNull(filtros("2027-02-29").error(hoy)) }
    @Test fun rechazaFechaVacia() { assertNotNull(filtros("").error(hoy)) }
    @Test fun validaLimitesDePasajeros() {
        assertNotNull(filtros(pasajeros = 0).error(hoy))
        assertNotNull(filtros(pasajeros = 10).error(hoy))
        assertNull(filtros(pasajeros = 9).error(hoy))
    }
    @Test fun rechazaSedesIgualesODesconocidas() {
        assertNotNull(FiltrosViaje("Chota", "Chota", "2026-11-14", 1).error(hoy))
        assertNotNull(FiltrosViaje("Lima", "Chota", "2026-11-14", 1).error(hoy))
    }
}
