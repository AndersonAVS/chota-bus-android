package com.app.transporte_chota_bus.ui.viajes

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FiltrosViaje
import kotlinx.coroutines.launch

class ResultadosFragment : Fragment(R.layout.resultados_busqueda) {
    private val filtros: FiltrosViaje get() = requireArguments().let {
        FiltrosViaje(it.getString("origen").orEmpty(), it.getString("destino").orEmpty(), it.getString("fecha").orEmpty(), it.getInt("pasajeros"))
    }
    private val viewModel: ResultadosViewModel by viewModels {
        ResultadosViewModelFactory(ContenedorDependencias.viajesConCache(requireContext()), filtros)
    }

    // Observa el resultado sin repetir la consulta al girar o volver a la pantalla.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val lista = view.findViewById<RecyclerView>(R.id.lista_salidas)
        val adapter = SalidasAdapter(verTerminales = { salida ->
            (requireActivity() as com.app.transporte_chota_bus.MainActivity).abrirPantalla(
                com.app.transporte_chota_bus.ui.terminal.MapaTerminalFragment.crear(salida.embarque, salida.destino))
        }, seleccionarSalida = { salida ->
            (requireActivity() as com.app.transporte_chota_bus.MainActivity).abrirPantalla(
                com.app.transporte_chota_bus.ui.compra.CompraFragment.crear(salida, filtros.pasajeros))
        })
        lista.layoutManager = LinearLayoutManager(requireContext())
        lista.adapter = adapter
        view.findViewById<TextView>(R.id.texto_ruta).text = getString(R.string.viaje_ruta, filtros.origen, filtros.destino)
        view.findViewById<TextView>(R.id.texto_fecha_resultados).text = getString(R.string.viaje_resumen, filtros.fecha, resources.getQuantityString(R.plurals.viaje_cantidad, filtros.pasajeros, filtros.pasajeros))
        view.findViewById<View>(R.id.btn_volver_resultados).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.btn_cambiar_busqueda).setOnClickListener { parentFragmentManager.popBackStack() }
        view.findViewById<View>(R.id.btn_reintentar_viajes).setOnClickListener { viewModel.buscar() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.estado.collect { estado ->
                    view.findViewById<View>(R.id.progreso_viajes).isVisible = estado.cargando
                    val mensaje = view.findViewById<TextView>(R.id.estado_viajes)
                    mensaje.text = when {
                        estado.cargando -> getString(R.string.viajes_cargando)
                        estado.error != null -> estado.error
                        estado.salidas.isEmpty() -> getString(R.string.viajes_vacios)
                        estado.hayMas -> getString(R.string.viajes_mas_resultados)
                        else -> ""
                    }
                    mensaje.isVisible = mensaje.text.isNotEmpty()
                    view.findViewById<View>(R.id.btn_reintentar_viajes).isVisible = estado.error != null
                    adapter.soloConsulta=estado.desdeCache
                    view.findViewById<TextView>(R.id.aviso_cache_viajes).apply{
                        isVisible=estado.desdeCache
                        text=getString(R.string.viajes_cache_aviso,estado.guardadoEn.orEmpty())
                    }
                    if(estado.desdeCache)view.findViewById<View>(R.id.btn_reintentar_viajes).isVisible=true
                    adapter.submitList(estado.salidas)
                }
            }
        }
    }

    override fun onDestroyView() {
        view?.findViewById<RecyclerView>(R.id.lista_salidas)?.adapter = null
        super.onDestroyView()
    }

    companion object {
        fun crear(filtros: FiltrosViaje) = ResultadosFragment().apply {
            arguments = Bundle().apply {
                putString("origen", filtros.origen)
                putString("destino", filtros.destino)
                putString("fecha", filtros.fecha)
                putInt("pasajeros", filtros.pasajeros)
            }
        }
    }
}
