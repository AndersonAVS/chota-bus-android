package com.app.transporte_chota_bus.data.model

// Representa una salida y sus terminales; capacidad no significa asientos libres.
data class TerminalViaje(
    val id: Int, val nombre: String, val ciudad: String, val direccion: String,
    val latitud: Double?, val longitud: Double?
)
data class SalidaViaje(
    val id: Int, val salida: String, val llegada: String, val precio: String,
    val moneda: String, val capacidad: Int, val embarque: TerminalViaje, val destino: TerminalViaje
)
data class ResultadoViajes(val salidas: List<SalidaViaje>, val hayMas: Boolean)
