package com.app.transporte_chota_bus.di

import android.content.Context
import com.app.transporte_chota_bus.data.local.preferenciasBusquedaDataStore
import com.app.transporte_chota_bus.data.repository.DataStorePreferenciasRepository
import com.app.transporte_chota_bus.data.repository.PreferenciasRepository
import com.app.transporte_chota_bus.data.repository.AutenticacionRepository
import com.app.transporte_chota_bus.data.repository.FirebaseAutenticacionRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings

// Proporciona las dependencias compartidas de autenticación y perfiles.
object ContenedorDependencias {

    // La API protegida comparte la sesion Firebase, pero obtiene el token por peticion.
    val reservas: com.app.transporte_chota_bus.data.repository.ReservasRepository by lazy {
        val auth = FirebaseAuth.getInstance()
        if (com.app.transporte_chota_bus.BuildConfig.DEBUG) {
            val cliente = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build()
            val api = retrofit2.Retrofit.Builder().baseUrl("http://127.0.0.1:3000/")
                .client(cliente).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build().create(com.app.transporte_chota_bus.data.remote.ReservasApi::class.java)
            com.app.transporte_chota_bus.data.repository.RetrofitReservasRepository(api,
                com.app.transporte_chota_bus.data.remote.FirebaseProveedorTokenSesion(auth))
        } else object : com.app.transporte_chota_bus.data.repository.ReservasRepository {
            override suspend fun consultarResumen(id: String) = Result.failure<com.app.transporte_chota_bus.data.model.ResumenCompra>(error())
            override suspend fun guardarPasajeros(id: String, pasajeros: List<com.app.transporte_chota_bus.data.model.PasajeroReserva>) = Result.failure<com.app.transporte_chota_bus.data.model.ResumenCompra>(error())
            override fun usuarioActual() = auth.currentUser?.uid
            private fun error() = com.app.transporte_chota_bus.data.model.ErrorReserva(
                com.app.transporte_chota_bus.data.model.TipoErrorReserva.RED, "El servicio de reservas aún no está configurado para producción.")
            override suspend fun consultarAsientos(salidaId: Int) = Result.failure<com.app.transporte_chota_bus.data.model.InventarioAsientos>(error())
            override suspend fun reservar(salidaId: Int, asientos: List<Int>, solicitudId: String) = Result.failure<com.app.transporte_chota_bus.data.model.ReservaAsientos>(error())
            override suspend fun consultarReserva(id: String) = Result.failure<com.app.transporte_chota_bus.data.model.ReservaAsientos>(error())
            override suspend fun cancelarReserva(id: String) = Result.failure<com.app.transporte_chota_bus.data.model.ReservaAsientos>(error())
        }
    }

    // Usa adb reverse para consultar el servidor de la computadora por USB.
    val viajes: com.app.transporte_chota_bus.data.repository.ViajesRepository by lazy {
        if (com.app.transporte_chota_bus.BuildConfig.DEBUG) {
            val cliente = okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val api = retrofit2.Retrofit.Builder()
                .baseUrl("http://127.0.0.1:3000/")
                .client(cliente)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build().create(com.app.transporte_chota_bus.data.remote.ViajesApi::class.java)
            com.app.transporte_chota_bus.data.repository.RetrofitViajesRepository(api)
        } else object : com.app.transporte_chota_bus.data.repository.ViajesRepository {
            override suspend fun buscar(filtros: com.app.transporte_chota_bus.domain.model.FiltrosViaje) =
                Result.failure<com.app.transporte_chota_bus.data.model.ResultadoViajes>(
                    java.io.IOException("El servicio de viajes aún no está configurado para producción."))
        }
    }

    @Volatile private var viajesGuardados:com.app.transporte_chota_bus.data.repository.ViajesRepository?=null
    fun viajesConCache(context:Context):com.app.transporte_chota_bus.data.repository.ViajesRepository = viajesGuardados?:synchronized(this){
        viajesGuardados?:com.app.transporte_chota_bus.data.repository.ViajesConCacheRepository(viajes,
            com.app.transporte_chota_bus.data.local.CacheViajesSqlite(context.applicationContext)).also{viajesGuardados=it}
    }

    // Utiliza el contexto de aplicación y el archivo compartido de preferencias.
    fun preferencias(context: Context): PreferenciasRepository =
        DataStorePreferenciasRepository(context.applicationContext.preferenciasBusquedaDataStore)

    val autenticacion: AutenticacionRepository by lazy {
        val firestore = FirebaseFirestore.getInstance().apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        FirebaseAutenticacionRepository(FirebaseAuth.getInstance(), firestore)
    }
}
