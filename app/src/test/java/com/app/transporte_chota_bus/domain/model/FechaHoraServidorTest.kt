package com.app.transporte_chota_bus.domain.model
import org.junit.Test
import org.junit.Assert.*

class FechaHoraServidorTest{
    @Test fun utcYPeruRepresentanElMismoInstante(){
        assertEquals(FechaHoraServidor.instante("2026-11-13T18:51:36.376233Z"),FechaHoraServidor.instante("2026-11-13T13:51:36.376233-05:00"))
    }
    @Test fun aceptaUtcSinFraccion(){assertNotNull(FechaHoraServidor.instante("2026-11-14T13:00:00Z"))}
    @Test(expected=java.time.format.DateTimeParseException::class) fun rechazaFechasSinZona(){FechaHoraServidor.instante("2026-11-14T13:00:00")}
}
