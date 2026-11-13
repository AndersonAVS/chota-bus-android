package com.app.transporte_chota_bus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// Mantiene los errores del formulario y la respuesta del registro.
data class EstadoFormularioRegistro(
    val errores: Map<CampoRegistro, String> = emptyMap(),
    val operacion: EstadoAutenticacion = EstadoAutenticacion.Inicial
)

class RegistroViewModel(private val repository: AutenticacionRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoFormularioRegistro())
    val estado = _estado.asStateFlow()

    // Valida los datos y registra la identidad junto con su perfil.
    fun registrar(
        nombre: String, dni: String, celular: String, correo: String,
        contrasena: String, confirmacion: String, aceptaTerminos: Boolean
    ) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        val errores = ValidadorRegistro.validar(
            nombre, dni, celular, correo, contrasena, confirmacion, aceptaTerminos
        )
        _estado.value = EstadoFormularioRegistro(errores)
        if (errores.isNotEmpty()) return
        _estado.update { it.copy(operacion = EstadoAutenticacion.Cargando) }
        viewModelScope.launch {
            val resultado = repository.registrar(nombre.trim(), dni.trim(), celular.trim(), correo.trim(), contrasena)
            _estado.update { it.copy(operacion = resultado.fold(
                onSuccess = { usuario -> EstadoAutenticacion.Autenticado(usuario) },
                onFailure = { error -> EstadoAutenticacion.Error(error.message ?: "No se pudo registrar la cuenta.") }
            )) }
        }
    }

    // Permite corregir los campos sin conservar respuestas anteriores.
    fun limpiarError(campo: CampoRegistro) {
        if (_estado.value.operacion == EstadoAutenticacion.Cargando) return
        _estado.update { actual ->
            val errores = actual.errores - campo
            actual.copy(
                errores = if (campo == CampoRegistro.CONTRASENA) errores - CampoRegistro.CONFIRMACION else errores,
                operacion = EstadoAutenticacion.Inicial
            )
        }
    }
}
