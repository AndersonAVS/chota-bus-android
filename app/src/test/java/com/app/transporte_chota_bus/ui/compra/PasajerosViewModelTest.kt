package com.app.transporte_chota_bus.ui.compra
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class PasajerosViewModelTest{
    private val dispatcher=StandardTestDispatcher()
    private lateinit var repo:Falso
    private var tiempo=0L
    @Before fun preparar(){Dispatchers.setMain(dispatcher);repo=Falso();tiempo=0}
    @After fun terminar(){Dispatchers.resetMain()}
    private fun vm()=PasajerosViewModel(repo,"reserva"){tiempo}
    private fun completar(vm:PasajerosViewModel){for(n in 1..2){
        vm.editar(n,CampoPerfil.NOMBRE,"Pasajero de prueba $n")
        vm.editar(n,CampoPerfil.DNI,"0000000$n")
        vm.editar(n,CampoPerfil.CELULAR,"900000001")
    }}
    @Test fun generaUnFormularioPorAsiento()=runTest{
        val vm=vm();advanceUntilIdle();assertEquals(listOf(1,2),vm.estado.value.borradores.map{it.asiento})
    }
    @Test fun formularioInvalidoNoLlamaAlServidor()=runTest{
        val vm=vm();advanceUntilIdle();vm.guardar();advanceUntilIdle()
        assertEquals(0,repo.guardados);assertEquals(2,vm.estado.value.errores.size)
    }
    @Test fun guardadoCompletoAbreResumenConTotalDelServidor()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);vm.guardar();advanceUntilIdle()
        assertTrue(vm.estado.value.mostrandoResumen);assertFalse(vm.estado.value.modificado)
        assertEquals("60.00",vm.estado.value.resumen!!.reserva.total)
    }
    @Test fun recargaConservaBorradorModificado()=runTest{
        val vm=vm();advanceUntilIdle();vm.editar(1,CampoPerfil.NOMBRE,"No perder este nombre")
        vm.cargar();advanceUntilIdle();assertEquals("No perder este nombre",vm.estado.value.borradores[0].nombreCompleto)
    }
    @Test fun falloDeConexionConservaDatosParaReintentar()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm)
        repo.error=ErrorReserva(TipoErrorReserva.RED,"Sin respuesta");vm.guardar();advanceUntilIdle()
        assertTrue(vm.estado.value.modificado);assertFalse(vm.estado.value.mostrandoResumen)
        repo.error=null;vm.guardar();advanceUntilIdle();assertTrue(vm.estado.value.mostrandoResumen)
    }
    @Test fun evitaGuardadosSimultaneos()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);repo.espera=CompletableDeferred()
        vm.guardar();runCurrent();vm.guardar();runCurrent();assertEquals(1,repo.guardados)
        repo.espera!!.complete(Unit);advanceUntilIdle()
    }
    @Test fun vencimientoBloqueaGuardado()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);tiempo=600001;vm.actualizarTiempo();vm.guardar();advanceUntilIdle()
        assertFalse(vm.estado.value.vigente);assertEquals(0,repo.guardados)
    }
    @Test fun DNIDuplicadoNoSeEnvia()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);vm.editar(2,CampoPerfil.DNI,"00000001")
        vm.guardar();advanceUntilIdle();assertEquals(0,repo.guardados);assertEquals(2,vm.estado.value.errores.size)
    }
    @Test fun cambioDeCuentaBorraDatosDeLaVista()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);repo.uid="otro";vm.actualizarTiempo()
        assertTrue(vm.estado.value.sesionRequerida);assertTrue(vm.estado.value.borradores.isEmpty());assertNull(vm.estado.value.resumen)
    }
    @Test fun pagoConfirmaResumenYActualizaSaldoUnaVez()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);vm.guardar();advanceUntilIdle()
        repo.esperaPago=CompletableDeferred();vm.pagarPrueba();runCurrent();vm.pagarPrueba();runCurrent()
        assertEquals(1,repo.pagos);assertTrue(vm.estado.value.pagando)
        repo.esperaPago!!.complete(Unit);advanceUntilIdle()
        assertEquals(EstadoReserva.CONFIRMADA,vm.estado.value.resumen!!.reserva.estado)
        assertEquals("240.00",vm.estado.value.saldoPrueba);assertFalse(vm.estado.value.vigente)
    }
    @Test fun formularioIncompletoNoPermitePago()=runTest{
        val vm=vm();advanceUntilIdle();vm.pagarPrueba();advanceUntilIdle();assertEquals(0,repo.pagos)
    }
    @Test fun pagoConRespuestaPerdidaRecuperaConfirmacionDelServidor()=runTest{
        val vm=vm();advanceUntilIdle();completar(vm);vm.guardar();advanceUntilIdle()
        repo.respuestaPerdida=true;vm.pagarPrueba();advanceUntilIdle()
        assertEquals(EstadoReserva.CONFIRMADA,vm.estado.value.resumen!!.reserva.estado)
        vm.pagarPrueba();advanceUntilIdle();assertEquals(1,repo.pagos)
    }
    private class Falso:ReservasRepository{
        var pagos=0;var esperaPago:CompletableDeferred<Unit>?=null;var respuestaPerdida=false
        override suspend fun consultarSaldoPrueba()=Result.success(SaldoPrueba(if(pagos>0)"240.00" else "300.00"))
        override suspend fun pagarConSaldoPrueba(id:String):Result<PagoPrueba>{
            pagos++;esperaPago?.await();resumen=resumen.copy(reserva=resumen.reserva.copy(estado=EstadoReserva.CONFIRMADA),puedeContinuar=false)
            return if(respuestaPerdida)Result.failure(ErrorReserva(TipoErrorReserva.RED,"Sin respuesta"))
            else Result.success(PagoPrueba("pago",id,"60.00","240.00"))
        }
        var uid:String?="usuario";var guardados=0;var error:Throwable?=null;var espera:CompletableDeferred<Unit>?=null
        val terminal=TerminalViaje(1,"DEMO","Ciudad","Referencia",null,null)
        val reserva=ReservaAsientos("reserva",1,EstadoReserva.PENDIENTE,listOf(1,2),"30.00","60.00","2026-11-14T13:10:00Z","2026-11-14T13:00:00Z")
        var resumen=ResumenCompra(reserva,"2026-11-14T13:00:00Z","2026-11-14T16:00:00Z",terminal,terminal,emptyList(),false,false)
        override fun usuarioActual()=uid
        override suspend fun consultarResumen(id:String)=Result.success(resumen)
        override suspend fun guardarPasajeros(id:String,pasajeros:List<PasajeroReserva>):Result<ResumenCompra>{
            guardados++;espera?.await();error?.let{return Result.failure(it)}
            resumen=resumen.copy(pasajeros=pasajeros,datosCompletos=true,puedeContinuar=true)
            return Result.success(resumen)
        }
        override suspend fun consultarAsientos(salidaId:Int)=Result.failure<InventarioAsientos>(UnsupportedOperationException())
        override suspend fun reservar(salidaId:Int,asientos:List<Int>,solicitudId:String)=Result.failure<ReservaAsientos>(UnsupportedOperationException())
        override suspend fun consultarReserva(id:String)=Result.success(reserva)
        override suspend fun cancelarReserva(id:String)=Result.success(reserva.copy(estado=EstadoReserva.CANCELADA))
    }
}
