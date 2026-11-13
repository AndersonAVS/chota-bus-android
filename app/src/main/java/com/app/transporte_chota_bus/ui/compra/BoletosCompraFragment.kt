package com.app.transporte_chota_bus.ui.compra

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BoletosCompraFragment:Fragment(R.layout.boletos_compra){
    private val viewModel:BoletosCompraViewModel by viewModels{
        BoletosCompraViewModelFactory(ContenedorDependencias.reservas,requireArguments().getString("reserva_id").orEmpty())
    }
    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        view.findViewById<View>(R.id.btn_volver_boletos_compra).setOnClickListener{parentFragmentManager.popBackStack()}
        view.findViewById<View>(R.id.btn_recargar_boletos_compra).setOnClickListener{viewModel.cargar()}
        view.findViewById<View>(R.id.btn_inicio_boletos_compra).setOnClickListener{(requireActivity() as MainActivity).entrarAlInicio()}
        view.findViewById<View>(R.id.btn_login_boletos_compra).setOnClickListener{(requireActivity() as MainActivity).volverAlLogin()}
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){viewModel.estado.collect{mostrar(it)}}
        }
    }
    override fun onResume(){super.onResume();viewModel.verificarSesion()}

    // Dibuja las copias del servidor; el Bundle solo transporta el identificador de compra.
    private fun mostrar(estado:EstadoBoletosCompra){
        val vista=view?:return
        vista.findViewById<View>(R.id.progreso_boletos_compra).isVisible=estado.cargando
        vista.findViewById<TextView>(R.id.error_boletos_compra).apply{text=estado.error.orEmpty();isVisible=estado.error!=null}
        vista.findViewById<View>(R.id.vacio_boletos_compra).isVisible=!estado.cargando&&estado.error==null&&estado.boletos.isEmpty()
        vista.findViewById<View>(R.id.btn_recargar_boletos_compra).isEnabled=!estado.cargando&&!estado.sesionRequerida
        vista.findViewById<View>(R.id.btn_login_boletos_compra).isVisible=estado.sesionRequerida
        val lista=vista.findViewById<LinearLayout>(R.id.lista_boletos_compra)
        lista.removeAllViews()
        val formato=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
        for(b in estado.boletos){
            val fila=layoutInflater.inflate(R.layout.item_boleto_compra,lista,false) as LinearLayout
            fila.tag="boleto_${b.id}"
            fila.findViewById<TextView>(R.id.boleto_codigo).text=getString(R.string.boletos_codigo,b.codigo)
            fila.findViewById<TextView>(R.id.boleto_persona).text=getString(R.string.boletos_persona,b.asiento,b.nombreCompleto,b.dni)
            fila.findViewById<TextView>(R.id.boleto_viaje).text=getString(R.string.boletos_viaje,b.embarque.ciudad,b.llegada.ciudad,
                formato.format(FechaHoraServidor.instante(b.fechaSalida)),formato.format(FechaHoraServidor.instante(b.fechaLlegada)),
                b.embarque.nombre,b.embarque.direccion,b.llegada.nombre,b.llegada.direccion)
            fila.findViewById<TextView>(R.id.boleto_importe).text=getString(R.string.boletos_importe,b.importe)
            fila.findViewById<TextView>(R.id.boleto_emision).text=getString(R.string.boletos_emision,formato.format(FechaHoraServidor.instante(b.emitidoEn)))
            fila.findViewById<View>(R.id.boleto_recuperado).isVisible=b.datosRecuperados
            fila.addView(android.widget.Button(requireContext()).apply{
                text=getString(R.string.mis_boletos_detalle);isAllCaps=false;minHeight=(48*resources.displayMetrics.density).toInt()
                tag="ver_boleto_${b.id}"
                setOnClickListener{if(viewModel.verificarSesion())(requireActivity() as MainActivity).abrirPantalla(DetalleBoletoFragment.crear(b.id))}
            })
            lista.addView(fila)
        }
    }
    companion object{
        fun crear(reservaId:String)=BoletosCompraFragment().apply{arguments=Bundle().apply{putString("reserva_id",reservaId)}}
    }
}
