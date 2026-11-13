package com.app.transporte_chota_bus.ui.compra

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BoletosCompraViewModelTest{
    private val dispatcher=StandardTestDispatcher()
    private lateinit var repo:Falso
    @Before fun preparar(){Dispatchers.setMain(dispatcher);repo=Falso()}
    @After fun terminar(){Dispatchers.resetMain()}
    @Test fun cargaLosBoletosEmitidosSinPagarOtraVez()=runTest{
        val vm=BoletosCompraViewModel(repo,"reserva");advanceUntilIdle()
        assertEquals(1,vm.estado.value.boletos.size);assertFalse(vm.estado.value.cargando);assertEquals(1,repo.consultas)
    }
    @Test fun errorDeRedPermiteReintentarSinInventarBoletos()=runTest{
        repo.error=ErrorReserva(TipoErrorReserva.RED,"Sin conexion")
        val vm=BoletosCompraViewModel(repo,"reserva");advanceUntilIdle()
        assertTrue(vm.estado.value.boletos.isEmpty());assertNotNull(vm.estado.value.error)
        repo.error=null;vm.cargar();advanceUntilIdle();assertEquals(1,vm.estado.value.boletos.size)
    }
    @Test fun noDuplicaConsultasMientrasCarga()=runTest{
        repo.espera=CompletableDeferred();val vm=BoletosCompraViewModel(repo,"reserva");runCurrent()
        vm.cargar();runCurrent();assertEquals(1,repo.consultas)
        repo.espera!!.complete(Unit);advanceUntilIdle()
    }
    @Test fun cambioDeCuentaBorraLosBoletosPersonales()=runTest{
        val vm=BoletosCompraViewModel(repo,"reserva");advanceUntilIdle();repo.uid="otra"
        assertFalse(vm.verificarSesion());assertTrue(vm.estado.value.sesionRequerida);assertTrue(vm.estado.value.boletos.isEmpty())
    }
    @Test fun cambioDeCuentaDuranteConsultaDescartaRespuesta()=runTest{
        repo.espera=CompletableDeferred();val vm=BoletosCompraViewModel(repo,"reserva");runCurrent();repo.uid="otra"
        repo.espera!!.complete(Unit);advanceUntilIdle();assertTrue(vm.estado.value.boletos.isEmpty());assertTrue(vm.estado.value.sesionRequerida)
    }
    @Test fun boletosDeOtraReservaNoSeMuestran()=runTest{
        repo.otraReserva=true;val vm=BoletosCompraViewModel(repo,"reserva");advanceUntilIdle()
        assertTrue(vm.estado.value.boletos.isEmpty());assertNotNull(vm.estado.value.error)
    }
    @Test fun sesionRechazadaOfreceIniciarSesion()=runTest{
        repo.error=ErrorReserva(TipoErrorReserva.SESION,"Inicia sesion")
        val vm=BoletosCompraViewModel(repo,"reserva");advanceUntilIdle();assertTrue(vm.estado.value.sesionRequerida)
    }
    private class Falso:ReservasRepository{
        var uid:String?="usuario";var consultas=0;var error:Throwable?=null;var espera:CompletableDeferred<Unit>?=null;var otraReserva=false
        override fun usuarioActual()=uid
        override suspend fun consultarBoletos(id:String):Result<List<BoletoPrueba>>{
            consultas++;espera?.await();error?.let{return Result.failure(it)}
            val t=TerminalViaje(1,"DEMO","Chota","Referencia",null,null)
            return Result.success(listOf(BoletoPrueba("boleto","CBP-CODIGO",if(otraReserva)"otra" else id,1,"30.00",
                "2026-11-13T13:00:00Z",false,"PASAJERO DE PRUEBA","00000001",3,"2026-11-14T13:00:00Z","2026-11-14T16:00:00Z",t,t)))
        }
        override suspend fun consultarResumen(id:String)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun guardarPasajeros(id:String,pasajeros:List<PasajeroReserva>)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun consultarAsientos(salidaId:Int)=Result.failure<InventarioAsientos>(UnsupportedOperationException())
        override suspend fun reservar(salidaId:Int,asientos:List<Int>,solicitudId:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun consultarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun cancelarReserva(id:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
    }
}
