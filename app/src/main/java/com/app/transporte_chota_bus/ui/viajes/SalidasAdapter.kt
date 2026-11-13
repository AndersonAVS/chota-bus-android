package com.app.transporte_chota_bus.ui.viajes

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.data.model.SalidaViaje
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Muestra las salidas reales recibidas por la API en tarjetas XML reutilizables.
class SalidasAdapter(private val verTerminales: (SalidaViaje) -> Unit, private val seleccionarSalida: (SalidaViaje) -> Unit) : ListAdapter<SalidaViaje, SalidasAdapter.Fila>(object : DiffUtil.ItemCallback<SalidaViaje>() {
    override fun areItemsTheSame(a: SalidaViaje, b: SalidaViaje) = a.id == b.id
    override fun areContentsTheSame(a: SalidaViaje, b: SalidaViaje) = a == b
}) {
    var soloConsulta=false
        set(valor){if(field!=valor){field=valor;notifyDataSetChanged()}}
    class Fila(view: View) : RecyclerView.ViewHolder(view)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Fila(LayoutInflater.from(parent.context).inflate(R.layout.item_salida, parent, false))

    override fun onBindViewHolder(holder: Fila, position: Int) {
        val salida = getItem(position)
        val formato = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
        with(holder.itemView) {
            findViewById<TextView>(R.id.salida_horario).text = context.getString(R.string.viaje_horarios,
                formato.format(Instant.parse(salida.salida)), formato.format(Instant.parse(salida.llegada)))
            findViewById<TextView>(R.id.salida_precio).text = context.getString(R.string.viaje_precio, salida.precio)
            findViewById<TextView>(R.id.salida_terminales).text = context.getString(R.string.viaje_terminales,
                salida.embarque.nombre, salida.destino.nombre)
            findViewById<TextView>(R.id.salida_disponibilidad).text=salida.asientosLibres?.let{context.getString(R.string.viajes_libres,it)}?:context.getString(R.string.viajes_sin_inventario)
            findViewById<TextView>(R.id.salida_duracion).text=java.time.Duration.between(Instant.parse(salida.salida),Instant.parse(salida.llegada)).let{context.getString(R.string.viajes_duracion,it.toMinutes()/60,it.toMinutes()%60)}
            findViewById<View>(R.id.btn_seleccionar_salida).isEnabled=!soloConsulta
            findViewById<View>(R.id.btn_ver_terminales).setOnClickListener { verTerminales(salida) }
            findViewById<View>(R.id.btn_seleccionar_salida).setOnClickListener {
                if(soloConsulta)return@setOnClickListener
                it.isEnabled = false
                seleccionarSalida(salida)
            }
        }
    }
}
