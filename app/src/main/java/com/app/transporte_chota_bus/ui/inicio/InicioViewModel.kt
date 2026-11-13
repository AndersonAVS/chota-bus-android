package com.app.transporte_chota_bus.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.Usuario
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoSesion(
    val cargando: Boolean = true,
    val usuario: Usuario? = null,
    val error: String? = null
)

// Comprueba la sesión y permite cerrarla desde Inicio.
class InicioViewModel(private val repository: AutenticacionRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoSesion())
    val estado = _estado.asStateFlow()

    init { consultarSesion() }

    fun consultarSesion() {
        _estado.value = EstadoSesion()
        viewModelScope.launch {
            _estado.value = repository.obtenerUsuarioActual().fold(
                onSuccess = { EstadoSesion(cargando = false, usuario = it) },
                onFailure = { EstadoSesion(cargando = false, error = it.message ?: "No pudimos consultar tu sesión.") }
            )
        }
    }

    fun cerrarSesion() {
        if (_estado.value.cargando) return
        val usuario = _estado.value.usuario
        _estado.value = EstadoSesion(usuario = usuario)
        viewModelScope.launch {
            _estado.value = repository.cerrarSesion().fold(
                onSuccess = { EstadoSesion(cargando = false) },
                onFailure = { EstadoSesion(cargando = false, usuario = usuario, error = it.message ?: "No pudimos cerrar la sesión.") }
            )
        }
    }
}
