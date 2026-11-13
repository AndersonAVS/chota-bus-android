package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.remote.ViajesApi
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.math.BigDecimal
import java.time.Instant

class RetrofitViajesRepository(private val api: ViajesApi) : ViajesRepository {
    // Valida la respuesta y transforma los fallos en mensajes sin informacion interna.
    override suspend fun buscar(filtros: FiltrosViaje): Result<ResultadoViajes> {
        filtros.error()?.let { return Result.failure(IllegalArgumentException(it)) }
        return try {
            val json = api.buscar(filtros.origen, filtros.destino, filtros.fecha, filtros.pasajeros)
            val elementos = requireNotNull(json.getAsJsonArray("datos"))
            require(json.has("hayMas") && json.has("disponibilidadConfirmada"))
            val salidas = elementos.map { elemento ->
                val dato = elemento.asJsonObject
                val salida = dato.texto("fecha_hora_salida")
                val llegada = dato.texto("fecha_hora_llegada")
                require(Instant.parse(llegada).isAfter(Instant.parse(salida)))
                val precio = dato.texto("precio")
                require(BigDecimal(precio) > BigDecimal.ZERO)
                val capacidad = dato.get("capacidad").asInt
                val id = dato.get("id").asInt
                require(id > 0 && capacidad > 0 && dato.texto("moneda") == "PEN")
                SalidaViaje(id, salida, llegada, precio, "PEN", capacidad,
                    terminal(dato.getAsJsonObject("embarque")), terminal(dato.getAsJsonObject("llegada")))
            }
            Result.success(ResultadoViajes(salidas, json.get("hayMas").asBoolean))
        } catch (cancelacion: CancellationException) {
            throw cancelacion
        } catch (error: HttpException) {
            val mensaje = if (error.code() == 400) "Revisa los datos de tu búsqueda."
                else "El servicio de viajes no está disponible. Intenta nuevamente."
            Result.failure(IOException(mensaje))
        } catch (error: IOException) {
            Result.failure(IOException("No pudimos conectar con el servicio de viajes. Intenta nuevamente."))
        } catch (error: Exception) {
            Result.failure(IOException("El servicio devolvió datos de viajes inválidos. Intenta nuevamente."))
        }
    }

    private fun JsonObject.texto(clave: String): String = get(clave).asString.also { require(it.isNotBlank()) }

    // Las coordenadas son opcionales hasta que cada terminal sea validado.
    private fun terminal(json: JsonObject): TerminalViaje {
        val id = json.get("id").asInt.also { require(it > 0) }
        val latitud = json.get("latitud")?.takeUnless { it.isJsonNull }?.asDouble
        val longitud = json.get("longitud")?.takeUnless { it.isJsonNull }?.asDouble
        require((latitud == null && longitud == null) ||
            (latitud != null && longitud != null && latitud in -90.0..90.0 && longitud in -180.0..180.0))
        return TerminalViaje(id, json.texto("nombre"), json.texto("ciudad"), json.texto("direccion"), latitud, longitud)
    }
}
