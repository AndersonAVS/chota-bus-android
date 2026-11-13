package com.app.transporte_chota_bus.data.model

// Representa el inventario consultado y la reserva temporal devuelta por PostgreSQL.
data class AsientoDisponible(val numero: Int, val fila: Int, val columna: Int, val disponible: Boolean)
data class InventarioAsientos(val salidaId: Int, val precio: String, val asientos: List<AsientoDisponible>)
enum class EstadoReserva { PENDIENTE, CANCELADA, EXPIRADA, CONFIRMADA }
data class ReservaAsientos(
    val id: String, val salidaId: Int, val estado: EstadoReserva, val asientos: List<Int>,
    val precio: String, val total: String, val venceEn: String, val horaServidor: String
)
enum class TipoErrorReserva { SESION, CONFLICTO, RECHAZADA, NO_ENCONTRADO, RED, DATOS }
class ErrorReserva(val tipo: TipoErrorReserva, mensaje: String) : Exception(mensaje)
