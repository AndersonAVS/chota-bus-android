package com.app.transporte_chota_bus.ui.viajes

import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.data.repository.ViajesRepository
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import java.io.IOException
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ResultadosViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val filtros = FiltrosViaje("Chota", "Cajamarca", LocalDate.now(ZoneId.of("America/Lima")).plusDays(1).toString(), 1)
    @Before fun preparar() { Dispatchers.setMain(dispatcher) }
    @After fun terminar() { Dispatchers.resetMain() }

    // Comprueba carga, consulta unica y reintento sin depender de PostgreSQL.
    @Test fun presentaCargaYListaVacia() = runTest {
        val repo = Falso()
        val vm = ResultadosViewModel(repo, filtros)
        assertTrue(vm.estado.value.cargando)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
        assertNull(vm.estado.value.error)
        assertTrue(vm.estado.value.salidas.isEmpty())
        assertEquals(filtros, repo.recibidos)
    }
    @Test fun conservaSalidasYHayMas() = runTest {
        val terminal = TerminalViaje(1, "DEMO", "Chota", "Ejemplo", null, null)
        val salida = SalidaViaje(1, "2026-12-20T13:00:00Z", "2026-12-20T16:00:00Z", "30.00", "PEN", 15, terminal, terminal)
        val repo = Falso().apply { resultado = Result.success(ResultadoViajes(listOf(salida), true)) }
        val vm = ResultadosViewModel(repo, filtros)
        advanceUntilIdle()
        assertEquals(listOf(salida), vm.estado.value.salidas)
        assertTrue(vm.estado.value.hayMas)
    }
    @Test fun falloPermiteReintentar() = runTest {
        val repo = Falso().apply { resultado = Result.failure(IOException("Sin conexión")) }
        val vm = ResultadosViewModel(repo, filtros)
        advanceUntilIdle()
        assertEquals("Sin conexión", vm.estado.value.error)
        repo.resultado = Result.success(ResultadoViajes(emptyList(), false))
        vm.buscar()
        advanceUntilIdle()
        assertNull(vm.estado.value.error)
        assertEquals(2, repo.llamadas)
    }
    @Test fun evitaSolicitudesSimultaneas() = runTest {
        val repo = Falso().apply { espera = CompletableDeferred() }
        val vm = ResultadosViewModel(repo, filtros)
        runCurrent()
        vm.buscar()
        assertEquals(1, repo.llamadas)
        repo.espera!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.estado.value.cargando)
    }
    @Test fun filtroInvalidoNoConsultaElRepositorio() = runTest {
        val repo = Falso()
        val vm = ResultadosViewModel(repo, filtros.copy(fecha = ""))
        advanceUntilIdle()
        assertEquals(0, repo.llamadas)
        assertNotNull(vm.estado.value.error)
    }

    private class Falso : ViajesRepository {
        var llamadas = 0
        var recibidos: FiltrosViaje? = null
        var espera: CompletableDeferred<Unit>? = null
        var resultado: Result<ResultadoViajes> = Result.success(ResultadoViajes(emptyList(), false))
        override suspend fun buscar(filtros: FiltrosViaje): Result<ResultadoViajes> {
            llamadas++
            recibidos = filtros
            espera?.await()
            return resultado
        }
    }
}
