package com.app.transporte_chota_bus.ui.inicio

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.*
import org.junit.Test

// Conserva los filtros de la vista y evita cantidades fuera del rango permitido.
class FormularioViajeViewModelTest {
    @Test fun recuperaFiltrosDelEstadoGuardado() {
        val guardado = SavedStateHandle(mapOf("fecha_viaje" to "2026-12-20", "cantidad_pasajeros" to 3))
        val vm = FormularioViajeViewModel(guardado)
        assertEquals("2026-12-20", vm.fecha.value)
        assertEquals(3, vm.pasajeros.value)
        vm.seleccionarPasajeros(4)
        assertEquals(4, guardado.get<Int>("cantidad_pasajeros"))
    }
    @Test fun ignoraCantidadesInvalidas() {
        val vm = FormularioViajeViewModel(SavedStateHandle())
        vm.seleccionarPasajeros(0)
        vm.seleccionarPasajeros(10)
        assertEquals(1, vm.pasajeros.value)
    }
}
