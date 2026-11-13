package com.app.transporte_chota_bus.ui.auth

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.widget.TextView
import androidx.core.view.isVisible
import com.app.transporte_chota_bus.ui.inicio.InicioViewModel
import kotlinx.coroutines.launch
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R

class BienvenidaFragment : Fragment(R.layout.bienvenida) {
    private val viewModel: InicioViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Restaura una sesión existente cuando su perfil puede verificarse.
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_bienvenida)
        val reintentar = view.findViewById<View>(R.id.btn_reintentar_bienvenida)
        reintentar.setOnClickListener { viewModel.consultarSesion() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    view.findViewById<View>(R.id.btn_bienvenida_login).isEnabled = !estado.cargando
                    view.findViewById<View>(R.id.btn_bienvenida_registro).isEnabled = !estado.cargando
                    mensaje.text = if (estado.cargando) getString(R.string.consultando_sesion)
                        else estado.error.orEmpty()
                    mensaje.isVisible = mensaje.text.isNotEmpty()
                    reintentar.isVisible = estado.error != null
                    if (estado.usuario != null && !estado.cargando) {
                        (requireActivity() as MainActivity).entrarAlInicio()
                    }
                }
            }
        }


        view.findViewById<View>(R.id.btn_bienvenida_login).setOnClickListener {
            (requireActivity() as MainActivity).abrirPantalla(LoginFragment())
        }

        view.findViewById<View>(R.id.btn_bienvenida_registro).setOnClickListener {
            (requireActivity() as MainActivity).abrirPantalla(RegistroFragment())
        }
    }
}
