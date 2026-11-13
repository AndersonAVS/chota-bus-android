package com.app.transporte_chota_bus.ui.inicio

import android.os.Bundle
import android.app.AlertDialog
import com.app.transporte_chota_bus.domain.model.SedesChotaBus
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
import com.app.transporte_chota_bus.ui.perfil.PerfilFragment
import kotlinx.coroutines.launch

class InicioFragment : Fragment(R.layout.inicio_busqueda) {
    private val busquedaViewModel: BusquedaViewModel by viewModels {
        BusquedaViewModelFactory(requireContext().applicationContext)
    }
    private var menuCuenta:android.widget.PopupMenu?=null
    private var selector: AlertDialog? = null
    private var calendario: android.app.DatePickerDialog? = null
    private val formulario: FormularioViajeViewModel by viewModels()

    private val viewModel: InicioViewModel by viewModels { AutenticacionViewModelFactory }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val saludo = view.findViewById<TextView>(R.id.texto_saludo_inicio)
        val mensaje = view.findViewById<TextView>(R.id.texto_estado_sesion)
        val abrirMenu = view.findViewById<View>(R.id.btn_menu_cuenta)
        val reintentar = view.findViewById<View>(R.id.btn_reintentar_sesion)
        // Agrupa perfil y cierre de sesion en el menu sin ocupar el formulario de busqueda.
        abrirMenu.setOnClickListener { mostrarMenuCuenta(it) }
        parentFragmentManager.setFragmentResultListener(
            PerfilFragment.RESULTADO_ACTUALIZADO, viewLifecycleOwner
        ) { _, _ -> viewModel.consultarSesion() }

        // Consulta todos los boletos propios sin depender de una compra recien realizada.
        view.findViewById<View>(R.id.btn_mis_boletos).setOnClickListener{
            (requireActivity() as MainActivity).abrirPantalla(com.app.transporte_chota_bus.ui.compra.MisBoletosFragment())
        }
        view.findViewById<View>(R.id.btn_historial).setOnClickListener{
            (requireActivity() as MainActivity).abrirPantalla(com.app.transporte_chota_bus.ui.compra.HistorialComprasFragment())
        }
        reintentar.setOnClickListener { viewModel.consultarSesion() }

        // Conecta los selectores con las preferencias locales.
        val origen = view.findViewById<TextView>(R.id.texto_origen)
        val destino = view.findViewById<TextView>(R.id.texto_destino)
        val filaOrigen = view.findViewById<View>(R.id.fila_origen)
        val filaDestino = view.findViewById<View>(R.id.fila_destino)
        val intercambiar = view.findViewById<View>(R.id.btn_intercambiar_ruta)
        val mensajePreferencias = view.findViewById<TextView>(R.id.texto_estado_preferencias)

        val fecha = view.findViewById<TextView>(R.id.texto_fecha)
        val pasajeros = view.findViewById<TextView>(R.id.texto_pasajeros)
        val filaFecha = view.findViewById<View>(R.id.fila_fecha)
        val filaPasajeros = view.findViewById<View>(R.id.fila_pasajeros)
        val buscar = view.findViewById<View>(R.id.btn_buscar_viajes)

        // Selecciona los filtros y abre los resultados cuando el formulario es valido.
        filaFecha.setOnClickListener { mostrarCalendario() }
        filaPasajeros.setOnClickListener {
            if (selector?.isShowing == true) return@setOnClickListener
            val opciones = (1..9).map { resources.getQuantityString(R.plurals.viaje_cantidad, it, it) }.toTypedArray()
            selector = AlertDialog.Builder(requireContext()).setTitle(R.string.viaje_pasajeros)
                .setSingleChoiceItems(opciones, formulario.pasajeros.value - 1) { dialogo, posicion ->
                    formulario.seleccionarPasajeros(posicion + 1)
                    dialogo.dismiss()
                }.setNegativeButton(R.string.cancelar_seleccion, null).show()
        }
        buscar.setOnClickListener {
            val seleccion = busquedaViewModel.estado.value.seleccion
            val filtros = com.app.transporte_chota_bus.domain.model.FiltrosViaje(
                seleccion.origen, seleccion.destino, formulario.fecha.value, formulario.pasajeros.value)
            val error = filtros.error()
            if (error != null) {
                android.widget.Toast.makeText(requireContext(), error, android.widget.Toast.LENGTH_LONG).show()
            } else {
                buscar.isEnabled = false
                (requireActivity() as MainActivity).abrirPantalla(
                    com.app.transporte_chota_bus.ui.viajes.ResultadosFragment.crear(filtros))
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { formulario.fecha.collect { fecha.text = it.ifBlank { getString(R.string.viaje_elegir_fecha) } } }
                launch { formulario.pasajeros.collect { pasajeros.text = resources.getQuantityString(R.plurals.viaje_cantidad, it, it) } }
            }
        }

