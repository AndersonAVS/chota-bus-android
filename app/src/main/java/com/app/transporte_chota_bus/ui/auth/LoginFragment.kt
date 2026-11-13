package com.app.transporte_chota_bus.ui.auth

import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.iniciar_sesion) {
    private val viewModel: LoginViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtiene los campos y componentes del formulario.
        val correo = view.findViewById<EditText>(R.id.input_correo_login)
        val contrasena = view.findViewById<EditText>(R.id.input_contrasena_login)
        val mostrar = view.findViewById<CheckBox>(R.id.check_mostrar_contrasena)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_login)

        // Mantiene la navegación hacia bienvenida, registro y recuperación.
        view.findViewById<View>(R.id.btn_volver_login).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.btn_ir_registro_login).setOnClickListener {
            (requireActivity() as MainActivity).abrirPantalla(RegistroFragment())
        }
        view.findViewById<View>(R.id.btn_recuperar_contrasena).setOnClickListener {
            (requireActivity() as MainActivity).abrirPantalla(RecuperarContrasenaFragment())
        }

        // Cambia la visibilidad de la contraseña y permite corregir los campos.
        mostrar.setOnCheckedChangeListener { _, visible ->
            val posicion = contrasena.selectionStart
            contrasena.transformationMethod = if (visible) {
                HideReturnsTransformationMethod.getInstance()
            } else {
                PasswordTransformationMethod.getInstance()
            }
            contrasena.setSelection(posicion.coerceIn(0, contrasena.text.length))
        }
        correo.doAfterTextChanged { viewModel.limpiarError(CampoAcceso.CORREO) }
        contrasena.doAfterTextChanged { viewModel.limpiarError(CampoAcceso.CONTRASENA) }

        // Envía los datos validados al servicio de autenticación.
        view.findViewById<View>(R.id.btn_iniciar_sesion).setOnClickListener {
            viewModel.iniciarSesion(correo.text.toString(), contrasena.text.toString())
            when (viewModel.estado.value.errores.keys.firstOrNull()) {
                CampoAcceso.CORREO -> correo.requestFocus()
                CampoAcceso.CONTRASENA -> contrasena.requestFocus()
                null -> Unit
            }
        }

        // Presenta los errores y el resultado mientras la vista está activa.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    correo.error = estado.errores[CampoAcceso.CORREO]
                    contrasena.error = estado.errores[CampoAcceso.CONTRASENA]
                    val cargando = estado.operacion == EstadoAutenticacion.Cargando
                    correo.isEnabled = !cargando
                    contrasena.isEnabled = !cargando
                    view.findViewById<View>(R.id.btn_iniciar_sesion).isEnabled = !cargando
                    mensaje.text = when (val operacion = estado.operacion) {
                        EstadoAutenticacion.Cargando -> getString(R.string.iniciando_sesion)
                        is EstadoAutenticacion.Error -> operacion.mensaje
                        else -> ""
                    }
                    mensaje.isVisible = mensaje.text.isNotEmpty()
                    if (estado.operacion is EstadoAutenticacion.Autenticado) {
                        (requireActivity() as MainActivity).entrarAlInicio()
                    }
                }
            }
        }
    }
}
