package com.app.transporte_chota_bus.domain.model

import com.app.transporte_chota_bus.data.model.TerminalViaje
import org.junit.Assert.*
import org.junit.Test

// Las coordenadas de estas pruebas son sinteticas y no se guardan como sedes reales.
class UbicacionTerminalTest {
    private fun terminal(latitud: Double? = -6.0, longitud: Double? = -78.0) =
        TerminalViaje(1, "Punto de prueba", "Ciudad de prueba", "Direccion de prueba", latitud, longitud)
    @Test fun sinCoordenadasQuedaPendiente() {
        assertEquals(EstadoUbicacionTerminal.PENDIENTE, UbicacionTerminal.estado(terminal(null, null), true, true))
        assertNull(UbicacionTerminal.enlaceComoLlegar(terminal(null, null)))
    }
    @Test fun noAceptaCoordenadasIncompletas() {
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(null, -78.0)))
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(-6.0, null)))
    }
    @Test fun noAceptaCoordenadasFueraDeRango() {
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(91.0)))
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(longitud = -181.0)))
    }
    @Test fun noAceptaValoresNoFinitos() {
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(Double.NaN)))
        assertFalse(UbicacionTerminal.tieneCoordenadas(terminal(longitud = Double.POSITIVE_INFINITY)))
    }
    @Test fun noMuestraMapaSinConfiguracion() {
        assertEquals(EstadoUbicacionTerminal.MAPA_NO_DISPONIBLE, UbicacionTerminal.estado(terminal(), false, true))
    }
    @Test fun noMuestraMapaSinServiciosGoogle() {
        assertEquals(EstadoUbicacionTerminal.MAPA_NO_DISPONIBLE, UbicacionTerminal.estado(terminal(), true, false))
    }
    @Test fun muestraMapaCuandoEstanTodosLosRequisitos() {
        assertEquals(EstadoUbicacionTerminal.LISTA, UbicacionTerminal.estado(terminal(), true, true))
    }
    @Test fun enlaceSoloIncluyeDestinoYNoUbicacionPersonal() {
        val enlace = UbicacionTerminal.enlaceComoLlegar(terminal())!!
        assertEquals("https://www.google.com/maps/dir/?api=1&destination=-6.0,-78.0&travelmode=driving", enlace)
        assertFalse(enlace.contains("origin="))
    }
}
