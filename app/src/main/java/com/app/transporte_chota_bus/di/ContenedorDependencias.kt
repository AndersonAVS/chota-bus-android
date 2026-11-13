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
