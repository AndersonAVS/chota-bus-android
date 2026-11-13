package com.app.transporte_chota_bus.ui.auth

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.R
import kotlinx.coroutines.launch

class RecuperarContrasenaFragment : Fragment(R.layout.recuperar_contrasena) {
    private val viewModel: RecuperarContrasenaViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Obtiene los componentes y conserva el regreso al login.
        val correo = view.findViewById<EditText>(R.id.input_correo_recuperacion)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_correo_recuperacion)
        view.findViewById<View>(R.id.btn_volver_recuperacion).setOnClickListener { volver() }
        view.findViewById<View>(R.id.btn_regresar_login).setOnClickListener { volver() }

        // Valida el correo y solicita las instrucciones al servicio.
        correo.doAfterTextChanged { viewModel.limpiarError(CampoAcceso.CORREO) }
        view.findViewById<View>(R.id.btn_enviar_recuperacion).setOnClickListener {
            viewModel.recuperar(correo.text.toString())
            if (viewModel.estado.value.errores.isNotEmpty()) correo.requestFocus()
        }

        // Muestra carga, errores y confirmación después de la respuesta del servicio.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    correo.error = estado.errores[CampoAcceso.CORREO]
                    val cargando = estado.operacion == EstadoAutenticacion.Cargando
                    correo.isEnabled = !cargando
                    view.findViewById<View>(R.id.btn_enviar_recuperacion).isEnabled = !cargando
                    mensaje.text = when (val operacion = estado.operacion) {
                        EstadoAutenticacion.Cargando -> getString(R.string.enviando_recuperacion)
                        is EstadoAutenticacion.Error -> operacion.mensaje
                        else -> ""
                    }
                    mensaje.isVisible = mensaje.text.isNotEmpty()
                    view.findViewById<View>(R.id.texto_confirmacion_recuperacion).isVisible =
                        estado.operacion == EstadoAutenticacion.RecuperacionSolicitada
                }
            }
        }
    }

    private fun volver() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}
