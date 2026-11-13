package com.app.transporte_chota_bus.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.ui.inicio.InicioViewModel

// Entrega el repositorio a cada ViewModel sin conectarlo directamente a Firebase.
object AutenticacionViewModelFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repository = ContenedorDependencias.autenticacion
        return when (modelClass) {
            LoginViewModel::class.java -> LoginViewModel(repository)
            RegistroViewModel::class.java -> RegistroViewModel(repository)
            RecuperarContrasenaViewModel::class.java -> RecuperarContrasenaViewModel(repository)
            InicioViewModel::class.java -> InicioViewModel(repository)
            else -> throw IllegalArgumentException("ViewModel no reconocido")
        } as T
    }
}
