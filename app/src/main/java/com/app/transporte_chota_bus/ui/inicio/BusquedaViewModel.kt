package com.app.transporte_chota_bus.ui.inicio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.PreferenciasBusqueda
import com.app.transporte_chota_bus.data.repository.PreferenciasRepository
import com.app.transporte_chota_bus.domain.model.SedesChotaBus
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoPreferenciasBusqueda(
    val seleccion: PreferenciasBusqueda = PreferenciasBusqueda(),
    val cargando: Boolean = true,
    val guardando: Boolean = false,
    val mensaje: String? = null
)

class BusquedaViewModel(private val repository: PreferenciasRepository) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoPreferenciasBusqueda())
    val estado = _estado.asStateFlow()

    // Observa los valores guardados sin bloquear la pantalla.
    init {
        viewModelScope.launch {
            try {
                repository.preferencias.collect { seleccion ->
                    _estado.update { it.copy(seleccion = seleccion, cargando = false) }
                }
            } catch (error: IOException) {
                _estado.update { it.copy(
                    cargando = false,
                    mensaje = "No pudimos recuperar la última selección. Puedes elegir origen y destino nuevamente."
                ) }
            }
        }
    }

    // Actualiza la selección y conserva siempre una combinación válida.
    fun seleccionarOrigen(origen: String) = guardar(origen, _estado.value.seleccion.destino)

    fun seleccionarDestino(destino: String) = guardar(_estado.value.seleccion.origen, destino)

    fun intercambiar() {
        val seleccion = _estado.value.seleccion
        guardar(seleccion.destino, seleccion.origen)
    }

    private fun guardar(origen: String, destino: String) {
        val actual = _estado.value
        if (actual.cargando || actual.guardando) return
        if (!SedesChotaBus.esSeleccionValida(origen, destino)) {
            _estado.update { it.copy(mensaje = "El origen y el destino deben ser sedes diferentes.") }
            return
        }
        val seleccion = PreferenciasBusqueda(origen, destino)
        if (seleccion == actual.seleccion) return
        _estado.update { it.copy(guardando = true, mensaje = null) }
        viewModelScope.launch {
            repository.guardarRuta(origen, destino).fold(
                onSuccess = {
                    _estado.update { it.copy(
                        seleccion = seleccion, guardando = false, mensaje = "Selección guardada."
                    ) }
                },
                onFailure = {
                    _estado.update { it.copy(
                        guardando = false,
                        mensaje = "No pudimos guardar la selección. Intenta nuevamente."
                    ) }
                }
            )
        }
    }
}
