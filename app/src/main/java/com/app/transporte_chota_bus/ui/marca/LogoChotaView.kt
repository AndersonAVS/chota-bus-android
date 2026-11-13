package com.app.transporte_chota_bus.ui.marca

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.app.transporte_chota_bus.R
import kotlin.math.min
import kotlin.math.roundToInt

class LogoChotaView @JvmOverloads constructor(context:Context,attrs:AttributeSet?=null,defStyleAttr:Int=0):View(context,attrs,defStyleAttr){
    private val logo=requireNotNull(context.getDrawable(R.drawable.logo_chota_bus))
    init{
        contentDescription=context.getString(R.string.logo_chota_descripcion)
        setBackgroundResource(R.drawable.fondo_logo_chota)
        val margen=(4*resources.displayMetrics.density).roundToInt();setPadding(margen,margen,margen,margen)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int){
        val ancho=resolveSize((280*resources.displayMetrics.density).roundToInt(),widthMeasureSpec)
        val alto=resolveSize(((ancho-paddingLeft-paddingRight)/2.85f).roundToInt()+paddingTop+paddingBottom,heightMeasureSpec)
        setMeasuredDimension(ancho,alto)
    }
    // Ajusta solo la ventana de presentacion para omitir margenes blancos; el JPEG original no se modifica.
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val w=logo.intrinsicWidth.toFloat();val h=logo.intrinsicHeight.toFloat()
        val area=RectF(w*0.015f,h*0.335f,w*0.985f,h*0.675f)
        val disponibleW=(width-paddingLeft-paddingRight).toFloat();val disponibleH=(height-paddingTop-paddingBottom).toFloat()
        if(disponibleW<=0||disponibleH<=0)return
        val escala=min(disponibleW/area.width(),disponibleH/area.height())
        val guardado=canvas.save()
        // Recorta al area de la marca y mantiene su proporcion en cabeceras pequenas y vistas grandes.
        val x=paddingLeft+(disponibleW-area.width()*escala)/2
        val y=paddingTop+(disponibleH-area.height()*escala)/2
        canvas.clipRect(x,y,x+area.width()*escala,y+area.height()*escala)
        canvas.translate(x-area.left*escala,y-area.top*escala);canvas.scale(escala,escala)
        logo.setBounds(0,0,w.roundToInt(),h.roundToInt());logo.draw(canvas)
        canvas.restoreToCount(guardado)
    }
}
