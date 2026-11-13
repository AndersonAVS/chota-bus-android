package com.app.transporte_chota_bus.ui.compra

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class MisBoletosViewModelsTest{
    private val dispatcher=StandardTestDispatcher();private lateinit var repo:Falso
    @Before fun preparar(){Dispatchers.setMain(dispatcher);repo=Falso()}
    @After fun terminar(){Dispatchers.resetMain()}
    @Test fun paginaDosConservaLaPrimeraYNoDuplicaConsultas()=runTest{
        val vm=MisBoletosViewModel(repo);advanceUntilIdle();assertEquals(listOf("a"),vm.estado.value.boletos.map{it.id})
        repo.espera=CompletableDeferred();vm.cargar(true);runCurrent();vm.cargar(true);runCurrent();assertEquals(listOf(null,"a"),repo.cursores)
        repo.espera!!.complete(Unit);advanceUntilIdle();assertEquals(listOf("a","b"),vm.estado.value.boletos.map{it.id});assertNull(vm.estado.value.siguiente)
    }
    @Test fun errorEnPaginaPosteriorConservaListaYPermiteReintentar()=runTest{
        val vm=MisBoletosViewModel(repo);advanceUntilIdle();repo.error=ErrorReserva(TipoErrorReserva.RED,"Sin red")
        vm.cargar(true);advanceUntilIdle();assertEquals(1,vm.estado.value.boletos.size);assertEquals("a",vm.estado.value.siguiente)
        repo.error=null;vm.cargar(true);advanceUntilIdle();assertEquals(2,vm.estado.value.boletos.size)
    }
    @Test fun actualizarReiniciaElCursor()=runTest{
        val vm=MisBoletosViewModel(repo);advanceUntilIdle();vm.cargar(true);advanceUntilIdle();vm.cargar();advanceUntilIdle()
        assertEquals(listOf(null,"a",null),repo.cursores);assertEquals(1,vm.estado.value.boletos.size)
    }
    @Test fun cuentaCambiadaDescartaListaYRespuestaPendiente()=runTest{
        repo.espera=CompletableDeferred();val vm=MisBoletosViewModel(repo);runCurrent();repo.uid="otra"
        repo.espera!!.complete(Unit);advanceUntilIdle();assertTrue(vm.estado.value.sesionRequerida);assertTrue(vm.estado.value.boletos.isEmpty())
    }
    @Test fun paginaDuplicadaNoAgregaElMismoBoleto()=runTest{
        val vm=MisBoletosViewModel(repo);advanceUntilIdle();repo.duplicar=true;vm.cargar(true);advanceUntilIdle()
        assertEquals(1,vm.estado.value.boletos.size);assertNotNull(vm.estado.value.error)
    }
    @Test fun detalleReconsultaPorIdYNoMuestraOtroBoleto()=runTest{
        val vm=DetalleBoletoViewModel(repo,"b");advanceUntilIdle();assertNull(vm.estado.value.boleto);assertNotNull(vm.estado.value.error)
    }
    @Test fun detalleReintentaDespuesDeErrorSinExponerDatosPrevios()=runTest{
        repo.error=ErrorReserva(TipoErrorReserva.RED,"Sin red");val vm=DetalleBoletoViewModel(repo,"a");advanceUntilIdle()
        assertNull(vm.estado.value.boleto);repo.error=null;vm.cargar();advanceUntilIdle();assertEquals("a",vm.estado.value.boleto!!.id)
        repo.uid="otra";vm.verificarSesion();assertNull(vm.estado.value.boleto);assertTrue(vm.estado.value.sesionRequerida)
    }
    @Test fun sesionRechazadaBorraListaYDetalle()=runTest{
        repo.error=ErrorReserva(TipoErrorReserva.SESION,"Sesion vencida")
        val lista=MisBoletosViewModel(repo);val detalle=DetalleBoletoViewModel(repo,"a");advanceUntilIdle()
        assertTrue(lista.estado.value.sesionRequerida);assertTrue(lista.estado.value.boletos.isEmpty())
        assertTrue(detalle.estado.value.sesionRequerida);assertNull(detalle.estado.value.boleto)
    }
    private class Falso:ReservasRepository{
        var uid:String?="usuario";var error:Throwable?=null;var espera:CompletableDeferred<Unit>?=null;var duplicar=false
        val cursores=mutableListOf<String?>()
        fun boleto(id:String):BoletoPrueba{val t=TerminalViaje(1,"DEMO","Chota","Referencia",null,null)
            return BoletoPrueba(id,"CBP-CODIGO", "reserva",1,"30.00","2026-11-13T13:00:00Z",false,"PASAJERO PRUEBA","00000001",3,"2026-11-14T13:00:00Z","2026-11-14T16:00:00Z",t,t)}
        override fun usuarioActual()=uid
        override suspend fun consultarMisBoletos(despues:String?):Result<PaginaBoletos>{
            cursores.add(despues);espera?.await();error?.let{return Result.failure(it)}
            return Result.success(PaginaBoletos(listOf(boleto(if(despues==null||duplicar)"a" else "b")),if(despues==null)"a" else null))
        }
        override suspend fun consultarBoleto(id:String):Result<BoletoPrueba>{espera?.await();error?.let{return Result.failure(it)};return Result.success(boleto("a"))}
        override suspend fun consultarResumen(id:String)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun guardarPasajeros(id:String,pasajeros:List<PasajeroReserva>)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun consultarAsientos(salidaId:Int)=Result.failure<InventarioAsientos>(UnsupportedOperationException())
        override suspend fun reservar(salidaId:Int,asientos:List<Int>,solicitudId:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun consultarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun cancelarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
    }
}
