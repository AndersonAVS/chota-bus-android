package com.app.transporte_chota_bus.ui.perfil

import com.app.transporte_chota_bus.data.model.Usuario
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class PerfilViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val usuario = Usuario("uid-prueba", "María Torres", "01234567", "987654321", "maria@example.com")
    private lateinit var repository: RepositorioDePrueba

    @Before fun preparar() {
        Dispatchers.setMain(dispatcher)
        repository = RepositorioDePrueba(usuario)
    }

    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun cargaLosDatosDelUsuarioAutenticado() = runTest {
        val vm = PerfilViewModel(repository)
        assertTrue(vm.estado.value.cargando)
        advanceUntilIdle()
        assertEquals(usuario, vm.estado.value.usuario)
        assertEquals(usuario.nombreCompleto, vm.estado.value.nombre)
        assertEquals(usuario.dni, vm.estado.value.dni)
        assertEquals(usuario.celular, vm.estado.value.celular)
        assertFalse(vm.estado.value.cargando)
    }

    @Test fun solicitaSesionCuandoNoExisteUsuario() = runTest {
        repository.resultadoActual = Result.success(null)
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        assertTrue(vm.estado.value.sesionRequerida)
        assertNull(vm.estado.value.usuario)
    }

    @Test fun muestraErrorDeCargaYPermiteReintentar() = runTest {
        repository.resultadoActual = Result.failure(Exception("Sin conexión"))
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        assertEquals("Sin conexión", vm.estado.value.mensaje)
        repository.resultadoActual = Result.success(usuario)
        vm.cargarPerfil()
        advanceUntilIdle()
        assertEquals(usuario, vm.estado.value.usuario)
        assertNull(vm.estado.value.mensaje)
    }

    @Test fun datosInvalidosNoLleganAlRepositorio() = runTest {
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, " ")
        vm.actualizarCampo(CampoPerfil.DNI, "12")
        vm.actualizarCampo(CampoPerfil.CELULAR, "abc")
        vm.guardarCambios()
        advanceUntilIdle()
        assertEquals(0, repository.llamadasGuardar)
        assertEquals(3, vm.estado.value.errores.size)
    }

    @Test fun guardaDatosNormalizadosYConservaLaIdentidad() = runTest {
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, " María Muñoz ")
        vm.actualizarCampo(CampoPerfil.DNI, " 12345678 ")
        vm.actualizarCampo(CampoPerfil.CELULAR, " 912345678 ")
        vm.guardarCambios()
        advanceUntilIdle()
        assertEquals(listOf("María Muñoz", "12345678", "912345678"), repository.ultimosDatos)
        assertEquals(usuario.id, vm.estado.value.usuario?.id)
        assertEquals(usuario.correo, vm.estado.value.usuario?.correo)
        assertTrue(vm.estado.value.guardado)
        assertEquals("María Muñoz", vm.estado.value.nombre)
    }

    @Test fun unFalloConservaElBorradorYNoConfirmaGuardado() = runTest {
        repository.falloGuardar = Exception("Permiso rechazado")
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, "Otro nombre")
        vm.guardarCambios()
        advanceUntilIdle()
        assertEquals("Otro nombre", vm.estado.value.nombre)
        assertEquals(usuario, vm.estado.value.usuario)
        assertFalse(vm.estado.value.guardado)
        assertFalse(vm.estado.value.guardando)
        assertEquals("Permiso rechazado", vm.estado.value.mensaje)
    }

    @Test fun noEnviaUnaActualizacionSiNoExistenCambios() = runTest {
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.guardarCambios()
        advanceUntilIdle()
        assertEquals(0, repository.llamadasGuardar)
        assertFalse(vm.estado.value.guardado)
        assertEquals("No hay cambios por guardar.", vm.estado.value.mensaje)
    }

    @Test fun evitaEnviosRepetidosYConfirmaDespuesDelServidor() = runTest {
        val espera = CompletableDeferred<Unit>()
        repository.esperaGuardar = espera
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, "Nuevo nombre")
        vm.guardarCambios()
        vm.guardarCambios()
        runCurrent()
        assertEquals(1, repository.llamadasGuardar)
        assertTrue(vm.estado.value.guardando)
        assertFalse(vm.estado.value.guardado)
        vm.actualizarCampo(CampoPerfil.NOMBRE, "Cambio durante envío")
        assertEquals("Nuevo nombre", vm.estado.value.nombre)
        espera.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.estado.value.guardado)
    }

    @Test fun corregirUnCampoLimpiaSoloSuError() = runTest {
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, "")
        vm.actualizarCampo(CampoPerfil.DNI, "")
        vm.guardarCambios()
        vm.actualizarCampo(CampoPerfil.NOMBRE, "María")
        assertFalse(vm.estado.value.errores.containsKey(CampoPerfil.NOMBRE))
        assertTrue(vm.estado.value.errores.containsKey(CampoPerfil.DNI))
    }

    @Test fun unaNuevaEdicionDescartaLaConfirmacionAnterior() = runTest {
        val vm = PerfilViewModel(repository)
        advanceUntilIdle()
        vm.actualizarCampo(CampoPerfil.NOMBRE, "Nuevo nombre")
        vm.guardarCambios()
        advanceUntilIdle()
        assertTrue(vm.estado.value.guardado)
        vm.actualizarCampo(CampoPerfil.NOMBRE, "Otra edición")
        assertFalse(vm.estado.value.guardado)
    }

    @Test fun evitaConsultasRepetidasDuranteLaCarga() = runTest {
        val vm = PerfilViewModel(repository)
        vm.cargarPerfil()
        advanceUntilIdle()
        assertEquals(1, repository.llamadasConsultar)
    }

    @Test fun noGuardaAntesDeTenerUnPerfil() = runTest {
        val vm = PerfilViewModel(repository)
        vm.guardarCambios()
        advanceUntilIdle()
        assertEquals(0, repository.llamadasGuardar)
    }

    private class RepositorioDePrueba(usuario: Usuario) : AutenticacionRepository {
        var resultadoActual: Result<Usuario?> = Result.success(usuario)
        var llamadasConsultar = 0
        var llamadasGuardar = 0
        var ultimosDatos = emptyList<String>()
        var falloGuardar: Exception? = null
        var esperaGuardar: CompletableDeferred<Unit>? = null

        override suspend fun obtenerUsuarioActual(): Result<Usuario?> {
            llamadasConsultar++
            return resultadoActual
        }

        override suspend fun actualizarPerfil(nombreCompleto: String, dni: String, celular: String): Result<Usuario> {
            llamadasGuardar++
            ultimosDatos = listOf(nombreCompleto, dni, celular)
            esperaGuardar?.await()
            falloGuardar?.let { return Result.failure(it) }
            val usuario = resultadoActual.getOrThrow() ?: return Result.failure(Exception("Sin sesión"))
            val actualizado = usuario.copy(nombreCompleto = nombreCompleto, dni = dni, celular = celular)
            resultadoActual = Result.success(actualizado)
            return Result.success(actualizado)
        }

        override suspend fun registrar(nombreCompleto: String, dni: String, celular: String, correo: String, contrasena: String): Result<Usuario> =
            Result.failure(UnsupportedOperationException())
        override suspend fun iniciarSesion(correo: String, contrasena: String): Result<Usuario> =
            Result.failure(UnsupportedOperationException())
        override suspend fun recuperarContrasena(correo: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun cerrarSesion(): Result<Unit> = Result.success(Unit)
    }
}
