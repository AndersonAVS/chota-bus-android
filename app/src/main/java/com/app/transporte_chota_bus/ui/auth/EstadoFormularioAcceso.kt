package com.app.transporte_chota_bus.ui.auth

// Combina los errores del formulario con el estado real de autenticación.
data class EstadoFormularioAcceso(
    val errores: Map<CampoAcceso, String> = emptyMap(),
    val operacion: EstadoAutenticacion = EstadoAutenticacion.Inicial
)
