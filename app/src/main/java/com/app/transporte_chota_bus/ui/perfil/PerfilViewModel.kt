package com.app.transporte_chota_bus.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.Usuario
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import com.app.transporte_chota_bus.domain.validation.ValidadorPerfil
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Conserva los datos editados y el estado de consulta y guardado.
data class EstadoPerfil(
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val usuario: Usuario? = null,
    val nombre: String = "",
    val dni: String = "",
    val celular: String = "",
    val errores: Map<CampoPerfil, String> = emptyMap(),
    val mensaje: String? = null,
    val guardado: Boolean = false,
    val sesionRequerida: Boolean = false
)

class PerfilViewModel(private val repository: AutenticacionRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPerfil())
    val estado = _estado.asStateFlow()
    private var consulta: Job? = null

    init { cargarPerfil() }

    // Recupera el perfil de la cuenta autenticada y permite reintentar si falla.
    fun cargarPerfil() {
        if (consulta?.isActive == true || _estado.value.guardando) return
        _estado.update { it.copy(cargando = true, mensaje = null, guardado = false) }
        consulta = viewModelScope.launch {
            _estado.value = repository.obtenerUsuarioActual().fold(
                onSuccess = { usuario ->
                    if (usuario == null) EstadoPerfil(cargando = false, sesionRequerida = true)
                    else EstadoPerfil(
                        cargando = false, usuario = usuario, nombre = usuario.nombreCompleto,
                        dni = usuario.dni, celular = usuario.celular
                    )
                },
                onFailure = { error ->
                    _estado.value.copy(cargando = false, mensaje = error.message ?: "No pudimos cargar tu perfil.")
                }
            )
        }
    }

    // Actualiza el borrador y limpia el error del campo corregido.
    fun actualizarCampo(campo: CampoPerfil, valor: String) {
        val actual = _estado.value
        if (actual.cargando || actual.guardando || actual.usuario == null) return
        val anterior = when (campo) {
            CampoPerfil.NOMBRE -> actual.nombre
            CampoPerfil.DNI -> actual.dni
            CampoPerfil.CELULAR -> actual.celular
        }
        if (anterior == valor) return
        _estado.update {
            val nuevo = when (campo) {
                CampoPerfil.NOMBRE -> it.copy(nombre = valor)
                CampoPerfil.DNI -> it.copy(dni = valor)
                CampoPerfil.CELULAR -> it.copy(celular = valor)
            }
            nuevo.copy(errores = nuevo.errores - campo, mensaje = null, guardado = false)
        }
    }

    // Valida y guarda los cambios sin modificar la identidad ni el correo.
    fun guardarCambios() {
        val actual = _estado.value
        val usuario = actual.usuario ?: return
        if (actual.cargando || actual.guardando) return
        val errores = ValidadorPerfil.validar(actual.nombre, actual.dni, actual.celular)
        if (errores.isNotEmpty()) {
            _estado.update { it.copy(errores = errores, mensaje = null, guardado = false) }
            return
        }
        val nombre = actual.nombre.trim()
        val dni = actual.dni.trim()
        val celular = actual.celular.trim()
        if (nombre == usuario.nombreCompleto && dni == usuario.dni && celular == usuario.celular) {
            _estado.update { it.copy(
                nombre = nombre, dni = dni, celular = celular,
                errores = emptyMap(), mensaje = "No hay cambios por guardar.", guardado = false
            ) }
            return
        }
        _estado.update { it.copy(guardando = true, errores = emptyMap(), mensaje = null, guardado = false) }
        viewModelScope.launch {
            repository.actualizarPerfil(nombre, dni, celular).fold(
                onSuccess = { actualizado ->
                    _estado.update { it.copy(
                        guardando = false, usuario = actualizado,
                        nombre = actualizado.nombreCompleto, dni = actualizado.dni,
                        celular = actualizado.celular, guardado = true, mensaje = null
                    ) }
                },
                onFailure = { error ->
                    _estado.update { it.copy(
                        guardando = false, guardado = false,
                        mensaje = error.message ?: "No pudimos guardar los cambios. Intenta nuevamente."
                    ) }
                }
            )
        }
    }
}
