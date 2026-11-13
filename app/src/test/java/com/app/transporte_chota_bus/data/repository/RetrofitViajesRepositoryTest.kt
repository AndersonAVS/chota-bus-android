package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.remote.ViajesApi
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import com.google.gson.JsonObject
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitViajesRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: RetrofitViajesRepository
    private val filtros = FiltrosViaje("Chota", "Jaén", LocalDate.now(ZoneId.of("America/Lima")).plusDays(1).toString(), 2)
    @Before fun preparar() {
        server = MockWebServer()
        server.start()
        val api = Retrofit.Builder().baseUrl(server.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(ViajesApi::class.java)
        repo = RetrofitViajesRepository(api)
    }
    @After fun terminar() { server.shutdown() }

    // Verifica HTTP y JSON reales contra un servidor local de prueba.
    @Test fun enviaFiltrosCodificadosYLeeListaVacia() = runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[],"hayMas":false,"disponibilidadConfirmada":false}"""))
        val resultado = repo.buscar(filtros).getOrThrow()
        assertTrue(resultado.salidas.isEmpty())
        val url = server.takeRequest().requestUrl!!
        assertEquals("/api/v1/salidas", url.encodedPath)
        assertEquals("Jaén", url.queryParameter("destino"))
        assertEquals(filtros.fecha, url.queryParameter("fecha"))
        assertEquals("2", url.queryParameter("pasajeros"))
    }
    @Test fun convierteSalidasYTerminalesSinCoordenadas() = runTest {
        server.enqueue(MockResponse().setBody(respuesta))
        val salida = repo.buscar(filtros).getOrThrow().salidas.single()
        assertEquals("30.00", salida.precio)
        assertEquals("Chota", salida.embarque.ciudad)
        assertEquals("Jaén", salida.destino.ciudad)
        assertNull(salida.embarque.latitud)
    }
    @Test fun rechazaRespuestaIncompleta() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(repo.buscar(filtros).isFailure)
    }
    @Test fun rechazaHorarioIncoherente() = runTest {
        server.enqueue(MockResponse().setBody(respuesta.replace("16:00:00Z", "12:00:00Z")))
        assertTrue(repo.buscar(filtros).isFailure)
    }
    @Test fun rechazaJsonMalformado() = runTest {
        server.enqueue(MockResponse().setBody("{no-es-json"))
        assertTrue(repo.buscar(filtros).isFailure)
    }
    @Test fun falloHttpNoExponeElCuerpoInterno() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("informacion-interna"))
        val error = repo.buscar(filtros).exceptionOrNull()!!
        assertFalse(error.message!!.contains("informacion-interna"))
    }
    @Test fun filtroInvalidoNoHacePeticion() = runTest {
        assertTrue(repo.buscar(filtros.copy(pasajeros = 0)).isFailure)
        assertEquals(0, server.requestCount)
    }
    @Test fun propagaCancelacion() = runTest {
        val api = object : ViajesApi {
            override suspend fun buscar(origen: String, destino: String, fecha: String, pasajeros: Int): JsonObject {
                throw CancellationException("cancelado")
            }
        }
        try {
            RetrofitViajesRepository(api).buscar(filtros)
            fail("Debe propagar cancelación")
        } catch (_: CancellationException) { }
    }

    private val respuesta = """{
      "datos":[{"id":1,"fecha_hora_salida":"2026-12-20T13:00:00Z","fecha_hora_llegada":"2026-12-20T16:00:00Z",
        "precio":"30.00","moneda":"PEN","capacidad":15,
        "embarque":{"id":1,"nombre":"DEMO Chota","ciudad":"Chota","direccion":"Direccion de prueba","latitud":null,"longitud":null},
        "llegada":{"id":2,"nombre":"DEMO Jaen","ciudad":"Jaén","direccion":"Direccion de prueba","latitud":null,"longitud":null}}],
      "hayMas":false,"disponibilidadConfirmada":false
    }"""
}
