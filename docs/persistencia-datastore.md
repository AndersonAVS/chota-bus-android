# Preferencias locales de búsqueda

Actividad de campo de semana 7: persistencia mediante Preferences DataStore y patrón Repository, observada desde un ViewModel. Esta implementación utiliza DataStore 1.1.7, versión que ya resolvían las dependencias del proyecto y compatible con la base Kotlin actual.

## Alcance

Inicio permite elegir origen y destino tocando sus filas. El selector excluye la otra sede y la validación también se aplica en ViewModel y Repository. Intercambiar guarda ambos valores en una sola operación. Las sedes iniciales son Chota, Cajamarca, Jaén y Lajas. Este catálogo de sedes no certifica que exista una salida entre cada combinación; la API del Sprint 3 determinará las rutas y salidas disponibles.

La selección se guarda inmediatamente, antes de implementar la consulta de viajes. Buscar viajes, fecha, cantidad de pasajeros y disponibilidad remota siguen correspondiendo al siguiente bloque de desarrollo.

## Archivos y responsabilidades

- PreferenciasBusqueda: modelo de origen y destino.
- PreferenciasDataStore: única instancia por archivo mediante el delegado de contexto de aplicación.
- PreferenciasRepository: contrato de lectura Flow y guardado.
- DataStorePreferenciasRepository: lectura y escritura atómica de ultimo_origen y ultimo_destino.
- BusquedaViewModel: observación de preferencias, validación y estados de carga, guardado y error.
- InicioFragment: selectores y presentación de valores. La sesión conserva su ViewModel independiente.

## Persistencia y errores

Archivo local: files/datastore/preferencias_busqueda.preferences_pb. Las preferencias son del dispositivo y se conservan al cerrar sesión; no están asociadas a un UID. No se guardan contraseñas, tokens, DNI ni perfiles. Borrar los datos de la aplicación elimina las preferencias; reinstalación y copia de seguridad dependen de la configuración de Android.

La primera lectura sin datos utiliza Chota y Cajamarca. Si el archivo contiene sedes desconocidas o iguales se utilizan los valores iniciales. Un error de lectura muestra un aviso y permite seleccionar nuevamente. Un error de escritura no se presenta como guardado exitoso; se conserva la selección previa. No se bloquea el hilo principal con operaciones de disco.

## Evidencias previstas

Pruebas unitarias del ViewModel para carga, selección, intercambio, validación, errores y envíos repetidos. Pruebas con DataStore real en archivo temporal para guardado, reapertura y recuperación de valores inválidos. Las pruebas no modifican preferencias del teléfono.

Prueba manual: entrar a Inicio; elegir Lajas y Jaén; cerrar y reabrir la app; comprobar la selección; intercambiarla; volver a abrir. Repetir al cerrar e iniciar sesión para verificar el alcance por dispositivo.
