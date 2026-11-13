package com.app.transporte_chota_bus.domain.model

import com.app.transporte_chota_bus.data.model.TerminalViaje

enum class EstadoUbicacionTerminal { PENDIENTE, MAPA_NO_DISPONIBLE, LISTA }

// Usa solo coordenadas completas y validas; nunca deduce un punto desde la ciudad.
object UbicacionTerminal {
    fun tieneCoordenadas(terminal: TerminalViaje): Boolean {
        val latitud = terminal.latitud ?: return false
        val longitud = terminal.longitud ?: return false
        return latitud.isFinite() && longitud.isFinite() &&
            latitud in -90.0..90.0 && longitud in -180.0..180.0
    }

    fun estado(terminal: TerminalViaje, configurado: Boolean, serviciosDisponibles: Boolean): EstadoUbicacionTerminal = when {
        !tieneCoordenadas(terminal) -> EstadoUbicacionTerminal.PENDIENTE
        !configurado || !serviciosDisponibles -> EstadoUbicacionTerminal.MAPA_NO_DISPONIBLE
        else -> EstadoUbicacionTerminal.LISTA
    }

    // Abre indicaciones en Google Maps sin enviar la ubicacion personal del usuario.
    fun enlaceComoLlegar(terminal: TerminalViaje): String? {
        if (!tieneCoordenadas(terminal)) return null
        return "https://www.google.com/maps/dir/?api=1&destination=${terminal.latitud},${terminal.longitud}&travelmode=driving"
    }
}
