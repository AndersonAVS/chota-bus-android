package com.app.transporte_chota_bus.data.model

// Representa las selecciones locales del formulario de búsqueda.
data class PreferenciasBusqueda(
    val origen: String = "Chota",
    val destino: String = "Cajamarca"
)
