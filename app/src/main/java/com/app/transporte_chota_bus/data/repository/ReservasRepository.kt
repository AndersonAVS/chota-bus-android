package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.*

// Permite probar la compra sin depender de Firebase o de una conexion real.
interface ReservasRepository {
    suspend fun consultarHistorial(despues:String?=null):Result<PaginaHistorial> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"No se pudo consultar el historial."))

    suspend fun consultarMisBoletos(despues:String?=null):Result<PaginaBoletos> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"No se pudieron consultar los boletos."))
    suspend fun consultarBoleto(id:String):Result<BoletoPrueba> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"No se pudo consultar el boleto."))

    suspend fun consultarBoletos(id:String):Result<List<BoletoPrueba>> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"No se pudieron consultar los boletos."))

    suspend fun consultarSaldoPrueba():Result<SaldoPrueba> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"El saldo de prueba no está disponible."))
    suspend fun pagarConSaldoPrueba(id:String):Result<PagoPrueba> = Result.failure(ErrorReserva(TipoErrorReserva.RED,"El pago de prueba no está disponible."))

    suspend fun consultarResumen(id: String): Result<ResumenCompra>
    suspend fun guardarPasajeros(id: String, pasajeros: List<PasajeroReserva>): Result<ResumenCompra>
    fun usuarioActual(): String?
    suspend fun consultarAsientos(salidaId: Int): Result<InventarioAsientos>
    suspend fun reservar(salidaId: Int, asientos: List<Int>, solicitudId: String): Result<ReservaAsientos>
    suspend fun consultarReserva(id: String): Result<ReservaAsientos>
    suspend fun cancelarReserva(id: String): Result<ReservaAsientos>
}
