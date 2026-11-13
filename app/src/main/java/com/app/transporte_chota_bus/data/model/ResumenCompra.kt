package com.app.transporte_chota_bus.data.model

// Asocia cada pasajero con un asiento y mantiene el resumen autorizado del servidor.
data class PasajeroReserva(val asiento: Int, val nombreCompleto: String = "", val dni: String = "", val celular: String = "")
data class ResumenCompra(
    val reserva: ReservaAsientos, val fechaSalida: String, val fechaLlegada: String,
    val embarque: TerminalViaje, val destino: TerminalViaje, val pasajeros: List<PasajeroReserva>,
    val datosCompletos: Boolean, val puedeContinuar: Boolean
)
