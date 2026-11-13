package com.app.transporte_chota_bus.domain.model

// Catálogo inicial de sedes; las salidas disponibles se consultarán en la API.
object SedesChotaBus {
    val nombres: List<String> = listOf("Chota", "Cajamarca", "Jaén", "Lajas")

    fun esSeleccionValida(origen: String, destino: String): Boolean =
        origen in nombres && destino in nombres && origen != destino
}
