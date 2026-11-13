package com.app.transporte_chota_bus.ui.inicio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

// Conserva la fecha y la cantidad al girar el dispositivo o recrear la pantalla.
class FormularioViajeViewModel(private val guardado: SavedStateHandle) : ViewModel() {
    val fecha = guardado.getStateFlow("fecha_viaje", "")
    val pasajeros = guardado.getStateFlow("cantidad_pasajeros", 1)
    fun seleccionarFecha(fecha: String) { guardado["fecha_viaje"] = fecha }
    fun seleccionarPasajeros(cantidad: Int) {
        if (cantidad in 1..9) guardado["cantidad_pasajeros"] = cantidad
    }
}
