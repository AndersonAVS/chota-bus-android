package com.app.transporte_chota_bus.ui.viajes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.transporte_chota_bus.data.model.SalidaViaje
import com.app.transporte_chota_bus.data.repository.ViajesRepository
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoResultados(
    val cargando: Boolean = false, val salidas: List<SalidaViaje> = emptyList(),
    val error: String? = null, val hayMas: Boolean = false, val desdeCache:Boolean=false, val guardadoEn:String?=null
)

class ResultadosViewModel(private val repository: ViajesRepository, val filtros: FiltrosViaje) : ViewModel() {
    private val _estado = MutableStateFlow(EstadoResultados())
    val estado = _estado.asStateFlow()
    init { buscar() }

    // Ejecuta una sola consulta a la vez y permite reintentar un fallo.
    fun buscar() {
        if (_estado.value.cargando) return
        filtros.error()?.let { _estado.value = EstadoResultados(error = it); return }
        _estado.value = EstadoResultados(cargando = true)
        viewModelScope.launch {
            repository.buscar(filtros).fold(
                onSuccess = { _estado.value = EstadoResultados(salidas = it.salidas, hayMas = it.hayMas, desdeCache=it.desdeCache, guardadoEn=it.guardadoEn) },
                onFailure = { _estado.value = EstadoResultados(error = it.message ?: "No se pudo consultar los viajes.") }
            )
        }
    }
}

class ResultadosViewModelFactory(private val repository: ViajesRepository, private val filtros: FiltrosViaje) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ResultadosViewModel::class.java))
        return ResultadosViewModel(repository, filtros) as T
    }
}
