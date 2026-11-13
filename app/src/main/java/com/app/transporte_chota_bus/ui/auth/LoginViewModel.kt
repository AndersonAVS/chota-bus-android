package com.app.transporte_chota_bus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: AutenticacionRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormularioAcceso())
    val estado = _estado.asStateFlow()

    // Valida el formulario y solicita el acceso al repositorio.
    fun iniciarSesion(correo: String, contrasena: String) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        val errores = ValidadorAcceso.validarLogin(correo, contrasena)
        _estado.value = EstadoFormularioAcceso(errores)
        if (errores.isNotEmpty()) return
        _estado.update { it.copy(operacion = EstadoAutenticacion.Cargando) }
        viewModelScope.launch {
            val resultado = repository.iniciarSesion(correo.trim(), contrasena)
            _estado.update { it.copy(operacion = resultado.fold(
                onSuccess = { usuario -> EstadoAutenticacion.Autenticado(usuario) },
                onFailure = { error -> EstadoAutenticacion.Error(error.message ?: "No se pudo iniciar sesión.") }
            )) }
        }
    }

    // Permite corregir los campos después de una respuesta.
    fun limpiarError(campo: CampoAcceso) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        _estado.update { it.copy(errores = it.errores - campo, operacion = EstadoAutenticacion.Inicial) }
    }
}
