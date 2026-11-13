package com.app.transporte_chota_bus

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.app.transporte_chota_bus.ui.auth.BienvenidaFragment
import com.app.transporte_chota_bus.ui.auth.LoginFragment
import com.app.transporte_chota_bus.ui.inicio.InicioFragment

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ajustarEspacioDelSistema(findViewById(R.id.contenedor_pantallas))

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.contenedor_pantallas, BienvenidaFragment())
                .commit()
        }
    }

    fun abrirPantalla(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.contenedor_pantallas, fragment)
            .addToBackStack(null)
            .commit()
    }

    fun volverAlLogin() {
        if (supportFragmentManager.isStateSaved) return
        supportFragmentManager.popBackStackImmediate(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
        abrirPantalla(LoginFragment())
    }

    // Reinicia la navegación al entrar o salir de una cuenta.
    fun entrarAlInicio() = mostrarRaiz(InicioFragment())

    fun volverABienvenida() = mostrarRaiz(BienvenidaFragment())

    private fun mostrarRaiz(fragment: Fragment) {
        if (supportFragmentManager.isStateSaved) return
        if (supportFragmentManager.findFragmentById(R.id.contenedor_pantallas)?.javaClass == fragment.javaClass) return
        supportFragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.contenedor_pantallas, fragment)
            .commit()
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
