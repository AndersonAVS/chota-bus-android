package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.PreferenciasBusqueda
import kotlinx.coroutines.flow.Flow

// Define la lectura y escritura de preferencias sin depender de la interfaz.
interface PreferenciasRepository {
    val preferencias: Flow<PreferenciasBusqueda>

    suspend fun guardarRuta(origen: String, destino: String): Result<Unit>
}
