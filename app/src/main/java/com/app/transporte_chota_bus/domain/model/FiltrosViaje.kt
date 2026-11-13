package com.app.transporte_chota_bus.domain.model

import java.time.LocalDate
import java.time.ZoneId

// Define los filtros y valida que la fecha corresponda al calendario de Peru.
data class FiltrosViaje(val origen: String, val destino: String, val fecha: String, val pasajeros: Int) {
    fun error(hoy: LocalDate = LocalDate.now(ZoneId.of("America/Lima"))): String? {
        if (!SedesChotaBus.esSeleccionValida(origen, destino)) return "Selecciona sedes diferentes."
        val dia = runCatching { LocalDate.parse(fecha) }.getOrNull()
            ?: return "Selecciona una fecha de viaje válida."
        if (!Regex("20\\d{2}-\\d{2}-\\d{2}").matches(fecha) || dia.isBefore(hoy))
            return "Selecciona una fecha desde hoy hasta el año 2099."
        if (pasajeros !in 1..9) return "Selecciona entre 1 y 9 pasajeros."
        return null
    }
}
