package com.app.transporte_chota_bus.ui.auth

import com.app.transporte_chota_bus.data.model.Usuario
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import com.app.transporte_chota_bus.ui.inicio.InicioViewModel
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
class AutenticacionViewModelsTest {
    private val dispatcher = StandardTestDispatcher()
    private val usuario = Usuario("uid-prueba", "María Torres", "01234567", "987654321", "maria@example.com")
    private lateinit var repository: RepositorioDePrueba

    @Before fun preparar() {
        Dispatchers.setMain(dispatcher)
        repository = RepositorioDePrueba(usuario)
    }

    @After fun terminar() { Dispatchers.resetMain() }

    @Test fun loginInvalidoNoConsultaElServicio() = runTest {
        val vm = LoginViewModel(repository)
        vm.iniciarSesion("maria@", "")
        advanceUntilIdle()
        assertEquals(0, repository.llamadasLogin)
        assertEquals(2, vm.estado.value.errores.size)
    }

    @Test fun loginConfirmaSoloDespuesDeLaRespuesta() = runTest {
        val vm = LoginViewModel(repository)
        vm.iniciarSesion(" maria@example.com ", " clave ")
        assertEquals(EstadoAutenticacion.Cargando, vm.estado.value.operacion)
        advanceUntilIdle()
        assertEquals(EstadoAutenticacion.Autenticado(usuario), vm.estado.value.operacion)
        assertEquals("maria@example.com", repository.ultimoCorreo)
        assertEquals(" clave ", repository.ultimaContrasena)
    }

    @Test fun loginPresentaElErrorDelRepositorio() = runTest {
        repository.resultadoLogin = Result.failure(Exception("No hay conexión"))
        val vm = LoginViewModel(repository)
        vm.iniciarSesion("maria@example.com", "clave")
        advanceUntilIdle()
        assertEquals(EstadoAutenticacion.Error("No hay conexión"), vm.estado.value.operacion)
    }

    @Test fun loginEvitaEnviosRepetidosMientrasEspera() = runTest {
        val espera = CompletableDeferred<Unit>()
        repository.esperaLogin = espera
        val vm = LoginViewModel(repository)
        vm.iniciarSesion("maria@example.com", "clave")
        vm.iniciarSesion("maria@example.com", "clave")
        runCurrent()
        assertEquals(1, repository.llamadasLogin)
        espera.complete(Unit)
        advanceUntilIdle()
        assertTrue(vm.estado.value.operacion is EstadoAutenticacion.Autenticado)
    }

    @Test fun registroInvalidoNoCreaCuenta() = runTest {
        val vm = RegistroViewModel(repository)
        vm.registrar("", "", "", "", "", "", false)
        advanceUntilIdle()
        assertEquals(0, repository.llamadasRegistro)
        assertEquals(7, vm.estado.value.errores.size)
    }

    @Test fun registroEnviaPerfilNormalizadoYConservaContrasena() = runTest {
        val vm = RegistroViewModel(repository)
        vm.registrar(" María Torres ", "01234567", "987654321", " maria@example.com ", " Clave123 ", " Clave123 ", true)
        advanceUntilIdle()
        assertEquals("María Torres", repository.ultimoNombre)
        assertEquals("maria@example.com", repository.ultimoCorreo)
        assertEquals(" Clave123 ", repository.ultimaContrasena)
        assertEquals(EstadoAutenticacion.Autenticado(usuario), vm.estado.value.operacion)
    }

    @Test fun errorDePerfilNoSePresentaComoRegistroExitoso() = runTest {
        repository.resultadoRegistro = Result.failure(Exception("Perfil pendiente"))
        val vm = RegistroViewModel(repository)
        vm.registrar("María Torres", "01234567", "987654321", "maria@example.com", "Clave123", "Clave123", true)
        advanceUntilIdle()
        assertEquals(EstadoAutenticacion.Error("Perfil pendiente"), vm.estado.value.operacion)
    }

