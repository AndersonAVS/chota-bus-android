package com.app.transporte_chota_bus.ui.inicio

import com.app.transporte_chota_bus.data.model.PreferenciasBusqueda
import com.app.transporte_chota_bus.data.repository.PreferenciasRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: RepositorioDePrueba

    @Before fun preparar() {
        Dispatchers.setMain(dispatcher)
        repository = RepositorioDePrueba()
    }

    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun recuperaLaSeleccionGuardada() = runTest {
        repository.datos.value = PreferenciasBusqueda("Lajas", "Jaén")
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        assertEquals(repository.datos.value, vm.estado.value.seleccion)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun cambiarOrigenConservaElDestino() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarOrigen("Lajas")
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda("Lajas", "Cajamarca"), vm.estado.value.seleccion)
        assertEquals(1, repository.llamadas)
    }

    @Test fun cambiarDestinoConservaElOrigen() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarDestino("Jaén")
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda("Chota", "Jaén"), vm.estado.value.seleccion)
    }

    @Test fun intercambiaLosDosValoresEnUnaSolicitud() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.intercambiar()
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda("Cajamarca", "Chota"), vm.estado.value.seleccion)
        assertEquals(1, repository.llamadas)
    }

    @Test fun sedesIgualesODesconocidasNoSeGuardan() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarOrigen("Cajamarca")
        vm.seleccionarDestino("Sede inexistente")
        advanceUntilIdle()
        assertEquals(0, repository.llamadas)
        assertEquals(PreferenciasBusqueda(), vm.estado.value.seleccion)
        assertNotNull(vm.estado.value.mensaje)
    }

    @Test fun falloDeEscrituraConservaLaSeleccionPrevia() = runTest {
        repository.fallo = true
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarOrigen("Lajas")
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda(), vm.estado.value.seleccion)
        assertFalse(vm.estado.value.guardando)
        assertTrue(vm.estado.value.mensaje!!.contains("No pudimos guardar"))
    }

    @Test fun evitaEnviosRepetidosMientrasGuarda() = runTest {
        val espera = CompletableDeferred<Unit>()
        repository.espera = espera
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarOrigen("Lajas")
        vm.intercambiar()
        runCurrent()
        assertTrue(vm.estado.value.guardando)
        assertEquals(1, repository.llamadas)
        espera.complete(Unit)
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda("Lajas", "Cajamarca"), vm.estado.value.seleccion)
    }

    @Test fun lecturaFallidaPermiteElegirNuevamente() = runTest {
        repository.falloLectura = true
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
        assertTrue(vm.estado.value.mensaje!!.contains("No pudimos recuperar"))
        vm.seleccionarOrigen("Lajas")
        advanceUntilIdle()
        assertEquals(PreferenciasBusqueda("Lajas", "Cajamarca"), vm.estado.value.seleccion)
    }

    @Test fun esperaLaLecturaAntesDePermitirCambios() = runTest {
        val vm = BusquedaViewModel(repository)
        vm.seleccionarOrigen("Lajas")
        advanceUntilIdle()
        assertEquals(0, repository.llamadas)
    }

    @Test fun elegirElValorActualNoEscribeEnDisco() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        vm.seleccionarOrigen("Chota")
        advanceUntilIdle()
        assertEquals(0, repository.llamadas)
    }

    @Test fun observaCambiosDelRepositorio() = runTest {
        val vm = BusquedaViewModel(repository)
        advanceUntilIdle()
        repository.datos.value = PreferenciasBusqueda("Jaén", "Lajas")
        advanceUntilIdle()
        assertEquals(repository.datos.value, vm.estado.value.seleccion)
    }

    private class RepositorioDePrueba : PreferenciasRepository {
        val datos = MutableStateFlow(PreferenciasBusqueda())
        var fallo = false
        var falloLectura = false
        var llamadas = 0
        var espera: CompletableDeferred<Unit>? = null

        override val preferencias: Flow<PreferenciasBusqueda>
            get() = if (falloLectura) flow { throw IOException("Sin acceso al archivo") } else datos

        override suspend fun guardarRuta(origen: String, destino: String): Result<Unit> {
            llamadas++
            espera?.await()
            if (fallo) return Result.failure(IOException("Sin espacio"))
            datos.value = PreferenciasBusqueda(origen, destino)
            return Result.success(Unit)
        }
    }
}
