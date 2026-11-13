package com.app.transporte_chota_bus.ui.compra

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.app.transporte_chota_bus.MainActivity
import com.app.transporte_chota_bus.R
import com.app.transporte_chota_bus.di.ContenedorDependencias
import com.app.transporte_chota_bus.domain.validation.CampoPerfil
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.app.transporte_chota_bus.domain.model.FechaHoraServidor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class PasajerosFragment:Fragment(R.layout.datos_pasajeros){
    private val viewModel:PasajerosViewModel by viewModels{
        PasajerosViewModelFactory(ContenedorDependencias.reservas,requireArguments().getString("reserva_id").orEmpty())
    }
    private data class Campos(val nombre:EditText,val dni:EditText,val celular:EditText)
    private val campos=linkedMapOf<Int,Campos>()
    private var dialogo:AlertDialog?=null
    private var modoAnterior=false
    private var confirmacionAnterior=false

    // La misma pantalla cambia entre formulario y resumen sin trasladar datos personales en argumentos.
    override fun onViewCreated(view:View,savedInstanceState:Bundle?){
        view.findViewById<View>(R.id.btn_ver_boletos_compra).setOnClickListener{
            val e=viewModel.estado.value
            if(!e.ocupada&&!e.sesionRequerida&&e.resumen?.reserva?.estado==com.app.transporte_chota_bus.data.model.EstadoReserva.CONFIRMADA)
                (requireActivity() as MainActivity).abrirPantalla(BoletosCompraFragment.crear(viewModel.reservaId))
        }
        view.findViewById<View>(R.id.btn_inicio_confirmacion).setOnClickListener{(requireActivity() as MainActivity).entrarAlInicio()}
        view.findViewById<View>(R.id.btn_volver_pasajeros).setOnClickListener{volver()}
        view.findViewById<View>(R.id.btn_guardar_pasajeros).setOnClickListener{viewModel.guardar()}
        view.findViewById<View>(R.id.btn_recargar_pasajeros).setOnClickListener{viewModel.cargar()}
        view.findViewById<View>(R.id.btn_ver_resumen).setOnClickListener{viewModel.verResumen()}
        view.findViewById<View>(R.id.btn_pagar_prueba).setOnClickListener{
            val estado=viewModel.estado.value
            if(estado.ocupada||!estado.vigente)return@setOnClickListener
            dialogo?.dismiss()
            dialogo=AlertDialog.Builder(requireContext()).setTitle(R.string.pago_prueba_boton)
                .setMessage(getString(R.string.pago_prueba_confirmar,estado.resumen?.reserva?.total.orEmpty()))
                .setPositiveButton(R.string.pago_prueba_boton){_,_->viewModel.pagarPrueba()}
                .setNegativeButton(R.string.cancelar_seleccion,null).show()
        }
        view.findViewById<View>(R.id.btn_editar_pasajeros).setOnClickListener{viewModel.editarFormulario()}
        view.findViewById<View>(R.id.btn_regresar_asientos).setOnClickListener{if(!viewModel.estado.value.ocupada)parentFragmentManager.popBackStack()}
        view.findViewById<View>(R.id.btn_login_pasajeros).setOnClickListener{(requireActivity() as MainActivity).volverAlLogin()}
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner,object:OnBackPressedCallback(true){override fun handleOnBackPressed(){volver()}})
        viewLifecycleOwner.lifecycleScope.launch{
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED){
                launch{viewModel.estado.collect{mostrar(it)}}
                launch{while(isActive){viewModel.actualizarTiempo();delay(1000)}}
            }
        }
    }
    override fun onResume(){super.onResume();if(viewModel.estado.value.resumen!=null)viewModel.cargar()}

    private fun mostrar(estado:EstadoPasajeros){
        val vista=view?:return
        val resumen=estado.resumen
        val confirmada=resumen?.reserva?.estado==com.app.transporte_chota_bus.data.model.EstadoReserva.CONFIRMADA
        vista.findViewById<View>(R.id.panel_confirmacion_compra).isVisible=confirmada
        vista.findViewById<View>(R.id.btn_ver_boletos_compra).isEnabled=confirmada&&!estado.ocupada&&!estado.sesionRequerida
        vista.findViewById<TextView>(R.id.confirmacion_saldo).text=estado.saldoPrueba?.let{getString(R.string.pago_prueba_saldo,it)}
            ?:estado.errorSaldo?:getString(R.string.pago_prueba_no_disponible)
        vista.findViewById<View>(R.id.vigencia_pasajeros).isVisible=!confirmada
        vista.findViewById<View>(R.id.aviso_reserva_resumen).isVisible=!confirmada
        vista.findViewById<TextView>(R.id.saldo_prueba).text=estado.saldoPrueba?.let{getString(R.string.pago_prueba_saldo,it)}
            ?:estado.errorSaldo?:getString(R.string.pago_prueba_no_disponible)
        vista.findViewById<View>(R.id.btn_pagar_prueba).apply{
            isVisible=!confirmada
            isEnabled=!estado.ocupada&&estado.vigente&&resumen?.puedeContinuar==true&&estado.saldoPrueba!=null
        }
        vista.findViewById<TextView>(R.id.titulo_pasajeros).setText(if(confirmada)R.string.confirmacion_titulo else if(estado.mostrandoResumen)R.string.pasajeros_resumen else R.string.pasajeros_titulo)
        vista.findViewById<View>(R.id.progreso_pasajeros).isVisible=estado.ocupada
        vista.findViewById<TextView>(R.id.error_pasajeros).apply{text=estado.error.orEmpty();isVisible=estado.error!=null}
        vista.findViewById<View>(R.id.btn_login_pasajeros).isVisible=estado.sesionRequerida
        vista.findViewById<View>(R.id.btn_recargar_pasajeros).isEnabled=!estado.ocupada&&!estado.sesionRequerida
        vista.findViewById<View>(R.id.panel_formulario_pasajeros).isVisible=resumen!=null&&!estado.mostrandoResumen&&!confirmada
        vista.findViewById<View>(R.id.panel_resumen_compra).isVisible=resumen!=null&&estado.mostrandoResumen&&!confirmada
        vista.findViewById<View>(R.id.btn_guardar_pasajeros).isEnabled=!estado.ocupada&&estado.vigente
        vista.findViewById<View>(R.id.btn_ver_resumen).apply{
            isVisible=resumen?.datosCompletos==true&&!estado.modificado
            isEnabled=!estado.ocupada
        }
        vista.findViewById<View>(R.id.btn_editar_pasajeros).isEnabled=estado.vigente&&!estado.ocupada
        vista.findViewById<View>(R.id.btn_regresar_asientos).isEnabled=!estado.ocupada
        vista.findViewById<TextView>(R.id.vigencia_pasajeros).text=when{
            estado.pagando->getString(R.string.pago_prueba_proceso)
            confirmada->getString(R.string.pago_prueba_confirmada)
            estado.guardando->getString(R.string.pasajeros_guardando)
            estado.cargando->getString(R.string.pasajeros_cargando)
            resumen==null->""
            estado.vigente->getString(R.string.compra_reserva_activa,estado.segundos/60,estado.segundos%60)
            else->getString(R.string.pasajeros_no_vigente)
        }
        if(resumen==null){
            vista.findViewById<TextView>(R.id.confirmacion_datos).text=""
            campos.clear();vista.findViewById<LinearLayout>(R.id.formularios_pasajeros).removeAllViews()
            for(id in listOf(R.id.resumen_viaje,R.id.resumen_pasajeros,R.id.resumen_total))vista.findViewById<TextView>(id).text=""
            return
        }
        construirFormularios(estado.borradores.map{it.asiento})
        for(p in estado.borradores){
            val fila=campos.getValue(p.asiento)
            for((input,valor)in listOf(fila.nombre to p.nombreCompleto,fila.dni to p.dni,fila.celular to p.celular)){
                if(input.text.toString()!=valor)input.setText(valor)
                input.isEnabled=estado.vigente&&!estado.ocupada
            }
            fila.nombre.error=estado.errores[p.asiento]?.get(CampoPerfil.NOMBRE)
            fila.dni.error=estado.errores[p.asiento]?.get(CampoPerfil.DNI)
            fila.celular.error=estado.errores[p.asiento]?.get(CampoPerfil.CELULAR)
        }
        val formato=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm",Locale.forLanguageTag("es-PE")).withZone(ZoneId.of("America/Lima"))
        vista.findViewById<TextView>(R.id.confirmacion_datos).text=if(confirmada)getString(R.string.confirmacion_datos,
            resumen.embarque.ciudad,resumen.destino.ciudad,formato.format(FechaHoraServidor.instante(resumen.fechaSalida)),
            resumen.reserva.asientos.joinToString(", "),resumen.reserva.total) else ""
        vista.findViewById<TextView>(R.id.resumen_viaje).text=getString(R.string.pasajeros_resumen_viaje,
            resumen.embarque.ciudad,resumen.destino.ciudad,formato.format(FechaHoraServidor.instante(resumen.fechaSalida)),
            formato.format(FechaHoraServidor.instante(resumen.fechaLlegada)),resumen.embarque.nombre,resumen.embarque.direccion,
            resumen.destino.nombre,resumen.destino.direccion)
        vista.findViewById<TextView>(R.id.resumen_pasajeros).text=resumen.pasajeros.joinToString("\n\n"){
            getString(R.string.pasajeros_resumen_persona,it.asiento,it.nombreCompleto,it.dni,it.celular)
        }
        vista.findViewById<TextView>(R.id.resumen_total).text=getString(R.string.compra_importe,resumen.reserva.total)
        vista.findViewById<TextView>(R.id.resumen_condicion).setText(if(confirmada)R.string.pago_prueba_confirmada else if(estado.vigente&&resumen.puedeContinuar)R.string.pasajeros_datos_guardados else R.string.pasajeros_no_continuar)
        if((estado.mostrandoResumen&&!modoAnterior)||(confirmada&&!confirmacionAnterior)){
            vista.clearFocus()
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(vista.windowToken,0)
            vista.findViewById<ScrollView>(R.id.scroll_pasajeros).post{view?.findViewById<ScrollView>(R.id.scroll_pasajeros)?.smoothScrollTo(0,0)}
        }
        modoAnterior=estado.mostrandoResumen
        confirmacionAnterior=confirmada
    }

    // Reutiliza un XML por pasajero y desactiva el guardado automatico de los campos personales.
    private fun construirFormularios(asientos:List<Int>){
        if(campos.keys.toList()==asientos)return
        val contenedor=view?.findViewById<LinearLayout>(R.id.formularios_pasajeros)?:return
        contenedor.removeAllViews();campos.clear()
        for(asiento in asientos){
            val fila=layoutInflater.inflate(R.layout.item_pasajero,contenedor,false)
            fila.tag="pasajero_$asiento"
            fila.findViewById<TextView>(R.id.titulo_item_pasajero).text=getString(R.string.pasajeros_asiento,asiento)
            val refs=Campos(fila.findViewById(R.id.input_pasajero_nombre),fila.findViewById(R.id.input_pasajero_dni),fila.findViewById(R.id.input_pasajero_celular))
            for((input,campo,label)in listOf(Triple(refs.nombre,CampoPerfil.NOMBRE,R.id.label_pasajero_nombre),
                Triple(refs.dni,CampoPerfil.DNI,R.id.label_pasajero_dni),Triple(refs.celular,CampoPerfil.CELULAR,R.id.label_pasajero_celular))){
                input.id=View.generateViewId();input.tag="${campo.name}_$asiento"
                fila.findViewById<TextView>(label).labelFor=input.id
                input.addTextChangedListener(afterTextChanged={viewModel.editar(asiento,campo,it?.toString().orEmpty())})
            }
            campos[asiento]=refs;contenedor.addView(fila)
        }
    }
    private fun volver(){
        val estado=viewModel.estado.value
        if(estado.ocupada){Toast.makeText(requireContext(),R.string.compra_espera,Toast.LENGTH_SHORT).show();return}
        if(estado.resumen?.reserva?.estado==com.app.transporte_chota_bus.data.model.EstadoReserva.CONFIRMADA){
            (requireActivity() as MainActivity).entrarAlInicio();return
        }
        if(estado.mostrandoResumen){viewModel.editarFormulario();return}
        if(!estado.modificado){parentFragmentManager.popBackStack();return}
        dialogo?.dismiss()
        dialogo=AlertDialog.Builder(requireContext()).setMessage(R.string.pasajeros_descartar)
            .setPositiveButton(R.string.compra_salir){_,_->parentFragmentManager.popBackStack()}
            .setNegativeButton(R.string.cancelar_seleccion,null).show()
    }
    override fun onDestroyView(){dialogo?.dismiss();dialogo=null;campos.clear();modoAnterior=false;confirmacionAnterior=false;super.onDestroyView()}
    companion object{
        fun crear(reservaId:String)=PasajerosFragment().apply{arguments=Bundle().apply{putString("reserva_id",reservaId)}}
    }
}
