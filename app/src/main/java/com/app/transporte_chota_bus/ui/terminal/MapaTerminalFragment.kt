package com.app.transporte_chota_bus.ui.terminal

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.app.transporte_chota_bus.BuildConfig
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.data.model.TerminalViaje
import com.app.transporte_chota_bus.domain.model.EstadoUbicacionTerminal
import com.app.transporte_chota_bus.domain.model.UbicacionTerminal
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapaTerminalFragment : Fragment(R.layout.mapa_terminal) {
    private var esLlegada = false
    private var mapa: GoogleMap? = null
    private var espera: Job? = null
    private var mapaSolicitado = false
    private val terminal: TerminalViaje get() = leerTerminal(requireArguments().getBundle(if (esLlegada) "llegada" else "embarque")!!)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        esLlegada = savedInstanceState?.getBoolean("es_llegada") ?: false
    }

    // Muestra los terminales del viaje seleccionado y conserva la eleccion al girar.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_volver_terminal).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.btn_terminal_embarque).setOnClickListener { esLlegada = false; mostrarTerminal() }
        view.findViewById<View>(R.id.btn_terminal_llegada).setOnClickListener { esLlegada = true; mostrarTerminal() }
        view.findViewById<View>(R.id.btn_como_llegar).setOnClickListener { abrirIndicaciones() }
        view.findViewById<View>(R.id.btn_reintentar_mapa).setOnClickListener { mostrarTerminal() }
        mostrarTerminal()
    }

    private fun mostrarTerminal() {
        val vista = view ?: return
        espera?.cancel()
        val actual = terminal
        vista.findViewById<TextView>(R.id.texto_tipo_terminal).setText(
            if (esLlegada) R.string.terminal_punto_llegada else R.string.terminal_punto_embarque)
        vista.findViewById<TextView>(R.id.texto_nombre_terminal).text = actual.nombre
        vista.findViewById<TextView>(R.id.texto_ciudad_terminal).text = actual.ciudad
        vista.findViewById<TextView>(R.id.texto_direccion_terminal).text = actual.direccion
        vista.findViewById<View>(R.id.btn_como_llegar).isEnabled = UbicacionTerminal.tieneCoordenadas(actual)
        actualizarSelector(vista.findViewById(R.id.btn_terminal_embarque), !esLlegada)
        actualizarSelector(vista.findViewById(R.id.btn_terminal_llegada), esLlegada)

        // El mapa requiere coordenadas, clave configurada y Google Play Services.
        val servicios = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(requireContext()) == ConnectionResult.SUCCESS
        val estado = UbicacionTerminal.estado(actual, BuildConfig.MAPS_CONFIGURADO, servicios)
        val contenedor = vista.findViewById<View>(R.id.contenedor_mapa)
        contenedor.isVisible = estado == EstadoUbicacionTerminal.LISTA
        vista.findViewById<View>(R.id.btn_reintentar_mapa).isVisible = false
        when (estado) {
            EstadoUbicacionTerminal.PENDIENTE -> mostrarEstado(R.string.terminal_ubicacion_pendiente, false)
            EstadoUbicacionTerminal.MAPA_NO_DISPONIBLE -> mostrarEstado(R.string.terminal_mapa_no_disponible, false)
            EstadoUbicacionTerminal.LISTA -> cargarMapa()
        }
    }

    private fun actualizarSelector(boton: Button, seleccionado: Boolean) {
        boton.isSelected = seleccionado
        boton.backgroundTintList = ColorStateList.valueOf(requireContext().getColor(
            if (seleccionado) R.color.chota_purple else R.color.white))
        boton.setTextColor(requireContext().getColor(if (seleccionado) R.color.white else R.color.chota_purple))
    }

    // El fragmento hijo administra el ciclo de vida del mapa nativo.
    private fun cargarMapa() {
        mostrarEstado(R.string.terminal_mapa_cargando, true)
        espera = viewLifecycleOwner.lifecycleScope.launch {
            delay(20_000)
            mostrarEstado(R.string.terminal_mapa_no_disponible, false)
            view?.findViewById<View>(R.id.btn_reintentar_mapa)?.isVisible = true
        }
        mapa?.let { dibujarTerminal(it); return }
        if (mapaSolicitado) return
        val propietario = viewLifecycleOwner
        try {
            val fragmento = childFragmentManager.findFragmentByTag(TAG_MAPA) as? SupportMapFragment
                ?: SupportMapFragment.newInstance().also {
                    childFragmentManager.beginTransaction().replace(R.id.contenedor_mapa, it, TAG_MAPA).commitNow()
                }
            mapaSolicitado = true
            fragmento.getMapAsync { disponible ->
                if (viewLifecycleOwnerLiveData.value !== propietario ||
                    !propietario.lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) return@getMapAsync
                mapa = disponible
                // No activa My Location ni solicita permisos de ubicacion personal.
                disponible.uiSettings.isMyLocationButtonEnabled = false
                disponible.uiSettings.isMapToolbarEnabled = false
                disponible.uiSettings.isZoomControlsEnabled = true
                disponible.uiSettings.isScrollGesturesEnabled = false
                if (UbicacionTerminal.tieneCoordenadas(terminal)) dibujarTerminal(disponible)
            }
        } catch (_: Exception) {
            mapaSolicitado = false
            espera?.cancel()
            mostrarEstado(R.string.terminal_mapa_no_disponible, false)
            view?.findViewById<View>(R.id.btn_reintentar_mapa)?.isVisible = true
        }
    }

    private fun dibujarTerminal(disponible: GoogleMap) {
        val actual = terminal
        if (!UbicacionTerminal.tieneCoordenadas(actual)) return
        val punto = LatLng(actual.latitud!!, actual.longitud!!)
        disponible.clear()
        disponible.addMarker(MarkerOptions().position(punto).title(actual.nombre).snippet(actual.direccion))
        disponible.moveCamera(CameraUpdateFactory.newLatLngZoom(punto, 16f))
        disponible.setOnMapLoadedCallback {
            if (view == null || !UbicacionTerminal.tieneCoordenadas(terminal)) return@setOnMapLoadedCallback
            espera?.cancel()
            mostrarEstado(null, false)
            view?.findViewById<View>(R.id.btn_reintentar_mapa)?.isVisible = false
        }
    }

    private fun mostrarEstado(texto: Int?, cargando: Boolean) {
        val vista = view ?: return
        vista.findViewById<TextView>(R.id.texto_estado_mapa).apply {
            text = texto?.let { getString(it) }.orEmpty()
            isVisible = texto != null
        }
        vista.findViewById<View>(R.id.progreso_mapa).isVisible = cargando
    }

    // Delega las indicaciones a Google Maps o al navegador sin pedir el GPS del pasajero.
    private fun abrirIndicaciones() {
        val enlace = UbicacionTerminal.enlaceComoLlegar(terminal) ?: return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(enlace)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(requireContext(), R.string.terminal_sin_aplicacion, Toast.LENGTH_LONG).show()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("es_llegada", esLlegada)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        espera?.cancel()
        espera = null
        mapa?.setOnMapLoadedCallback(null)
        mapa = null
        mapaSolicitado = false
        super.onDestroyView()
    }

    companion object {
        private const val TAG_MAPA = "mapa_terminal_viaje"
        fun crear(embarque: TerminalViaje, llegada: TerminalViaje) = MapaTerminalFragment().apply {
            arguments = Bundle().apply {
                putBundle("embarque", guardarTerminal(embarque))
                putBundle("llegada", guardarTerminal(llegada))
            }
        }

        // Conserva los datos de la API en argumentos restaurables, sin otra consulta.
        private fun guardarTerminal(terminal: TerminalViaje) = Bundle().apply {
            putInt("id", terminal.id)
            putString("nombre", terminal.nombre)
            putString("ciudad", terminal.ciudad)
            putString("direccion", terminal.direccion)
            terminal.latitud?.let { putDouble("latitud", it) }
            terminal.longitud?.let { putDouble("longitud", it) }
        }
        private fun leerTerminal(datos: Bundle) = TerminalViaje(
            datos.getInt("id"), datos.getString("nombre").orEmpty(), datos.getString("ciudad").orEmpty(),
            datos.getString("direccion").orEmpty(),
            if (datos.containsKey("latitud")) datos.getDouble("latitud") else null,
            if (datos.containsKey("longitud")) datos.getDouble("longitud") else null
        )
    }
}
