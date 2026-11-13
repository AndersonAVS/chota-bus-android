package com.app.transporte_chota_bus.data.remote

import com.app.transporte_chota_bus.data.model.ErrorReserva
import com.app.transporte_chota_bus.data.model.TipoErrorReserva
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException

data class TokenSesion(val uid: String, val token: String)
interface ProveedorTokenSesion {
    fun usuarioActual(): String?
    suspend fun obtener(forzar: Boolean = false): TokenSesion
}

// Solicita el ID token en memoria; no lo guarda en preferencias, archivos ni registros.
class FirebaseProveedorTokenSesion(private val auth: FirebaseAuth) : ProveedorTokenSesion {
    override fun usuarioActual() = auth.currentUser?.uid
    override suspend fun obtener(forzar: Boolean): TokenSesion {
        val usuario = auth.currentUser ?: throw sinSesion()
        try {
            val token = withTimeout(15_000) {
                suspendCancellableCoroutine<String> { continuacion ->
                    usuario.getIdToken(forzar).addOnCompleteListener { tarea ->
                        if (continuacion.isActive) {
                            if (tarea.isCanceled) continuacion.resumeWith(Result.failure(IOException("No se pudo comprobar la sesión.")))
                            else if (tarea.isSuccessful) {
                                val valor = tarea.result.token
                                if (valor.isNullOrBlank()) continuacion.resumeWith(Result.failure(sinSesion()))
                                else continuacion.resumeWith(Result.success(valor))
                            } else continuacion.resumeWith(Result.failure(tarea.exception ?: IOException()))
                        }
                    }
                }
            }
            if (auth.currentUser?.uid != usuario.uid) throw sinSesion()
            return TokenSesion(usuario.uid, token)
        } catch (_: TimeoutCancellationException) {
            throw IOException("No se pudo comprobar la sesión. Intenta nuevamente.")
        } catch (error: CancellationException) { throw error
        } catch (_: FirebaseAuthInvalidUserException) { throw sinSesion()
        } catch (_: com.google.firebase.FirebaseNetworkException) { throw IOException("No se pudo comprobar la sesión. Revisa tu conexión.") }
    }
    private fun sinSesion() = ErrorReserva(TipoErrorReserva.SESION, "Inicia sesión nuevamente para continuar.")
}
