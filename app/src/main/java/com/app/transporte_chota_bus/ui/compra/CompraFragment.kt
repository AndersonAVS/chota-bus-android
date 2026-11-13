package com.app.transporte_chota_bus.ui.compra

import android.app.AlertDialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.di.ContenedorDependencias
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CompraFragment : Fragment(R.layout.comprar_boleto) {
    private val viewModel: CompraViewModel by viewModels {
        CompraViewModelFactory(ContenedorDependencias.reservas, requireArguments().getInt("salida_id"), requireArguments().getInt("pasajeros"))
    }
    private var dialogo: AlertDialog? = null
    private var firmaGrid: Triple<List<AsientoDisponible>, Set<Int>, Boolean>? = null

    // Presenta la salida elegida y observa la reserva con el ciclo de vida de la vista.
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val args = requireArguments()
        view.findViewById<TextView>(R.id.texto_ruta_compra).text = args.getString("ruta")
        view.findViewById<TextView>(R.id.texto_horario_compra).text = args.getString("horario")
        view.findViewById<TextView>(R.id.texto_embarque_compra).text = args.getString("embarque")
        view.findViewById<TextView>(R.id.texto_llegada_compra).text = args.getString("llegada")
        view.findViewById<TextView>(R.id.texto_cantidad_compra).text = resources.getQuantityString(R.plurals.compra_cantidad, viewModel.pasajeros, viewModel.pasajeros)
        view.findViewById<View>(R.id.btn_datos_pasajeros).setOnClickListener {
            viewModel.actualizarTiempo()
            val estado=viewModel.estado.value
            val reserva=estado.reserva
            if(!estado.ocupada&&reserva?.estado==EstadoReserva.PENDIENTE&&estado.segundos>0){
                it.isEnabled=false
                (requireActivity() as MainActivity).abrirPantalla(PasajerosFragment.crear(reserva.id))
            }
        }
        view.findViewById<View>(R.id.btn_reservar_asientos).setOnClickListener { viewModel.reservar() }
        view.findViewById<View>(R.id.btn_actualizar_asientos).setOnClickListener { viewModel.actualizar() }
        view.findViewById<View>(R.id.btn_cancelar_reserva).setOnClickListener { confirmarCancelacion(false) }
        view.findViewById<View>(R.id.btn_sesion_compra).setOnClickListener { (requireActivity() as MainActivity).volverAlLogin() }
        val callbackVolver = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { volver() }
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, callbackVolver)
        view.findViewById<View>(R.id.btn_volver_compra).setOnClickListener { volver() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.estado.collect { mostrar(it) } }
                launch { while (isActive) { viewModel.actualizarTiempo(); delay(1000) } }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(viewModel.estado.value.inventario!=null)viewModel.actualizar()
    }

    private fun mostrar(estado: EstadoCompra) {
        val vista = view ?: return
        vista.findViewById<View>(R.id.progreso_compra).isVisible = estado.ocupada
        vista.findViewById<TextView>(R.id.texto_error_compra).apply {
            text = estado.error.orEmpty(); isVisible = estado.error != null
        }
        vista.findViewById<View>(R.id.btn_sesion_compra).isVisible = estado.sesionRequerida
        vista.findViewById<View>(R.id.btn_actualizar_asientos).isEnabled = !estado.ocupada && !estado.sesionRequerida
        val r = estado.reserva
        val activa = r?.estado in setOf(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA)
        vista.findViewById<Button>(R.id.btn_reservar_asientos).apply {
            isVisible = !activa && !estado.reservaDesconocida && !estado.sesionRequerida
            isEnabled = !estado.ocupada && (estado.pendienteConfirmacion ||
                (estado.inventario != null && estado.seleccion.size == viewModel.pasajeros))
            setText(if (estado.pendienteConfirmacion) R.string.compra_reintentar_reserva else R.string.compra_reservar)
        }
        vista.findViewById<View>(R.id.btn_datos_pasajeros).apply {
            isVisible=r?.estado==EstadoReserva.PENDIENTE&&estado.segundos>0
            isEnabled=!estado.ocupada
        }
        vista.findViewById<View>(R.id.btn_cancelar_reserva).apply {
            isVisible = r?.estado == EstadoReserva.PENDIENTE
            isEnabled = !estado.ocupada
        }
        vista.findViewById<TextView>(R.id.texto_asiento_seleccionado).text = getString(R.string.compra_elegidos,
            estado.seleccion.sorted().joinToString(", ").ifEmpty { getString(R.string.compra_ninguno) })
        vista.findViewById<TextView>(R.id.texto_estado_reserva).text = when {
            estado.procesando -> getString(R.string.compra_procesando)
            estado.cargando -> getString(R.string.compra_cargando)
            estado.reservaDesconocida -> getString(R.string.compra_recuperar)
            estado.pendienteConfirmacion -> getString(R.string.compra_resultado_pendiente)
            r?.estado == EstadoReserva.PENDIENTE -> getString(R.string.compra_reserva_activa, estado.segundos / 60, estado.segundos % 60)
            r?.estado == EstadoReserva.CANCELADA -> getString(R.string.compra_reserva_cancelada)
            r?.estado == EstadoReserva.EXPIRADA -> getString(R.string.compra_reserva_expirada)
            r?.estado == EstadoReserva.CONFIRMADA -> getString(R.string.compra_reserva_confirmada)
            estado.inventario?.asientos?.none { it.disponible } == true -> getString(R.string.compra_sin_asientos)
            else -> getString(R.string.compra_elige_asientos)
        }
        val importe = r?.takeIf { activa }?.total ?: estado.inventario?.precio?.let {
            BigDecimal(it).multiply(BigDecimal(estado.seleccion.size)).setScale(2).toPlainString()
        }
        vista.findViewById<TextView>(R.id.texto_total_compra).text = getString(R.string.compra_importe, importe ?: "0.00")
        vista.findViewById<TextView>(R.id.texto_etiqueta_total).setText(if (activa) R.string.compra_total_reserva else R.string.compra_total_estimado)
        val firma = Triple(estado.inventario?.asientos.orEmpty(), estado.seleccion, estado.bloqueada)
        if (firma != firmaGrid) { firmaGrid = firma; dibujarAsientos(firma.first, firma.second, firma.third) }
        if (estado.salir) { viewModel.consumirSalida(); parentFragmentManager.popBackStack() }
    }

    // Crea botones desde fila/columna reales de la API, sin inventar plazas o pasillos.
    private fun dibujarAsientos(asientos: List<AsientoDisponible>, elegidos: Set<Int>, bloqueada: Boolean) {
        val contenedor = view?.findViewById<LinearLayout>(R.id.filas_asientos) ?: return
        contenedor.removeAllViews()
        val columnas = asientos.maxOfOrNull { it.columna } ?: return
        val alto = (56 * resources.displayMetrics.density).toInt()
        for ((_, fila) in asientos.groupBy { it.fila }.toSortedMap()) {
            val linea = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
            for (columna in 1..columnas) {
                val asiento = fila.find { it.columna == columna }
                val vista = if (asiento == null) Space(requireContext()) else Button(requireContext()).apply {
                    text = asiento.numero.toString()
                    minWidth = 0; minimumWidth = 0
                    val elegido = asiento.numero in elegidos
                    val color = if (elegido) R.color.chota_purple else if (asiento.disponible) R.color.asiento_libre else R.color.asiento_ocupado
                    backgroundTintList = ColorStateList.valueOf(requireContext().getColor(color))
                    setTextColor(requireContext().getColor(if (elegido) R.color.white else R.color.chota_text))
                    isEnabled = asiento.disponible && !bloqueada
                    isSelected = elegido
                    contentDescription = getString(R.string.compra_descripcion_asiento, asiento.numero,
                        getString(if (elegido) R.string.compra_elegido else if (asiento.disponible) R.string.compra_libre else R.string.compra_ocupado))
                    setOnClickListener { viewModel.seleccionar(asiento.numero) }
                }
                linea.addView(vista, LinearLayout.LayoutParams(0, alto, 1f))
            }
            contenedor.addView(linea)
        }
    }

    private fun volver() {
        val estado = viewModel.estado.value
        if (estado.ocupada) {
            Toast.makeText(requireContext(), R.string.compra_espera, Toast.LENGTH_SHORT).show(); return
        }
        if (estado.reserva?.estado == EstadoReserva.PENDIENTE) { confirmarCancelacion(true); return }
        if (estado.pendienteConfirmacion || estado.reservaDesconocida) {
            dialogo?.dismiss()
            dialogo = AlertDialog.Builder(requireContext()).setMessage(R.string.compra_salir_pendiente)
                .setPositiveButton(R.string.viaje_reintentar) { _, _ ->
                    if (estado.pendienteConfirmacion) viewModel.reservar() else viewModel.actualizar()
                }.setNegativeButton(R.string.compra_salir) { _, _ -> parentFragmentManager.popBackStack() }.show()
        } else parentFragmentManager.popBackStack()
    }
    private fun confirmarCancelacion(salir: Boolean) {
        dialogo?.dismiss()
        dialogo = AlertDialog.Builder(requireContext()).setMessage(R.string.compra_confirmar_cancelacion)
            .setPositiveButton(R.string.compra_cancelar) { _, _ -> viewModel.cancelar(salir) }
            .setNegativeButton(R.string.cancelar_seleccion, null).show()
    }
    override fun onDestroyView() {
        dialogo?.dismiss(); dialogo = null; firmaGrid = null
        super.onDestroyView()
    }
    companion object {
        fun crear(salida: SalidaViaje, pasajeros: Int) = CompraFragment().apply {
            val formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
            arguments = Bundle().apply {
                putInt("salida_id", salida.id); putInt("pasajeros", pasajeros)
                putString("ruta", "${salida.embarque.ciudad} → ${salida.destino.ciudad}")
                putString("horario", formato.format(Instant.parse(salida.salida)))
                putString("embarque", "${salida.embarque.nombre}: ${salida.embarque.direccion}")
                putString("llegada", "${salida.destino.nombre}: ${salida.destino.direccion}")
            }
        }
    }
}
