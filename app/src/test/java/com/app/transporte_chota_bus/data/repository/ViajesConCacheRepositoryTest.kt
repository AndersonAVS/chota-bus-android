package com.app.transporte_chota_bus.data.repository
import com.app.transporte_chota_bus.data.local.*
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import java.io.IOException
import java.time.LocalDate

class ViajesConCacheRepositoryTest{
    private val f=FiltrosViaje("Chota","Cajamarca",LocalDate.now().plusDays(1).toString(),1)
    private val t=TerminalViaje(1,"DEMO","Chota","Referencia",null,null)
    private val datos=ResultadoViajes(listOf(SalidaViaje(3,"2026-12-20T13:00:00Z","2026-12-20T16:00:00Z","30.00","PEN",15,t,t,10)),false)
    private class Cache:CacheViajes{
        var dato:ConsultaGuardada?=null;var guardados=0;var falla=false
        override suspend fun guardar(filtros:FiltrosViaje,resultado:ResultadoViajes){if(falla)throw IOException();guardados++;dato=ConsultaGuardada(resultado,"2026-11-13T13:00:00Z")}
        override suspend fun leer(filtros:FiltrosViaje)=dato
        override suspend fun borrar(filtros:FiltrosViaje){dato=null}
    }
    @Test fun onlineGuardaDatosPeroNoLosMarcaComoCache()=runTest{
        val c=Cache();val remoto=object:ViajesRepository{override suspend fun buscar(filtros:FiltrosViaje)=Result.success(datos)}
        assertFalse(ViajesConCacheRepository(remoto,c).buscar(f).getOrThrow().desdeCache);assertEquals(1,c.guardados)
    }
    @Test fun sinRedDevuelveConsultaSoloLecturaSinInventario()=runTest{
        val c=Cache();c.guardar(f,datos)
        val remoto=object:ViajesRepository{override suspend fun buscar(filtros:FiltrosViaje)=Result.failure<ResultadoViajes>(IOException())}
        val r=ViajesConCacheRepository(remoto,c).buscar(f).getOrThrow();assertTrue(r.desdeCache);assertNotNull(r.guardadoEn);assertNull(r.salidas.single().asientosLibres)
        c.borrar(f);assertTrue(ViajesConCacheRepository(remoto,c).buscar(f).isFailure)
    }
    @Test fun falloAlGuardarNoRompeConsultaOnline()=runTest{
        val c=Cache();c.falla=true
        val remoto=object:ViajesRepository{override suspend fun buscar(filtros:FiltrosViaje)=Result.success(datos)}
        assertTrue(ViajesConCacheRepository(remoto,c).buscar(f).isSuccess)
    }
    @Test(expected=CancellationException::class) fun cancelacionNoSeDisfrazaDeConsultaGuardada()=runTest{
        val c=Cache();c.guardar(f,datos)
        val remoto=object:ViajesRepository{override suspend fun buscar(filtros:FiltrosViaje):Result<ResultadoViajes>{throw CancellationException()}}
        ViajesConCacheRepository(remoto,c).buscar(f)
    }
}
