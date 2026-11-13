package com.app.transporte_chota_bus.ui.compra

import android.graphics.*
import android.graphics.drawable.Drawable

// Dibuja respaldo, apoyabrazos y cojin dentro del boton sin convertir el numero en una imagen.
class AsientoCombiDrawable(private val relleno:Int,private val borde:Int):Drawable(){
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private var transparencia=255
    override fun draw(canvas:Canvas){
        val guardado=canvas.save()
        canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/100f,bounds.height()/100f)
        fun pieza(l:Float,t:Float,r:Float,b:Float,radio:Float){
            p.style=Paint.Style.FILL;p.color=relleno;p.alpha=transparencia;canvas.drawRoundRect(l,t,r,b,radio,radio,p)
            p.style=Paint.Style.STROKE;p.color=borde;p.alpha=transparencia;p.strokeWidth=1.8f;canvas.drawRoundRect(l,t,r,b,radio,radio,p)
        }
        pieza(9f,8f,91f,78f,12f)
        pieza(27f,5f,73f,16f,5f)
        pieza(4f,39f,12f,77f,3f);pieza(88f,39f,96f,77f,3f)
        pieza(9f,72f,91f,92f,7f)
        p.color=borde;p.strokeWidth=1.8f;canvas.drawLine(23f,93f,23f,97f,p);canvas.drawLine(77f,93f,77f,97f,p)
        canvas.restoreToCount(guardado)
    }
    override fun setAlpha(alpha:Int){transparencia=alpha;invalidateSelf()}
    override fun setColorFilter(colorFilter:ColorFilter?){p.colorFilter=colorFilter;invalidateSelf()}
    @Suppress("DEPRECATION") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
