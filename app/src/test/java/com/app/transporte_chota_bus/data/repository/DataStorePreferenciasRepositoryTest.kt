package com.app.transporte_chota_bus.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.app.transporte_chota_bus.data.model.PreferenciasBusqueda
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStorePreferenciasRepositoryTest {
    @get:Rule val temporal = TemporaryFolder()
    private val scopes = mutableListOf<CoroutineScope>()

    private fun abrir(file: File): Pair<DataStore<Preferences>, CoroutineScope> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes.add(scope)
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file }) to scope
    }

    @After fun cerrarArchivos() = runBlocking {
        scopes.forEach { it.coroutineContext[Job]?.cancelAndJoin() }
    }

    @Test fun archivoNuevoUtilizaValoresIniciales() = runTest {
        val (store, _) = abrir(File(temporal.root, "nuevo.preferences_pb"))
        assertEquals(PreferenciasBusqueda(), DataStorePreferenciasRepository(store).preferencias.first())
    }

    @Test fun guardaAmbosCamposYLosPublicaJuntos() = runTest {
        val (store, _) = abrir(File(temporal.root, "seleccion.preferences_pb"))
        val repo = DataStorePreferenciasRepository(store)
        repo.guardarRuta("Lajas", "Jaén").getOrThrow()
        assertEquals(PreferenciasBusqueda("Lajas", "Jaén"), repo.preferencias.first())
        val datos = store.data.first()
        assertEquals("Lajas", datos[stringPreferencesKey("ultimo_origen")])
        assertEquals("Jaén", datos[stringPreferencesKey("ultimo_destino")])
    }

    @Test fun conservaLosValoresAlCerrarYReabrirElArchivo() = runTest {
        val file = File(temporal.root, "persistencia.preferences_pb")
        val (primero, scope) = abrir(file)
        DataStorePreferenciasRepository(primero).guardarRuta("Jaén", "Lajas").getOrThrow()
        scope.coroutineContext[Job]!!.cancelAndJoin()
        val (segundo, _) = abrir(file)
        assertEquals(PreferenciasBusqueda("Jaén", "Lajas"),
            DataStorePreferenciasRepository(segundo).preferencias.first())
    }

    @Test fun seleccionInvalidaNoReemplazaLaAnterior() = runTest {
        val (store, _) = abrir(File(temporal.root, "validacion.preferences_pb"))
        val repo = DataStorePreferenciasRepository(store)
        repo.guardarRuta("Lajas", "Jaén").getOrThrow()
        assertTrue(repo.guardarRuta("Chota", "Chota").isFailure)
        assertTrue(repo.guardarRuta("Sede desconocida", "Jaén").isFailure)
        assertEquals(PreferenciasBusqueda("Lajas", "Jaén"), repo.preferencias.first())
    }

    @Test fun valoresDesconocidosDelArchivoRecuperanLosIniciales() = runTest {
        val (store, _) = abrir(File(temporal.root, "desconocidos.preferences_pb"))
        store.edit {
            it[stringPreferencesKey("ultimo_origen")] = "Sede retirada"
            it[stringPreferencesKey("ultimo_destino")] = "Jaén"
        }
        assertEquals(PreferenciasBusqueda(), DataStorePreferenciasRepository(store).preferencias.first())
    }

    @Test fun valoresIgualesDelArchivoNoLleganAlFormulario() = runTest {
        val (store, _) = abrir(File(temporal.root, "iguales.preferences_pb"))
        store.edit {
            it[stringPreferencesKey("ultimo_origen")] = "Chota"
            it[stringPreferencesKey("ultimo_destino")] = "Chota"
        }
        assertEquals(PreferenciasBusqueda(), DataStorePreferenciasRepository(store).preferencias.first())
    }

    @Test fun errorDeDiscoSeDevuelveComoFallo() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw IOException("Disco no disponible")
            }
        }
        val resultado = DataStorePreferenciasRepository(store).guardarRuta("Lajas", "Jaén")
        assertTrue(resultado.exceptionOrNull() is IOException)
    }

    @Test fun cancelarNoSeConvierteEnUnErrorDeDisco() = runTest {
        val store = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                throw CancellationException("Cancelado")
            }
        }
        try {
            DataStorePreferenciasRepository(store).guardarRuta("Lajas", "Jaén")
            fail("Debe respetar la cancelación")
        } catch (expected: CancellationException) {
            assertEquals("Cancelado", expected.message)
        }
    }
}
