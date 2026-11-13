package com.app.transporte_chota_bus.ui.inicio

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.ui.auth.AutenticacionViewModelFactory
import kotlinx.coroutines.launch

class InicioFragment : Fragment(R.layout.inicio_busqueda) {
    private val viewModel: InicioViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saludo = view.findViewById<TextView>(R.id.texto_saludo_inicio)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_sesion)
        val cerrar = view.findViewById<View>(R.id.btn_cerrar_sesion)
        val reintentar = view.findViewById<View>(R.id.btn_reintentar_sesion)
        cerrar.setOnClickListener { viewModel.cerrarSesion() }
        reintentar.setOnClickListener { viewModel.consultarSesion() }

        // Presenta el usuario real y protege el acceso sin una sesión válida.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    saludo.text = estado.usuario?.let { getString(R.string.saludo_usuario, it.nombreCompleto) }
                        ?: getString(R.string.consultando_sesion)
                    cerrar.isEnabled = !estado.cargando
                    mensaje.text = estado.error.orEmpty()
                    mensaje.isVisible = estado.error != null
                    reintentar.isVisible = estado.error != null
                    if (!estado.cargando && estado.error == null && estado.usuario == null) {
                        (requireActivity() as MainActivity).volverABienvenida()
                    }
                }
            }
        }
    }
}
