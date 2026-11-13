package com.app.transporte_chota_bus.data.remote

import com.google.gson.JsonObject
import retrofit2.http.GET
import retrofit2.http.Query

// Retrofit convierte los filtros en parametros de la solicitud HTTP.
interface ViajesApi {
    @GET("api/v1/salidas")
    suspend fun buscar(
        @Query("origen") origen: String,
        @Query("destino") destino: String,
        @Query("fecha") fecha: String,
        @Query("pasajeros") pasajeros: Int
    ): JsonObject
}
