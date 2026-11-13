package com.app.transporte_chota_bus.data.repository

import com.app.transporte_chota_bus.data.model.Usuario
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

class ErrorDeAutenticacion(message: String) : Exception(message)

// Gestiona la identidad en Authentication y el perfil privado en Firestore.
class FirebaseAutenticacionRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AutenticacionRepository {
    private val exclusividad = Mutex()

    override suspend fun registrar(
        nombreCompleto: String, dni: String, celular: String,
        correo: String, contrasena: String
    ): Result<Usuario> = ejecutar {
        val email = correo.trim()
        val pendiente = auth.currentUser?.takeIf { it.email.equals(email, ignoreCase = true) }
        val identidad = if (pendiente != null) {
            pendiente.reauthenticate(EmailAuthProvider.getCredential(email, contrasena)).esperar()
            pendiente
        } else {
            auth.createUserWithEmailAndPassword(email, contrasena).esperar().user
                ?: throw ErrorDeAutenticacion("No se pudo crear la cuenta. Intenta nuevamente.")
        }
        val usuario = Usuario(
            identidad.uid, nombreCompleto.trim(), dni.trim(), celular.trim(),
            identidad.email ?: email
        )

        // Permite completar un perfil pendiente sin sobrescribir cuentas existentes.
        val referencia = firestore.collection("usuarios").document(identidad.uid)
        try {
            firestore.runTransaction { transaccion ->
                if (transaccion.get(referencia).exists()) {
                    throw ErrorDeAutenticacion("Esta cuenta ya está registrada. Inicia sesión.")
                }
                transaccion.set(referencia, mapOf(
                    "id" to usuario.id,
                    "nombreCompleto" to usuario.nombreCompleto,
                    "dni" to usuario.dni,
                    "celular" to usuario.celular,
                    "correo" to usuario.correo,
                    "creadoEn" to FieldValue.serverTimestamp()
                ))
                usuario
            }.esperar()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ErrorDeAutenticacion) {
            throw e
        } catch (e: Exception) {
            throw ErrorDeAutenticacion(
                "El acceso existe, pero no pudimos completar el perfil. " +
                    "Revisa tu conexión y vuelve a pulsar Crear cuenta con los mismos datos."
            )
        }
    }

    override suspend fun iniciarSesion(correo: String, contrasena: String): Result<Usuario> = ejecutar {
        val identidad = auth.signInWithEmailAndPassword(correo.trim(), contrasena).esperar().user
            ?: throw ErrorDeAutenticacion("No se pudo iniciar sesión.")
        leerPerfil(identidad)
    }

    override suspend fun recuperarContrasena(correo: String): Result<Unit> = ejecutar {
        try {
            auth.setLanguageCode("es")
            auth.sendPasswordResetEmail(correo.trim()).esperar()
        } catch (e: FirebaseAuthInvalidUserException) {
            // Mantiene la misma respuesta para no revelar cuentas registradas.
            if (e.errorCode != "ERROR_USER_NOT_FOUND") throw e
        }
        Unit
    }

    override suspend fun obtenerUsuarioActual(): Result<Usuario?> = ejecutar {
        val identidad = auth.currentUser ?: return@ejecutar null
        try {
            identidad.reload().esperar()
            auth.currentUser?.let { leerPerfil(it) }
        } catch (e: FirebaseAuthInvalidUserException) {
            auth.signOut()
            null
        }
    }

    override suspend fun cerrarSesion(): Result<Unit> = ejecutar { auth.signOut() }

    // Recupera el perfil del servidor antes de habilitar la pantalla de inicio.
    private suspend fun leerPerfil(identidad: FirebaseUser): Usuario {
        val doc = firestore.collection("usuarios").document(identidad.uid).get(Source.SERVER).esperar()
        if (!doc.exists()) {
            throw ErrorDeAutenticacion(
                "Tu cuenta aún no tiene un perfil completo. Abre Crear cuenta y completa tus datos."
            )
        }
        val usuario = Usuario(
            doc.getString("id").orEmpty(), doc.getString("nombreCompleto").orEmpty(),
            doc.getString("dni").orEmpty(), doc.getString("celular").orEmpty(),
            doc.getString("correo").orEmpty()
        )
        if (usuario.id != identidad.uid || usuario.nombreCompleto.isBlank() ||
            !usuario.dni.matches(Regex("[0-9]{8}")) ||
            !usuario.celular.matches(Regex("[0-9]{9}")) ||
            usuario.correo != identidad.email) {
            throw ErrorDeAutenticacion("No pudimos validar tu perfil. Contacta con soporte.")
        }
        return usuario
    }

    // Controla solicitudes repetidas, tiempos de espera y mensajes del servicio.
    private suspend fun <T> ejecutar(operacion: suspend () -> T): Result<T> = exclusividad.withLock {
        try {
            Result.success(withTimeout(30_000) { operacion() })
        } catch (e: TimeoutCancellationException) {
            Result.failure(ErrorDeAutenticacion("La solicitud tardó demasiado. Revisa tu conexión e intenta nuevamente."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(ErrorDeAutenticacion(mensajeDeError(e)))
        }
    }

    private fun mensajeDeError(error: Exception): String = when (error) {
        is ErrorDeAutenticacion -> error.message.orEmpty()
        is FirebaseNetworkException -> "No pudimos conectar. Revisa tu conexión a internet."
        is FirebaseAuthException -> when (error.errorCode) {
            "ERROR_EMAIL_ALREADY_IN_USE" -> "Este correo ya está registrado. Inicia sesión o recupera tu contraseña."
            "ERROR_WEAK_PASSWORD" -> "La contraseña no cumple los requisitos de seguridad."
            "ERROR_INVALID_EMAIL" -> "Ingresa un correo electrónico válido."
            "ERROR_USER_DISABLED" -> "La cuenta no está disponible. Contacta con soporte."
            "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera unos minutos y vuelve a intentarlo."
            "ERROR_OPERATION_NOT_ALLOWED" -> "El servicio de acceso no está disponible en este momento."
            else -> "No pudimos completar el acceso. Revisa tu correo y contraseña e intenta nuevamente."
        }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "No pudimos acceder a tu perfil. Contacta con soporte."
            else -> "No pudimos consultar tu perfil. Revisa tu conexión e intenta nuevamente."
        }
        else -> "No se pudo completar la operación. Intenta nuevamente."
    }
}

// Espera la respuesta de Firebase sin bloquear la pantalla.
private suspend fun <T> Task<T>.esperar(): T = suspendCancellableCoroutine { continuacion ->
    addOnCompleteListener { tarea ->
        if (continuacion.isActive) {
            if (tarea.isCanceled) continuacion.cancel()
            else if (tarea.isSuccessful) continuacion.resumeWith(Result.success(tarea.result))
            else continuacion.resumeWith(Result.failure(
                tarea.exception ?: IllegalStateException("El servicio no devolvió una respuesta válida")
            ))
        }
    }
}
