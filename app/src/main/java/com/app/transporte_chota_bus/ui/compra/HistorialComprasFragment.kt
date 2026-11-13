package com.app.transporte_chota_bus.ui.compra

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.recyclerview.widget.*
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.data.model.*
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class HistorialComprasFragment:Fragment(R.layout.historial_compras){
    private val vm:HistorialComprasViewModel by viewModels{HistorialComprasViewModelFactory(ContenedorDependencias.reservas)}
    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        val adaptador=HistorialAdapter{id->if(vm.verificarSesion())(requireActivity() as MainActivity).abrirPantalla(BoletosCompraFragment.crear(id))}
        view.findViewById<RecyclerView>(R.id.lista_historial).apply{layoutManager=LinearLayoutManager(requireContext());adapter=adaptador}
        view.findViewById<View>(R.id.btn_volver_historial).setOnClickListener{parentFragmentManager.popBackStack()}
        view.findViewById<View>(R.id.btn_actualizar_historial).setOnClickListener{vm.cargar()}
        view.findViewById<View>(R.id.btn_mas_historial).setOnClickListener{vm.cargar(true)}
        view.findViewById<View>(R.id.btn_login_historial).setOnClickListener{(requireActivity() as MainActivity).volverAlLogin()}
        viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){vm.estado.collect{e->
            view.findViewById<View>(R.id.progreso_historial).isVisible=e.cargando
            view.findViewById<TextView>(R.id.error_historial).apply{text=e.error.orEmpty();isVisible=e.error!=null}
            view.findViewById<View>(R.id.vacio_historial).isVisible=!e.cargando&&e.error==null&&e.registros.isEmpty()
            view.findViewById<View>(R.id.btn_actualizar_historial).isEnabled=!e.cargando&&!e.sesionRequerida
            view.findViewById<View>(R.id.btn_mas_historial).apply{isVisible=e.siguiente!=null;isEnabled=!e.cargando&&!e.sesionRequerida}
            view.findViewById<View>(R.id.btn_login_historial).isVisible=e.sesionRequerida
            adaptador.submitList(e.registros)
        }}}
    }
    override fun onResume(){super.onResume();vm.verificarSesion()}
    override fun onDestroyView(){view?.findViewById<RecyclerView>(R.id.lista_historial)?.adapter=null;super.onDestroyView()}
}

// Ofrece boletos solo para compras confirmadas; las demas filas no simulan un cobro.
private class HistorialAdapter(private val abrir:(String)->Unit):ListAdapter<RegistroHistorial,HistorialAdapter.Fila>(object:DiffUtil.ItemCallback<RegistroHistorial>(){
    override fun areItemsTheSame(a:RegistroHistorial,b:RegistroHistorial)=a.id==b.id
    override fun areContentsTheSame(a:RegistroHistorial,b:RegistroHistorial)=a==b
}){
    init{stateRestorationPolicy=RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY}
    class Fila(v:View):RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=Fila(LayoutInflater.from(p.context).inflate(R.layout.item_historial,p,false))
    override fun onBindViewHolder(h:Fila,pos:Int){
        val r=getItem(pos);val v=h.itemView;val c=v.context;v.tag="historial_${r.id}"
        val f=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
        val estado=when(r.estado){EstadoReserva.CONFIRMADA->R.string.historial_confirmada;EstadoReserva.PENDIENTE->R.string.historial_pendiente;EstadoReserva.CANCELADA->R.string.historial_cancelada;EstadoReserva.EXPIRADA->R.string.historial_expirada}
        v.findViewById<TextView>(R.id.historial_estado).setText(estado)
        v.findViewById<TextView>(R.id.historial_ruta).text=c.getString(R.string.mi_boleto_ruta,r.origen,r.destino)
        v.findViewById<TextView>(R.id.historial_fecha).text=c.getString(if(r.pagada)R.string.historial_fecha_compra else R.string.historial_fecha_reserva,f.format(FechaHoraServidor.instante(r.fechaPago?:r.creadoEn)))
        v.findViewById<TextView>(R.id.historial_salida).text=c.getString(R.string.historial_datos_salida,f.format(FechaHoraServidor.instante(r.fechaSalida)),r.asientos.joinToString(", "))
        v.findViewById<TextView>(R.id.historial_importe).text=c.getString(if(r.pagada)R.string.historial_pagado else R.string.historial_sin_pago,if(r.pagada)r.importePagado else r.totalReserva)
        v.findViewById<View>(R.id.btn_boletos_historial).apply{
            isVisible=r.pagada&&r.estado==EstadoReserva.CONFIRMADA&&r.boletosEmitidos==r.cantidad
            tag="boletos_historial_${r.id}";setOnClickListener{if(isVisible)abrir(r.id)}
        }
    }
}
