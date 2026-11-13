package com.app.transporte_chota_bus.ui.compra

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class HistorialComprasViewModelTest{
    private val dispatcher=StandardTestDispatcher();private lateinit var repo:Falso
    @Before fun preparar(){Dispatchers.setMain(dispatcher);repo=Falso()}
    @After fun terminar(){Dispatchers.resetMain()}
    @Test fun cargaHistorialYPaginaSinDuplicarConsultas()=runTest{
        val vm=HistorialComprasViewModel(repo);advanceUntilIdle();assertEquals(1,vm.estado.value.registros.size)
        repo.espera=CompletableDeferred();vm.cargar(true);runCurrent();vm.cargar(true);runCurrent();assertEquals(2,repo.cursores.size)
        repo.espera!!.complete(Unit);advanceUntilIdle();assertEquals(2,vm.estado.value.registros.size);assertNull(vm.estado.value.siguiente)
    }
    @Test fun errorEnPaginaPosteriorConservaRegistrosYPermiteReintentar()=runTest{
        val vm=HistorialComprasViewModel(repo);advanceUntilIdle();repo.error=ErrorReserva(TipoErrorReserva.RED,"Sin red")
        vm.cargar(true);advanceUntilIdle();assertEquals(1,vm.estado.value.registros.size);assertEquals("a",vm.estado.value.siguiente)
        repo.error=null;vm.cargar(true);advanceUntilIdle();assertEquals(2,vm.estado.value.registros.size)
    }
    @Test fun actualizarReiniciaPaginacion()=runTest{
        val vm=HistorialComprasViewModel(repo);advanceUntilIdle();vm.cargar(true);advanceUntilIdle();vm.cargar();advanceUntilIdle()
        assertEquals(listOf(null,"a",null),repo.cursores);assertEquals(1,vm.estado.value.registros.size)
    }
    @Test fun cambioDeCuentaDescartaHistorialInclusoDuranteConsulta()=runTest{
        repo.espera=CompletableDeferred();val vm=HistorialComprasViewModel(repo);runCurrent();repo.uid="otra"
        repo.espera!!.complete(Unit);advanceUntilIdle();assertTrue(vm.estado.value.sesionRequerida);assertTrue(vm.estado.value.registros.isEmpty())
    }
    @Test fun paginaDuplicadaNoRepiteUnaCompra()=runTest{
        val vm=HistorialComprasViewModel(repo);advanceUntilIdle();repo.duplicar=true;vm.cargar(true);advanceUntilIdle()
        assertEquals(1,vm.estado.value.registros.size);assertNotNull(vm.estado.value.error)
    }
    @Test fun sesionRechazadaNoConservaInformacionPrivada()=runTest{
        val vm=HistorialComprasViewModel(repo);advanceUntilIdle();repo.error=ErrorReserva(TipoErrorReserva.SESION,"Sesion vencida")
        vm.cargar();advanceUntilIdle();assertTrue(vm.estado.value.sesionRequerida);assertTrue(vm.estado.value.registros.isEmpty())
    }
    private class Falso:ReservasRepository{
        var uid:String?="usuario";var error:Throwable?=null;var espera:CompletableDeferred<Unit>?=null;var duplicar=false
        val cursores=mutableListOf<String?>()
        override fun usuarioActual()=uid
        override suspend fun consultarHistorial(despues:String?):Result<PaginaHistorial>{
            cursores.add(despues);espera?.await();error?.let{return Result.failure(it)}
            val id=if(despues==null||duplicar)"a" else "b"
            val r=RegistroHistorial(id,3,EstadoReserva.CANCELADA,"2026-11-13T13:00:00Z","2026-11-14T13:00:00Z","Chota","Cajamarca",1,"30.00","0.00",false,null,listOf(1),0)
            return Result.success(PaginaHistorial(listOf(r),if(despues==null)"a" else null))
        }
        override suspend fun consultarResumen(id:String)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun guardarPasajeros(id:String,pasajeros:List<PasajeroReserva>)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun consultarAsientos(salidaId:Int)=Result.failure<InventarioAsientos>(UnsupportedOperationException())
        override suspend fun reservar(salidaId:Int,asientos:List<Int>,solicitudId:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun consultarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun cancelarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
    }
}
