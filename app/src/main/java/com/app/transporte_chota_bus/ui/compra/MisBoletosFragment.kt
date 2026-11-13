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
import com.app.transporte_chota_bus.data.model.BoletoPrueba
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class MisBoletosFragment:Fragment(R.layout.mis_boletos){
    private val vm:MisBoletosViewModel by viewModels{MisBoletosViewModelFactory(ContenedorDependencias.reservas)}
    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        val adaptador=MisBoletosAdapter{id->if(vm.verificarSesion())(requireActivity() as MainActivity).abrirPantalla(DetalleBoletoFragment.crear(id))}
        view.findViewById<RecyclerView>(R.id.lista_mis_boletos).apply{layoutManager=LinearLayoutManager(requireContext());adapter=adaptador}
        view.findViewById<View>(R.id.btn_volver_mis_boletos).setOnClickListener{parentFragmentManager.popBackStack()}
        view.findViewById<View>(R.id.btn_actualizar_mis_boletos).setOnClickListener{vm.cargar()}
        view.findViewById<View>(R.id.btn_mas_boletos).setOnClickListener{vm.cargar(true)}
        view.findViewById<View>(R.id.btn_login_mis_boletos).setOnClickListener{(requireActivity() as MainActivity).volverAlLogin()}
        viewLifecycleOwner.lifecycleScope.launch{viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){vm.estado.collect{e->
            view.findViewById<View>(R.id.progreso_mis_boletos).isVisible=e.cargando
            view.findViewById<TextView>(R.id.error_mis_boletos).apply{text=e.error.orEmpty();isVisible=e.error!=null}
            view.findViewById<View>(R.id.vacio_mis_boletos).isVisible=!e.cargando&&e.error==null&&e.boletos.isEmpty()
            view.findViewById<View>(R.id.btn_actualizar_mis_boletos).isEnabled=!e.cargando&&!e.sesionRequerida
            view.findViewById<View>(R.id.btn_mas_boletos).apply{isVisible=e.siguiente!=null;isEnabled=!e.cargando&&!e.sesionRequerida}
            view.findViewById<View>(R.id.btn_login_mis_boletos).isVisible=e.sesionRequerida
            adaptador.submitList(e.boletos)
        }}}
    }
    override fun onResume(){super.onResume();vm.verificarSesion()}
    override fun onDestroyView(){view?.findViewById<RecyclerView>(R.id.lista_mis_boletos)?.adapter=null;super.onDestroyView()}
}

// La lista muestra ruta y asiento; los datos personales completos se consultan al abrir el detalle.
private class MisBoletosAdapter(private val abrir:(String)->Unit):ListAdapter<BoletoPrueba,MisBoletosAdapter.Fila>(object:DiffUtil.ItemCallback<BoletoPrueba>(){
    override fun areItemsTheSame(a:BoletoPrueba,b:BoletoPrueba)=a.id==b.id
    override fun areContentsTheSame(a:BoletoPrueba,b:BoletoPrueba)=a==b
}){
    init{stateRestorationPolicy=RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY}
    class Fila(v:View):RecyclerView.ViewHolder(v)
    override fun onCreateViewHolder(p:ViewGroup,t:Int)=Fila(LayoutInflater.from(p.context).inflate(R.layout.item_mi_boleto,p,false))
    override fun onBindViewHolder(h:Fila,pos:Int){
        val b=getItem(pos);val v=h.itemView;val c=v.context
        val fecha=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima")).format(FechaHoraServidor.instante(b.fechaSalida))
        v.findViewById<TextView>(R.id.mi_boleto_ruta).text=c.getString(R.string.mi_boleto_ruta,b.embarque.ciudad,b.llegada.ciudad)
        v.findViewById<TextView>(R.id.mi_boleto_salida).text=c.getString(R.string.mi_boleto_salida,fecha,b.asiento,b.importe)
        v.findViewById<TextView>(R.id.mi_boleto_codigo).text=b.codigo
        v.findViewById<View>(R.id.btn_detalle_mi_boleto).apply{tag="ver_boleto_${b.id}";setOnClickListener{abrir(b.id)}}
    }
}
