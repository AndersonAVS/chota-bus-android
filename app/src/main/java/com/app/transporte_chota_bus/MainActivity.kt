package com.app.transporte_chota_bus

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        mostrarPantalla(R.layout.mapa_terminal)
    }

    private fun mostrarPantalla(layout: Int) {
        setContentView(layout)

        val contenedor = findViewById<ViewGroup>(android.R.id.content)
        val vista = contenedor.getChildAt(0)

        ajustarEspacioDelSistema(vista)
    }

    private fun ajustarEspacioDelSistema(vista: View) {
        val izquierda = vista.paddingLeft
        val arriba = vista.paddingTop
        val derecha = vista.paddingRight
        val abajo = vista.paddingBottom

        ViewCompat.setOnApplyWindowInsetsListener(vista) { elemento, insets ->
            val espacios = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                        WindowInsetsCompat.Type.displayCutout()
            )

            elemento.setPadding(
                izquierda + espacios.left,
                arriba + espacios.top,
                derecha + espacios.right,
                abajo + espacios.bottom
            )

            WindowInsetsCompat.CONSUMED
        }

        ViewCompat.requestApplyInsets(vista)
    }
}