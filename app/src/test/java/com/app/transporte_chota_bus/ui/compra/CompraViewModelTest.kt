package com.app.transporte_chota_bus.ui.compra

import androidx.lifecycle.SavedStateHandle
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ReservasRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CompraViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repo: Falso
    private var tiempo = 0L
    @Before fun preparar() { Dispatchers.setMain(dispatcher); repo = Falso(); tiempo = 0 }
    @After fun terminar() { Dispatchers.resetMain() }
    private fun vm(cantidad: Int = 1, guardado: SavedStateHandle = SavedStateHandle()) = CompraViewModel(repo, guardado, 1, cantidad) { tiempo }

    // Comprueba seleccion, concurrencia local y recuperacion del resultado incierto.
    @Test fun cargaAsientosYEvitaElegirUnOcupado() = runTest {
        val vm = vm(); advanceUntilIdle()
        assertEquals(3, vm.estado.value.inventario!!.asientos.size)
        vm.seleccionar(3)
        assertTrue(vm.estado.value.seleccion.isEmpty())
        vm.seleccionar(1)
        assertEquals(setOf(1), vm.estado.value.seleccion)
        vm.seleccionar(2)
        assertEquals(setOf(1), vm.estado.value.seleccion)
    }
    @Test fun exigeUnAsientoPorPasajero() = runTest {
        val vm = vm(2); advanceUntilIdle(); vm.seleccionar(1); vm.reservar(); advanceUntilIdle()
        assertEquals(0, repo.creaciones)
        vm.seleccionar(2); vm.reservar(); advanceUntilIdle()
        assertEquals(1, repo.creaciones)
        assertEquals(listOf(1,2), repo.ultimos)
    }
    @Test fun evitaDoblePulsacionMientrasReserva() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.seleccionar(1)
        repo.espera = CompletableDeferred()
        vm.reservar(); runCurrent(); vm.reservar(); runCurrent()
        assertEquals(1, repo.creaciones)
        repo.espera!!.complete(Unit); advanceUntilIdle()
        assertEquals(EstadoReserva.PENDIENTE, vm.estado.value.reserva!!.estado)
    }
    @Test fun reintentoConservaSolicitudYNoPermiteCambiarSeleccion() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.seleccionar(1)
        repo.errorCreacion = ErrorReserva(TipoErrorReserva.RED,"Sin respuesta")
        vm.reservar(); advanceUntilIdle()
        assertTrue(vm.estado.value.pendienteConfirmacion)
        vm.seleccionar(2); assertEquals(setOf(1), vm.estado.value.seleccion)
        repo.errorCreacion = null; vm.reservar(); advanceUntilIdle()
        assertEquals(repo.solicitudes[0], repo.solicitudes[1])
    }
    @Test fun recreacionConservaIntentoInciertoDelMismoUsuario() = runTest {
        val guardado = SavedStateHandle()
        val uno = vm(guardado = guardado); advanceUntilIdle(); uno.seleccionar(1)
        repo.errorCreacion = ErrorReserva(TipoErrorReserva.RED,"Sin respuesta")
        uno.reservar(); advanceUntilIdle()
        val dos = vm(guardado = guardado); advanceUntilIdle()
        assertTrue(dos.estado.value.pendienteConfirmacion)
        repo.errorCreacion = null; dos.reservar(); advanceUntilIdle()
        assertEquals(repo.solicitudes[0], repo.solicitudes[1])
    }
    @Test fun conflictoLiberaElIntentoParaElegirOtrosAsientos() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.seleccionar(1)
        repo.errorCreacion = ErrorReserva(TipoErrorReserva.CONFLICTO,"Ocupado")
        vm.reservar(); advanceUntilIdle()
        assertFalse(vm.estado.value.pendienteConfirmacion)
        assertTrue(vm.estado.value.seleccion.isEmpty())
        repo.errorCreacion = null; vm.seleccionar(2); vm.reservar(); advanceUntilIdle()
        assertNotEquals(repo.solicitudes[0],repo.solicitudes[1])
    }
    @Test fun cancelaYPermiteSalirSoloDespuesDeRespuesta() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.seleccionar(1); vm.reservar(); advanceUntilIdle()
        vm.cancelar(true); advanceUntilIdle()
        assertEquals(EstadoReserva.CANCELADA,vm.estado.value.reserva!!.estado)
        assertTrue(vm.estado.value.salir)
        assertFalse(vm.estado.value.bloqueada)
    }
    @Test fun vencimientoUsaRelojMonotonoYLiberaSeleccion() = runTest {
        val vm = vm(); advanceUntilIdle(); vm.seleccionar(1); vm.reservar(); advanceUntilIdle()
        assertEquals(600L, vm.estado.value.segundos)
        tiempo=600_001; vm.actualizarTiempo(); advanceUntilIdle()
        assertEquals(EstadoReserva.EXPIRADA, vm.estado.value.reserva!!.estado)
        assertTrue(vm.estado.value.seleccion.isEmpty())
    }
    @Test fun restauraReservaMedianteConsultaAlServidor() = runTest {
        val guardado=SavedStateHandle(mapOf("propietario_uid" to "usuario", "reserva_id" to "id-reserva"))
        val vm=vm(guardado=guardado); advanceUntilIdle()
        assertEquals(1,repo.consultasReserva)
        assertEquals(EstadoReserva.PENDIENTE, vm.estado.value.reserva!!.estado)
    }
    @Test fun falloAlRestaurarNoPermiteCrearOtraReserva() = runTest {
        repo.errorConsulta=ErrorReserva(TipoErrorReserva.RED,"Sin conexión")
        val vm=vm(guardado=SavedStateHandle(mapOf("propietario_uid" to "usuario","reserva_id" to "id-reserva")))
        advanceUntilIdle(); vm.seleccionar(1); vm.reservar(); advanceUntilIdle()
        assertTrue(vm.estado.value.reservaDesconocida)
        assertEquals(0,repo.creaciones)
    }
    @Test fun cambioDeCuentaNoRecuperaDatosDelUsuarioAnterior() = runTest {
        val vm=vm(guardado=SavedStateHandle(mapOf("propietario_uid" to "otro","reserva_id" to "id-otro")))
        advanceUntilIdle()
        assertEquals(0,repo.consultasReserva)
        assertNull(vm.estado.value.reserva)
    }
    @Test fun cierreDeSesionBloqueaLaCompra() = runTest {
        val vm=vm(); advanceUntilIdle(); repo.uid=null; vm.seleccionar(1)
        assertTrue(vm.estado.value.sesionRequerida)
        assertNull(vm.estado.value.inventario)
    }

    private class Falso : ReservasRepository {
        override suspend fun consultarResumen(id:String)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        override suspend fun guardarPasajeros(id:String,pasajeros:List<PasajeroReserva>)=Result.failure<ResumenCompra>(UnsupportedOperationException())
        var uid: String?="usuario"
        var creaciones=0
        var consultasReserva=0
        var ultimos=listOf(1)
        val solicitudes=mutableListOf<String>()
        var espera: CompletableDeferred<Unit>?=null
        var errorCreacion: Throwable?=null
        var errorConsulta: Throwable?=null
        fun reserva(estado: EstadoReserva=EstadoReserva.PENDIENTE)=ReservaAsientos("id-reserva",1,estado,ultimos,"30.00",
            (30*ultimos.size).toString()+".00","2026-11-14T13:10:00Z","2026-11-14T13:00:00Z")
        override fun usuarioActual()=uid
        override suspend fun consultarAsientos(salidaId:Int)=Result.success(InventarioAsientos(1,"30.00",listOf(
            AsientoDisponible(1,1,1,true),AsientoDisponible(2,1,2,true),AsientoDisponible(3,1,3,false))))
        override suspend fun reservar(salidaId:Int,asientos:List<Int>,solicitudId:String):Result<ReservaAsientos> {
            creaciones++;ultimos=asientos;solicitudes.add(solicitudId);espera?.await()
            return errorCreacion?.let { Result.failure(it) } ?: Result.success(reserva())
        }
        override suspend fun consultarReserva(id:String):Result<ReservaAsientos> {
            consultasReserva++
            return errorConsulta?.let { Result.failure(it) } ?: Result.success(reserva())
        }
        override suspend fun cancelarReserva(id:String)=Result.success(reserva(EstadoReserva.CANCELADA))
    }
}
