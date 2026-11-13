package com.app.transporte_chota_bus.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.transporte_chota_bus.data.model.PreferenciasBusqueda
import com.app.transporte_chota_bus.domain.model.SedesChotaBus
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DataStorePreferenciasRepository(
    private val dataStore: DataStore<Preferences>
) : PreferenciasRepository {
    private companion object {
        val ORIGEN = stringPreferencesKey("ultimo_origen")
        val DESTINO = stringPreferencesKey("ultimo_destino")
    }

    // Recupera las preferencias y descarta combinaciones inválidas guardadas.
    override val preferencias: Flow<PreferenciasBusqueda> = dataStore.data.map { datos ->
        val valoresIniciales = PreferenciasBusqueda()
        val origen = datos[ORIGEN] ?: valoresIniciales.origen
        val destino = datos[DESTINO] ?: valoresIniciales.destino
        if (SedesChotaBus.esSeleccionValida(origen, destino)) {
            PreferenciasBusqueda(origen, destino)
        } else valoresIniciales
    }

    // Guarda ambos valores en una sola operación y comunica errores de disco.
    override suspend fun guardarRuta(origen: String, destino: String): Result<Unit> {
        if (!SedesChotaBus.esSeleccionValida(origen, destino)) {
            return Result.failure(IllegalArgumentException("Selecciona sedes diferentes y válidas."))
        }
        return try {
            dataStore.edit { datos ->
                datos[ORIGEN] = origen
                datos[DESTINO] = destino
            }
            Result.success(Unit)
        } catch (error: IOException) {
            Result.failure(error)
        }
    }
}
