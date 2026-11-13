package com.app.transporte_chota_bus.data.remote

import com.google.gson.JsonObject
import com.app.transporte_chota_bus.data.model.PasajeroReserva
import retrofit2.http.*

data class SolicitudPasajeros(val pasajeros: List<PasajeroReserva>)

data class SolicitudReserva(val salidaId: Int, val asientos: List<Int>, val solicitudId: String)

// Cada peticion protegida lleva el ID token; el cuerpo nunca contiene UID ni precio.
interface ReservasApi {
    @GET("api/v1/historial")
    suspend fun historial(@Header("Authorization") token:String,@Query("despues") despues:String?):JsonObject

    @GET("api/v1/boletos")
    suspend fun misBoletos(@Header("Authorization") token:String,@Query("despues") despues:String?):JsonObject
    @GET("api/v1/boletos/{id}")
    suspend fun boleto(@Header("Authorization") token:String,@Path("id") id:String):JsonObject

    @GET("api/v1/reservas/{id}/boletos")
    suspend fun boletos(@Header("Authorization") token:String,@Path("id") id:String):JsonObject

    @GET("api/v1/saldo-prueba")
    suspend fun saldoPrueba(@Header("Authorization") token:String):JsonObject
    @POST("api/v1/reservas/{id}/pago-prueba")
    suspend fun pagarPrueba(@Header("Authorization") token:String,@Path("id") id:String):JsonObject

    @GET("api/v1/reservas/{id}/resumen")
    suspend fun resumen(@Header("Authorization") token: String, @Path("id") id: String): JsonObject
    @PUT("api/v1/reservas/{id}/pasajeros")
    suspend fun pasajeros(@Header("Authorization") token: String, @Path("id") id: String, @Body solicitud: SolicitudPasajeros): JsonObject
    @GET("api/v1/salidas/{id}/asientos")
    suspend fun asientos(@Header("Authorization") token: String, @Path("id") id: Int): JsonObject
    @POST("api/v1/reservas")
    suspend fun reservar(@Header("Authorization") token: String, @Body solicitud: SolicitudReserva): JsonObject
    @GET("api/v1/reservas/{id}")
    suspend fun consultar(@Header("Authorization") token: String, @Path("id") id: String): JsonObject
    @POST("api/v1/reservas/{id}/cancelacion")
    suspend fun cancelar(@Header("Authorization") token: String, @Path("id") id: String): JsonObject
}
