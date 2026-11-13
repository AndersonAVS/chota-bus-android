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

class RegistroFragment : Fragment(R.layout.registro_cuenta) {

    private val viewModel: RegistroViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Relaciona los componentes del formulario con sus campos de validación.
        val nombre = view.findViewById<EditText>(R.id.input_nombre_registro)
        val dni = view.findViewById<EditText>(R.id.input_dni_registro)
        val celular = view.findViewById<EditText>(R.id.input_celular_registro)
        val correo = view.findViewById<EditText>(R.id.input_correo_registro)
        val contrasena = view.findViewById<EditText>(R.id.input_contrasena_registro)
        val confirmacion = view.findViewById<EditText>(R.id.input_confirmar_contrasena)
        val terminos = view.findViewById<CheckBox>(R.id.check_aceptar_terminos)
        val mostrar = view.findViewById<CheckBox>(R.id.check_mostrar_contrasenas_registro)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_registro)
        val campos = linkedMapOf(
            CampoRegistro.NOMBRE to nombre,
            CampoRegistro.DNI to dni,
            CampoRegistro.CELULAR to celular,
            CampoRegistro.CORREO to correo,
            CampoRegistro.CONTRASENA to contrasena,
            CampoRegistro.CONFIRMACION to confirmacion
        )

        // Conserva la navegación entre las pantallas de acceso.
        view.findViewById<View>(R.id.btn_volver_registro).setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        view.findViewById<View>(R.id.btn_ir_login_registro).setOnClickListener {
            (requireActivity() as MainActivity).volverAlLogin()
        }

        // Permite mostrar las contraseñas y corregir mensajes anteriores.
        mostrar.setOnCheckedChangeListener { _, visible ->
            listOf(contrasena, confirmacion).forEach { campo ->
                val posicion = campo.selectionStart
                campo.transformationMethod = if (visible) {
                    HideReturnsTransformationMethod.getInstance()
                } else {
                    PasswordTransformationMethod.getInstance()
                }
                campo.setSelection(posicion.coerceIn(0, campo.text.length))
            }
        }
        campos.forEach { (tipo, campo) ->
            campo.doAfterTextChanged { viewModel.limpiarError(tipo) }
        }
        terminos.setOnCheckedChangeListener { _, _ ->
            viewModel.limpiarError(CampoRegistro.TERMINOS)
        }

        // Solicita el registro después de comprobar los campos.
        view.findViewById<View>(R.id.btn_crear_cuenta).setOnClickListener {
            viewModel.registrar(
                nombre = nombre.text.toString(),
                dni = dni.text.toString(),
                celular = celular.text.toString(),
                correo = correo.text.toString(),
                contrasena = contrasena.text.toString(),
                confirmacion = confirmacion.text.toString(),
                aceptaTerminos = terminos.isChecked
            )
            val primero = viewModel.estado.value.errores.keys.firstOrNull()
            campos[primero]?.requestFocus()
        }

        // Muestra el estado mientras la vista está activa.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    campos.forEach { (tipo, campo) ->
                        campo.error = estado.errores[tipo]
                    }
                    terminos.error = estado.errores[CampoRegistro.TERMINOS]
                    val cargando = estado.operacion == EstadoAutenticacion.Cargando
                    campos.values.forEach { it.isEnabled = !cargando }
                    terminos.isEnabled = !cargando
                    view.findViewById<View>(R.id.btn_crear_cuenta).isEnabled = !cargando
                    mensaje.text = when (val operacion = estado.operacion) {
                        EstadoAutenticacion.Cargando -> getString(R.string.creando_cuenta)
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