        fun actualizarControles() {
            val sesion = viewModel.estado.value
            val preferencias = busquedaViewModel.estado.value
            val habilitado = !sesion.cargando && sesion.usuario != null && sesion.error == null &&
                !preferencias.cargando && !preferencias.guardando
            filaOrigen.isEnabled = habilitado
            filaDestino.isEnabled = habilitado
            intercambiar.isEnabled = habilitado
            filaFecha.isEnabled = habilitado
            filaPasajeros.isEnabled = habilitado
            buscar.isEnabled = habilitado
        }
        actualizarControles()
        filaOrigen.setOnClickListener { mostrarSelector(true) }
        filaDestino.setOnClickListener { mostrarSelector(false) }
        intercambiar.setOnClickListener { busquedaViewModel.intercambiar() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                busquedaViewModel.estado.collect { estado ->
                    origen.text = estado.seleccion.origen
                    destino.text = estado.seleccion.destino
                    mensajePreferencias.text = when {
                        estado.cargando -> getString(R.string.preferencias_cargando)
                        estado.guardando -> getString(R.string.preferencias_guardando)
                        else -> estado.mensaje.orEmpty()
                    }
                    mensajePreferencias.isVisible = mensajePreferencias.text.isNotEmpty()
                    actualizarControles()
                }
            }
        }

        // Presenta el usuario real y protege el acceso sin una sesión válida.
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    saludo.text = estado.usuario?.let { getString(R.string.saludo_usuario, it.nombreCompleto) }
                        ?: getString(R.string.consultando_sesion)
                    actualizarControles()
                    abrirMenu.isEnabled = !estado.cargando
                    menuCuenta?.menu?.findItem(R.id.btn_mi_perfil)?.isEnabled = !estado.cargando && estado.usuario != null && estado.error == null
                    menuCuenta?.menu?.findItem(R.id.btn_cerrar_sesion)?.isEnabled = !estado.cargando
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

    private fun mostrarMenuCuenta(ancla:View){
        if(viewModel.estado.value.cargando)return
        menuCuenta?.dismiss()
        menuCuenta=android.widget.PopupMenu(requireContext(),ancla,android.view.Gravity.START).apply{
            menuInflater.inflate(R.menu.menu_cuenta,menu)
            val estado=viewModel.estado.value
            menu.findItem(R.id.btn_mi_perfil).isEnabled=estado.usuario!=null&&estado.error==null
            setOnMenuItemClickListener{item->
                // Revalida el estado al pulsar para no actuar sobre una sesion que esta cambiando.
                val actual=viewModel.estado.value
                when(item.itemId){
                    R.id.btn_mi_perfil->{
                        if(!actual.cargando&&actual.usuario!=null&&actual.error==null)
                            (requireActivity() as MainActivity).abrirPantalla(PerfilFragment())
                        true
                    }
                    R.id.btn_cerrar_sesion->{if(!actual.cargando)viewModel.cerrarSesion();true}
                    else->false
                }
            }
            show()
        }
    }

    // Ofrece las sedes disponibles y evita elegir el mismo origen y destino.
    private fun mostrarSelector(esOrigen: Boolean) {
        if (selector?.isShowing == true) return
        val estado = busquedaViewModel.estado.value
        if (estado.cargando || estado.guardando) return
        val seleccion = estado.seleccion
        val excluir = if (esOrigen) seleccion.destino else seleccion.origen
        val opciones = SedesChotaBus.nombres.filter { it != excluir }
        val actual = if (esOrigen) seleccion.origen else seleccion.destino
        selector = AlertDialog.Builder(requireContext())
            .setTitle(if (esOrigen) R.string.seleccionar_origen else R.string.seleccionar_destino)
            .setSingleChoiceItems(opciones.toTypedArray(), opciones.indexOf(actual)) { dialogo, posicion ->
                if (esOrigen) busquedaViewModel.seleccionarOrigen(opciones[posicion])
                else busquedaViewModel.seleccionarDestino(opciones[posicion])
                dialogo.dismiss()
            }
            .setNegativeButton(R.string.cancelar_seleccion, null)
            .create()
        selector?.show()
    }

    // Abre el calendario y conserva la fecha como AAAA-MM-DD para el servidor.
    private fun mostrarCalendario() {
        if (calendario?.isShowing == true) return
        val hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Lima"))
        val actual = runCatching { java.time.LocalDate.parse(formulario.fecha.value) }.getOrDefault(hoy)
            .let { if (it.isBefore(hoy)) hoy else it }
        calendario = android.app.DatePickerDialog(requireContext(), { _, anio, mes, dia ->
            formulario.seleccionarFecha(java.time.LocalDate.of(anio, mes + 1, dia).toString())
        }, actual.year, actual.monthValue - 1, actual.dayOfMonth).apply {
            datePicker.minDate = java.util.Calendar.getInstance().apply {
                set(hoy.year, hoy.monthValue - 1, hoy.dayOfMonth, 0, 0, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            datePicker.maxDate = java.util.Calendar.getInstance().apply { set(2099, 11, 31, 23, 59, 59) }.timeInMillis
            show()
        }
    }

    override fun onDestroyView() {
        menuCuenta?.dismiss();menuCuenta=null
        calendario?.dismiss()
        calendario = null
        selector?.dismiss()
        selector = null
        super.onDestroyView()
    }
}
