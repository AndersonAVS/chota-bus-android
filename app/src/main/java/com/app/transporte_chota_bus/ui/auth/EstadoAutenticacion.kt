package com.app.transporte_chota_bus.ui.auth

import com.app.transporte_chota_bus.data.model.Usuario

sealed interface EstadoAutenticacion {

    data object Inicial : EstadoAutenticacion

    data object Cargando : EstadoAutenticacion

    data class Autenticado(
        val usuario: Usuario
    ) : EstadoAutenticacion

    data object RecuperacionSolicitada : EstadoAutenticacion

    data class Error(
        val mensaje: String
    ) : EstadoAutenticacion
}
