package com.app.transporte_chota_bus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RecuperarContrasenaViewModel(private val repository: AutenticacionRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormularioAcceso())
    val estado = _estado.asStateFlow()

    // Envía la recuperación únicamente después de validar el correo.
    fun recuperar(correo: String) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        val errores = ValidadorAcceso.validarRecuperacion(correo)
        _estado.value = EstadoFormularioAcceso(errores)
        if (errores.isNotEmpty()) return
        _estado.update { it.copy(operacion = EstadoAutenticacion.Cargando) }
        viewModelScope.launch {
            val resultado = repository.recuperarContrasena(correo.trim())
            _estado.update { it.copy(operacion = resultado.fold(
                onSuccess = { EstadoAutenticacion.RecuperacionSolicitada },
                onFailure = { error -> EstadoAutenticacion.Error(error.message ?: "No se pudo solicitar la recuperación.") }
            )) }
        }
    }

    // Limpia la respuesta anterior al corregir el correo.
    fun limpiarError(campo: CampoAcceso) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        _estado.update { it.copy(errores = it.errores - campo, operacion = EstadoAutenticacion.Inicial) }
    }
}
