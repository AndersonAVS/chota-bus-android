package com.app.transporte_chota_bus.domain.model

import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

// Acepta UTC y desplazamientos ISO de PostgreSQL en todas las versiones de Android soportadas.
object FechaHoraServidor {
    fun instante(valor:String):Instant = OffsetDateTime.parse(valor,DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
}
