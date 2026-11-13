package com.app.transporte_chota_bus.ui.perfil

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import com.app.transporte_chota_bus.ui.auth.AutenticacionViewModelFactory
import kotlinx.coroutines.launch

class PerfilFragment : Fragment(R.layout.perfil_usuario) {
    private val viewModel: PerfilViewModel by viewModels { AutenticacionViewModelFactory }

    companion object {
        const val RESULTADO_ACTUALIZADO = "perfil_actualizado"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Relaciona los componentes visuales con los datos del perfil.
        val nombre = view.findViewById<EditText>(R.id.input_nombre_perfil)
        val dni = view.findViewById<EditText>(R.id.input_dni_perfil)
        val celular = view.findViewById<EditText>(R.id.input_celular_perfil)
        val correo = view.findViewById<TextView>(R.id.texto_correo_perfil)
        val formulario = view.findViewById<View>(R.id.formulario_perfil)
        val guardar = view.findViewById<View>(R.id.btn_guardar_perfil)
        val volver = view.findViewById<View>(R.id.btn_volver_perfil)
        val progreso = view.findViewById<View>(R.id.progreso_perfil)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_perfil)
        val reintentar = view.findViewById<View>(R.id.btn_reintentar_perfil)
        val campos = linkedMapOf(
            CampoPerfil.NOMBRE to nombre,
            CampoPerfil.DNI to dni,
            CampoPerfil.CELULAR to celular
        )

        // Recoge los cambios y evita abandonar un guardado en curso.
        val bloquearAtras = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() = Unit
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, bloquearAtras)
        volver.setOnClickListener { requireActivity().onBackPressedDispatcher.onBackPressed() }
        reintentar.setOnClickListener { viewModel.cargarPerfil() }
        campos.forEach { (campo, entrada) ->
            entrada.doAfterTextChanged { viewModel.actualizarCampo(campo, it.toString()) }
        }
        guardar.setOnClickListener {
            viewModel.guardarCambios()
            campos[viewModel.estado.value.errores.keys.firstOrNull()]?.requestFocus()
        }

        // Presenta carga, errores y confirmación del servidor.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    if (estado.sesionRequerida) {
                        (requireActivity() as MainActivity).volverABienvenida()
                        return@collect
                    }
                    formulario.isVisible = estado.usuario != null
                    progreso.isVisible = estado.cargando || estado.guardando
                    val editable = !estado.cargando && !estado.guardando && estado.usuario != null
                    guardar.isEnabled = editable
                    volver.isEnabled = !estado.guardando
                    bloquearAtras.isEnabled = estado.guardando
                    reintentar.isVisible = !estado.cargando && estado.usuario == null && estado.mensaje != null
                    campos.forEach { (campo, entrada) ->
                        val valor = when (campo) {
                            CampoPerfil.NOMBRE -> estado.nombre
                            CampoPerfil.DNI -> estado.dni
                            CampoPerfil.CELULAR -> estado.celular
                        }
                        if (entrada.text.toString() != valor) entrada.setText(valor)
                        entrada.isEnabled = editable
                        entrada.error = estado.errores[campo]
                    }
                    correo.text = estado.usuario?.correo.orEmpty()
                    mensaje.text = when {
                        estado.cargando -> getString(R.string.perfil_cargando)
                        estado.guardando -> getString(R.string.perfil_guardando)
                        estado.guardado -> getString(R.string.perfil_guardado)
                        else -> estado.mensaje.orEmpty()
                    }
                    mensaje.isVisible = mensaje.text.isNotEmpty()

                    // Avisa a Inicio para actualizar el saludo al regresar.
                    if (estado.guardado) {
                        parentFragmentManager.setFragmentResult(RESULTADO_ACTUALIZADO, Bundle())
                    }
                }
            }
        }
    }
}
