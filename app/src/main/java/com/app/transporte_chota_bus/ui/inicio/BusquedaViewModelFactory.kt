package com.app.transporte_chota_bus.ui.inicio

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.transporte_chota_bus.di.ContenedorDependencias

// Proporciona el repositorio local sin guardar una referencia a la actividad.
class BusquedaViewModelFactory(context: Context) : ViewModelProvider.Factory {
    private val repository = ContenedorDependencias.preferencias(context.applicationContext)

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass == BusquedaViewModel::class.java)
        return BusquedaViewModel(repository) as T
    }
}
