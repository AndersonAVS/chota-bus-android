package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.remote.*
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RetrofitReservasRepositoryTest {
    private lateinit var server:MockWebServer
    private lateinit var repo:RetrofitReservasRepository
    private lateinit var sesion:SesionFalsa
    private val id="00000000-0000-4000-8000-000000000001"
    @Before fun preparar() {
        server=MockWebServer();server.start();sesion=SesionFalsa()
        val api=Retrofit.Builder().baseUrl(server.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ReservasApi::class.java)
        repo=RetrofitReservasRepository(api,sesion)
    }
    @After fun terminar(){server.shutdown()}

    @Test fun saldoSoloAceptaModoPruebaYMonedaPEN()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":{"saldo":"300.00","moneda":"PEN","modo":"PRUEBA"}}"""))
        assertEquals("300.00",repo.consultarSaldoPrueba().getOrThrow().saldo)
        assertEquals("Bearer token-inicial",server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"datos":{"saldo":"300.00","moneda":"PEN","modo":"REAL"}}"""))
        assertTrue(repo.consultarSaldoPrueba().isFailure)
    }
    @Test fun pagoNoEnviaImporteNiUsuarioYCompruebaReserva()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":{"id":"$id","reserva_id":"$id","importe":"30.00","saldo":"270.00","moneda":"PEN","modo":"PRUEBA"}}"""))
        assertEquals("270.00",repo.pagarConSaldoPrueba(id).getOrThrow().saldo)
        val peticion=server.takeRequest()
        assertEquals("POST",peticion.method);assertEquals(0L,peticion.body.size)
        assertEquals("/api/v1/reservas/$id/pago-prueba",peticion.path)
    }
    @Test fun saldoInsuficientePresentaMensajeClaro()=runTest {
        server.enqueue(MockResponse().setResponseCode(422))
        val e=repo.pagarConSaldoPrueba(id).exceptionOrNull() as ErrorReserva
        assertEquals(TipoErrorReserva.RECHAZADA,e.tipo);assertTrue(e.message!!.contains("saldo"))
    }
    private fun boletoJson(reservaId:String=id)="""{"id":"$id","codigo":"CBP-00000000000040008000000000000001",
        "reserva_id":"$reservaId","asiento":1,"importe_unitario":"30.00","moneda":"PEN","modo":"PRUEBA","estado":"EMITIDO",
        "emitido_en":"2026-11-13T13:51:36.376233-05:00","datos_recuperados":false,
        "pasajero":{"nombreCompleto":"PASAJERO DE PRUEBA","dni":"00000001"},
        "viaje":{"salida_id":3,"fecha_hora_salida":"2026-11-14T08:00:00-05:00","fecha_hora_llegada":"2026-11-14T11:00:00-05:00",
        "embarque":{"id":1,"nombre":"DEMO Chota","ciudad":"Chota","direccion":"Referencia","latitud":null,"longitud":null},
        "llegada":{"id":2,"nombre":"DEMO Cajamarca","ciudad":"Cajamarca","direccion":"Referencia","latitud":null,"longitud":null}}}"""
    @Test fun consultaBoletosConSesionYDatosHistoricos()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[${boletoJson()}]}"""))
        val b=repo.consultarBoletos(id).getOrThrow().single()
        assertEquals("00000001",b.dni);assertEquals("30.00",b.importe);assertEquals("Chota",b.embarque.ciudad)
        val req=server.takeRequest();assertEquals("Bearer token-inicial",req.getHeader("Authorization"))
        assertEquals("/api/v1/reservas/$id/boletos",req.path)
    }
    @Test fun reservaSinPagoPuedeDevolverListaVacia()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[]}"""));assertTrue(repo.consultarBoletos(id).getOrThrow().isEmpty())
    }
    @Test fun rechazaBoletosDeOtraCompraYDuplicados()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[${boletoJson("00000000-0000-4000-8000-000000000002")}]}"""))
        assertTrue(repo.consultarBoletos(id).isFailure)
        server.enqueue(MockResponse().setBody("""{"datos":[${boletoJson()},${boletoJson()}]}"""))
        assertTrue(repo.consultarBoletos(id).isFailure)
    }
    @Test fun rechazaBoletoSinMarcaPruebaOImporteValido()=runTest {
        for(b in listOf(boletoJson().replace("PRUEBA","REAL"),boletoJson().replace("30.00","-1.00"),
            boletoJson().replace("00000001\"","123\""),boletoJson().replace("2026-11-14T11:00:00-05:00","fecha-invalida"))){
            server.enqueue(MockResponse().setBody("""{"datos":[$b]}"""));assertTrue(repo.consultarBoletos(id).isFailure)
        }
    }
    @Test fun noConsultaBoletosSinSesionNiConIdInvalido()=runTest {
        assertTrue(repo.consultarBoletos("incorrecto").isFailure);assertEquals(0,server.requestCount)
        sesion.uid=null;assertTrue(repo.consultarBoletos(id).isFailure);assertEquals(0,server.requestCount)
    }
    @Test fun boletosAjenosNoExponenContenidoDeError()=runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("datos-personales-internos"))
        val e=repo.consultarBoletos(id).exceptionOrNull() as ErrorReserva
        assertEquals(TipoErrorReserva.NO_ENCONTRADO,e.tipo);assertFalse(e.message!!.contains("datos-personales"))
    }
    @Test fun consultaListaGeneralConCursorDelServidor()=runTest {
        val items=(1..20).map{n->com.google.gson.JsonParser.parseString(boletoJson()).asJsonObject.apply{
            addProperty("id","00000000-0000-4000-8000-"+n.toString().padStart(12,'0'))
            addProperty("codigo","CBP-"+n.toString(16).uppercase().padStart(32,'0'))
        }}
        val siguiente=items.last().get("id").asString
        server.enqueue(MockResponse().setBody("""{"datos":[${items.joinToString(",")}],"siguiente":"$siguiente"}"""))
        val p=repo.consultarMisBoletos().getOrThrow();assertEquals(20,p.boletos.size);assertEquals(siguiente,p.siguiente)
        assertEquals("/api/v1/boletos",server.takeRequest().path)
        server.enqueue(MockResponse().setBody("""{"datos":[],"siguiente":null}"""))
        assertTrue(repo.consultarMisBoletos(siguiente).getOrThrow().boletos.isEmpty())
        assertEquals("/api/v1/boletos?despues=$siguiente",server.takeRequest().path)
    }
    @Test fun listaRechazaCursorRepetidoYBoletosDuplicados()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[${boletoJson()}],"siguiente":"$id"}"""))
        assertTrue(repo.consultarMisBoletos(id).isFailure)
        server.enqueue(MockResponse().setBody("""{"datos":[${boletoJson()},${boletoJson()}],"siguiente":null}"""))
        assertTrue(repo.consultarMisBoletos().isFailure)
    }
    @Test fun detalleExigeIdCorrectoYRenuevaToken401()=runTest {
        server.enqueue(MockResponse().setResponseCode(401));server.enqueue(MockResponse().setBody("""{"datos":${boletoJson()}}"""))
        assertEquals(id,repo.consultarBoleto(id).getOrThrow().id)
        assertEquals("Bearer token-inicial",server.takeRequest().getHeader("Authorization"))
        assertEquals("Bearer token-renovado",server.takeRequest().getHeader("Authorization"))
        server.enqueue(MockResponse().setBody("""{"datos":${boletoJson()}}"""))
        assertTrue(repo.consultarBoleto("00000000-0000-4000-8000-000000000002").isFailure)
    }
    private fun historialJson()="""{"id":"$id","salida_id":3,"estado":"CONFIRMADA","creado_en":"2026-11-13T13:00:00.123456-05:00",
        "fecha_hora_salida":"2026-11-14T08:00:00-05:00","origen":"Chota","destino":"Cajamarca","cantidad":2,
        "total_reserva":"60.00","importe_pagado":"60.00","pagada":true,"fecha_pago":"2026-11-13T13:01:00-05:00",
        "asientos":[1,2],"boletos_emitidos":2,"modo":"PRUEBA","moneda":"PEN"}"""
    @Test fun historialLeeUnaCompraPorReservaConTotalPagado()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[${historialJson()}],"siguiente":null}"""))
        val r=repo.consultarHistorial().getOrThrow().registros.single()
        assertEquals("60.00",r.importePagado);assertEquals(2,r.cantidad);assertTrue(r.pagada)
        val peticion=server.takeRequest();assertEquals("/api/v1/historial",peticion.path);assertEquals("Bearer token-inicial",peticion.getHeader("Authorization"))
    }
    @Test fun historialSinPagoNoConfundeImporteReservadoConCobrado()=runTest {
        val d=com.google.gson.JsonParser.parseString(historialJson()).asJsonObject
        d.addProperty("estado","CANCELADA");d.addProperty("pagada",false);d.addProperty("importe_pagado","0.00");d.add("fecha_pago",com.google.gson.JsonNull.INSTANCE);d.addProperty("boletos_emitidos",0)
        server.enqueue(MockResponse().setBody("""{"datos":[$d],"siguiente":null}"""))
        val r=repo.consultarHistorial().getOrThrow().registros.single();assertFalse(r.pagada);assertEquals("0.00",r.importePagado);assertEquals("60.00",r.totalReserva)
    }
    @Test fun historialRechazaEstadosYMontosInconsistentes()=runTest {
        for(d in listOf(historialJson().replace("CONFIRMADA","CANCELADA"),historialJson().replace("\"importe_pagado\":\"60.00\"","\"importe_pagado\":\"1.00\""),
            historialJson().replace("\"boletos_emitidos\":2","\"boletos_emitidos\":1"),historialJson().replace("[1,2]","[1,1]"))){
            server.enqueue(MockResponse().setBody("""{"datos":[$d],"siguiente":null}"""));assertTrue(repo.consultarHistorial().isFailure)
        }
    }
    @Test fun historialVacioYCursorSinSesionNoFabricanCompras()=runTest {
        server.enqueue(MockResponse().setBody("""{"datos":[],"siguiente":null}"""));assertTrue(repo.consultarHistorial().getOrThrow().registros.isEmpty())
        assertEquals(1,server.requestCount);assertTrue(repo.consultarHistorial("invalido").isFailure);assertEquals(1,server.requestCount)
        sesion.uid=null;assertTrue(repo.consultarHistorial().isFailure);assertEquals(1,server.requestCount)
    }
    // Comprueba cabecera, cuerpo, renovacion y respuestas sin usar tokens de personas.
    @Test fun consultaInventarioConBearer()=runTest {
        server.enqueue(MockResponse().setBody(inventario))
        val datos=repo.consultarAsientos(1).getOrThrow()
        assertEquals(1,datos.asientos.size)
        assertEquals("Bearer token-inicial",server.takeRequest().getHeader("Authorization"))
    }
    @Test fun enviaSoloDatosPermitidosYTotalDelServidor()=runTest {
        server.enqueue(MockResponse().setBody(reserva))
        val r=repo.reservar(1,listOf(1),id).getOrThrow()
        val cuerpo=JsonParser.parseString(server.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(setOf("salidaId","asientos","solicitudId"),cuerpo.keySet())
        assertEquals("30.00",r.total)
    }
    @Test fun renueva401UnaVezSinCambiarLaSolicitud()=runTest {
        server.enqueue(MockResponse().setResponseCode(401));server.enqueue(MockResponse().setBody(reserva))
        repo.reservar(1,listOf(1),id).getOrThrow()
        val primero=server.takeRequest();val segundo=server.takeRequest()
        assertEquals(primero.body.readUtf8(),segundo.body.readUtf8())
        assertEquals("Bearer token-renovado",segundo.getHeader("Authorization"))
        assertEquals(listOf(false,true),sesion.forzados)
    }
    @Test fun segundo401ExigeSesionSinOtroReintento()=runTest {
        server.enqueue(MockResponse().setResponseCode(401));server.enqueue(MockResponse().setResponseCode(401))
        val error=repo.consultarAsientos(1).exceptionOrNull() as ErrorReserva
        assertEquals(TipoErrorReserva.SESION,error.tipo)
        assertEquals(2,server.requestCount)
    }
    @Test fun cambioDeUsuarioDuranteRenovacionNoReenvia()=runTest {
        sesion.cambiarAlRenovar=true;server.enqueue(MockResponse().setResponseCode(401))
        val error=repo.consultarAsientos(1).exceptionOrNull() as ErrorReserva
        assertEquals(TipoErrorReserva.SESION,error.tipo)
        assertEquals(1,server.requestCount)
    }
    @Test fun sinSesionNoEnviaPeticion()=runTest {
        sesion.uid=null
        assertTrue(repo.consultarAsientos(1).isFailure)
        assertEquals(0,server.requestCount)
    }
    @Test fun conflictoNoExponeElCuerpoDelServidor()=runTest {
        server.enqueue(MockResponse().setResponseCode(409).setBody("detalle-interno"))
        val error=repo.reservar(1,listOf(1),id).exceptionOrNull() as ErrorReserva
        assertEquals(TipoErrorReserva.CONFLICTO,error.tipo)
        assertFalse(error.message!!.contains("detalle-interno"))
    }
    @Test fun rechazaUnTotalQueNoCoincideConPrecioYCantidad()=runTest {
        server.enqueue(MockResponse().setBody(reserva.replace("\"total\":\"30.00\"","\"total\":\"1.00\"")))
        assertTrue(repo.reservar(1,listOf(1),id).isFailure)
    }
    @Test fun rechazaInventarioConNumeroDuplicado()=runTest {
        server.enqueue(MockResponse().setBody(inventario.replace("\"asientos_totales\":1","\"asientos_totales\":2")
            .replace("\"asientos_libres\":1","\"asientos_libres\":2")
            .replace("[{\"numero\":1,\"fila\":1,\"columna\":1,\"disponible\":true}]",
                "[{\"numero\":1,\"fila\":1,\"columna\":1,\"disponible\":true},{\"numero\":1,\"fila\":1,\"columna\":2,\"disponible\":true}]")))
        assertTrue(repo.consultarAsientos(1).isFailure)
    }
    @Test fun cancelarUsaEndpointYNoEnviaUID()=runTest {
        server.enqueue(MockResponse().setBody(reserva.replace("PENDIENTE","CANCELADA")))
        assertEquals(EstadoReserva.CANCELADA,repo.cancelarReserva(id).getOrThrow().estado)
        val peticion=server.takeRequest()
        assertEquals("POST",peticion.method)
        assertEquals("/api/v1/reservas/$id/cancelacion",peticion.path)
        assertEquals(0,peticion.body.size)
    }

    @Test fun consultaResumenConHorarioYTotalDelServidor()=runTest {
        server.enqueue(MockResponse().setBody(resumenJson()))
        val r=repo.consultarResumen(id).getOrThrow()
        assertTrue(r.datosCompletos);assertTrue(r.puedeContinuar)
        assertEquals("30.00",r.reserva.total);assertEquals("00000001",r.pasajeros.single().dni)
    }
    @Test fun guardarPasajerosUsaPutSinPrecioUIDNiDatosNumericosParaDNI()=runTest {
        server.enqueue(MockResponse().setBody(resumenJson()))
        repo.guardarPasajeros(id,listOf(PasajeroReserva(1,"Pasajero de prueba","00000001","900000001"))).getOrThrow()
        val peticion=server.takeRequest();assertEquals("PUT",peticion.method)
        val cuerpo=JsonParser.parseString(peticion.body.readUtf8()).asJsonObject
        assertEquals(setOf("pasajeros"),cuerpo.keySet())
        assertEquals("00000001",cuerpo.getAsJsonArray("pasajeros")[0].asJsonObject.get("dni").asString)
    }
    @Test fun resumenNoAceptaPasajerosDeOtroAsiento()=runTest {
        val json=JsonParser.parseString(resumenJson()).asJsonObject
        json.getAsJsonObject("datos").getAsJsonArray("pasajeros")[0].asJsonObject.addProperty("asiento",2)
        server.enqueue(MockResponse().setBody(json.toString()))
        assertTrue(repo.consultarResumen(id).isFailure)
    }
    @Test fun resumenNoAceptaContinuarConDatosIncompletos()=runTest {
        val json=JsonParser.parseString(resumenJson()).asJsonObject
        json.getAsJsonObject("datos").addProperty("datos_completos",false)
        server.enqueue(MockResponse().setBody(json.toString()))
        assertTrue(repo.consultarResumen(id).isFailure)
    }
    private fun resumenJson():String {
        val datos=com.google.gson.JsonObject()
        datos.add("reserva",JsonParser.parseString(reserva).asJsonObject.getAsJsonObject("datos"))
        datos.add("viaje",JsonParser.parseString("""{"fecha_hora_salida":"2026-11-14T08:00:00-05:00","fecha_hora_llegada":"2026-11-14T11:00:00-05:00",
          "embarque":{"id":1,"nombre":"DEMO","ciudad":"Chota","direccion":"Referencia","latitud":null,"longitud":null},
          "llegada":{"id":2,"nombre":"DEMO","ciudad":"Cajamarca","direccion":"Referencia","latitud":null,"longitud":null}}"""))
        datos.add("pasajeros",JsonParser.parseString("""[{"asiento":1,"nombreCompleto":"Pasajero de prueba","dni":"00000001","celular":"900000001"}]"""))
        datos.addProperty("datos_completos",true);datos.addProperty("puede_continuar",true)
        return com.google.gson.JsonObject().apply{add("datos",datos)}.toString()
    }

    private class SesionFalsa:ProveedorTokenSesion {
        var uid:String?="usuario"
        var cambiarAlRenovar=false
        val forzados=mutableListOf<Boolean>()
        override fun usuarioActual()=uid
        override suspend fun obtener(forzar:Boolean):TokenSesion {
            forzados.add(forzar)
            if(uid==null)throw ErrorReserva(TipoErrorReserva.SESION,"Sin sesión")
            if(forzar&&cambiarAlRenovar)uid="otro"
            return TokenSesion(uid!!,if(forzar)"token-renovado" else "token-inicial")
        }
    }
    private val inventario="""{"datos":{"salida_id":1,"precio_unitario":"30.00","moneda":"PEN","hora_servidor":"2026-11-14T13:00:00Z",
      "asientos_totales":1,"asientos_libres":1,"asientos":[{"numero":1,"fila":1,"columna":1,"disponible":true}]}}"""
    private val reserva="""{"datos":{"id":"00000000-0000-4000-8000-000000000001","salida_id":1,"estado":"PENDIENTE","cantidad":1,
      "asientos":[1],"precio_unitario":"30.00","total":"30.00","moneda":"PEN","creado_en":"2026-11-14T13:00:00Z",
      "vence_en":"2026-11-14T13:10:00Z","hora_servidor":"2026-11-14T13:00:00Z"}}"""
}