    @Test fun recuperacionInvalidaNoEnviaSolicitud() = runTest {
        val vm = RecuperarContrasenaViewModel(repository)
        vm.recuperar("incorrecto")
        advanceUntilIdle()
        assertEquals(0, repository.llamadasRecuperacion)
    }

    @Test fun recuperacionConfirmaSoloDespuesDelServicio() = runTest {
        val vm = RecuperarContrasenaViewModel(repository)
        vm.recuperar(" maria@example.com ")
        assertEquals(EstadoAutenticacion.Cargando, vm.estado.value.operacion)
        advanceUntilIdle()
        assertEquals(EstadoAutenticacion.RecuperacionSolicitada, vm.estado.value.operacion)
        assertEquals("maria@example.com", repository.ultimoCorreo)
    }

    @Test fun recuperacionFallidaNoConfirmaEnvio() = runTest {
        repository.resultadoRecuperacion = Result.failure(Exception("Sin conexión"))
        val vm = RecuperarContrasenaViewModel(repository)
        vm.recuperar("maria@example.com")
        advanceUntilIdle()
        assertEquals(EstadoAutenticacion.Error("Sin conexión"), vm.estado.value.operacion)
    }

    @Test fun inicioRestauraElUsuarioYCierraLaSesion() = runTest {
        val vm = InicioViewModel(repository)
        advanceUntilIdle()
        assertEquals(usuario, vm.estado.value.usuario)
        vm.cerrarSesion()
        advanceUntilIdle()
        assertNull(vm.estado.value.usuario)
        assertFalse(vm.estado.value.cargando)
        assertEquals(1, repository.llamadasCerrar)
    }

    @Test fun inicioNoInventaUnUsuarioSiFallaElPerfil() = runTest {
        repository.resultadoActual = Result.failure(Exception("Perfil no disponible"))
        val vm = InicioViewModel(repository)
        advanceUntilIdle()
        assertNull(vm.estado.value.usuario)
        assertEquals("Perfil no disponible", vm.estado.value.error)
    }

    private class RepositorioDePrueba(usuario: Usuario) : AutenticacionRepository {
        var llamadasLogin = 0
        var llamadasRegistro = 0
        var llamadasRecuperacion = 0
        var llamadasCerrar = 0
        var ultimoCorreo = ""
        var ultimaContrasena = ""
        var ultimoNombre = ""
        var resultadoLogin = Result.success(usuario)
        var resultadoRegistro = Result.success(usuario)
        var resultadoRecuperacion: Result<Unit> = Result.success(Unit)
        var resultadoActual: Result<Usuario?> = Result.success(usuario)
        var esperaLogin: CompletableDeferred<Unit>? = null

        override suspend fun iniciarSesion(correo: String, contrasena: String): Result<Usuario> {
            llamadasLogin++
            ultimoCorreo = correo
            ultimaContrasena = contrasena
            esperaLogin?.await()
            return resultadoLogin
        }

        override suspend fun registrar(nombreCompleto: String, dni: String, celular: String, correo: String, contrasena: String): Result<Usuario> {
            llamadasRegistro++
            ultimoNombre = nombreCompleto
            ultimoCorreo = correo
            ultimaContrasena = contrasena
            return resultadoRegistro
        }

        override suspend fun recuperarContrasena(correo: String): Result<Unit> {
            llamadasRecuperacion++
            ultimoCorreo = correo
            return resultadoRecuperacion
        }

        override suspend fun obtenerUsuarioActual() = resultadoActual

        override suspend fun actualizarPerfil(nombreCompleto: String, dni: String, celular: String): Result<Usuario> {
            val usuario = resultadoActual.getOrThrow() ?: return Result.failure(Exception("Sin sesión"))
            val actualizado = usuario.copy(nombreCompleto = nombreCompleto, dni = dni, celular = celular)
            resultadoActual = Result.success(actualizado)
            return Result.success(actualizado)
        }

        override suspend fun cerrarSesion(): Result<Unit> {
            llamadasCerrar++
            resultadoActual = Result.success(null)
            return Result.success(Unit)
        }
    }
}
