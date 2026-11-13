package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.Usuario

interface AutenticacionRepository {

    suspend fun registrar(
        nombreCompleto: String,
        dni: String,
        celular: String,
        correo: String,
        contrasena: String
    ): Result<Usuario>

    suspend fun iniciarSesion(
        correo: String,
        contrasena: String
    ): Result<Usuario>

    suspend fun recuperarContrasena(
        correo: String
    ): Result<Unit>

    suspend fun obtenerUsuarioActual(): Result<Usuario?>

    suspend fun cerrarSesion(): Result<Unit>
}
