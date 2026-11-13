package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.ResultadoViajes
import com.app.transporte_chota_bus.domain.model.FiltrosViaje

// Separa la consulta de viajes de la pantalla y permite probarla sin un servidor real.
interface ViajesRepository {
    suspend fun buscar(filtros: FiltrosViaje): Result<ResultadoViajes>
}
