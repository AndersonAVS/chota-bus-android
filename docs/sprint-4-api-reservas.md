# Sprint 4 — API de reservas y autenticacion Firebase

## Flujo para la exposicion

1. Android iniciara sesion en Firebase como hasta ahora y obtendra un ID token con currentUser.getIdToken(false). Lo enviara en Authorization: Bearer seguido del token. No se enviaran contrasenas al servidor Node.
2. autenticacion.js usa Firebase Admin para verificar el token del proyecto chota-bus. Solo el UID de la identidad verificada se utiliza como propietario.
3. reservas-routes.js valida entradas y rechaza campos controlados por el servidor, incluyendo UID, estado, precio y total.
4. reservas-repository.js consulta asientos y ejecuta las funciones SQL del modelo anterior. Escritura y lectura usan la misma conexion y transaccion. Ante un fallo se revierte y se libera la conexion.
5. PostgreSQL decide disponibilidad, vencimiento e importes. Las respuestas no incluyen el UID de ningun usuario. Una reserva ajena y una inexistente devuelven el mismo 404.

Los archivos tienen comentarios generales por bloque. Este incremento solo modifica el servidor: Android todavia no envia estos tokens a la API ni presenta la seleccion real de asientos. No se implementan pasajeros, pagos o boletos en este paso.

## Configuracion local

backend/.env incorpora FIREBASE_PROJECT_ID=chota-bus, que es un identificador publico, no una clave privada. .env permanece fuera de Git. Se usa Firebase Admin 13.6.0 para Node 20; Admin 14.5.1 requiere Node >=22 segun el registro npm. El override de @firebase/database-compat=2.1.0 mantiene esa dependencia transitiva y su database 1.1.0 compatibles con Node 20. Esta API solo importa app y auth del SDK; no usa Realtime Database.

Para verificar firma, emisor, audiencia y vencimiento mediante verifyIdToken(token,false), la implementacion proporciona el projectId y usa certificados publicos. No descarga claves de cuenta de servicio ni realiza operaciones administrativas de cuentas. No usa getUser, emision de tokens o comprobacion de revocacion. La comprobacion inmediata de usuarios deshabilitados/tokens revocados queda fuera de este incremento; requeriria operaciones adicionales y credenciales administrativas. Firebase controla la vigencia normal del ID token. Los errores de token invalido/vencido son 401; un fallo temporal al verificar se presenta como 503.

Si FIREBASE_AUTH_EMULATOR_HOST esta definido, el verificador de este servidor rechaza la configuracion. Las pruebas validas usan una dependencia inyectada exclusivamente dentro del proceso de pruebas, sin activar un bypass en server.js. La inyeccion no se puede seleccionar desde una peticion HTTP.

## Contrato de endpoints

Todos los endpoints siguientes requieren Authorization: Bearer ID_TOKEN. No pegar tokens reales en documentos, capturas, Git o chats. Las respuestas incluyen Cache-Control: no-store.

| Metodo | Ruta | Uso |
| --- | --- | --- |
| GET | /api/v1/salidas/:id/asientos | Inventario y disponibilidad sin datos privados |
| POST | /api/v1/reservas | Crear o recuperar el mismo intento de reserva |
| GET | /api/v1/reservas/:id | Consultar una reserva propia |
| POST | /api/v1/reservas/:id/cancelacion | Cancelar una reserva propia pendiente |

Crear reserva recibe Content-Type: application/json y un cuerpo como:

```json
{
  "salidaId": 1,
  "asientos": [1, 2],
  "solicitudId": "00000000-0000-4000-8000-000000000001"
}
```

El UUID mostrado es ilustrativo. Android debe generar uno nuevo por intento de compra y conservarlo al reintentar una respuesta incierta. Cambiar de datos o iniciar otra compra requiere un UUID nuevo; reusar la misma solicitud no revive una reserva cancelada o expirada. El servidor devuelve 200 tanto en creacion como en reintento, con datos.id y estado del mismo recurso. No es una compra pagada.

Los asientos deben ser numeros enteros positivos, sin repetidos, de 1 a 9 elementos; no necesariamente consecutivos. salidaId es entero positivo de 32 bits. Los ids de reserva y solicitud son UUID. El cuerpo esta limitado a 8 KiB. Cancelar acepta cuerpo vacio o JSON vacio {}. Cualquier precio, UID o campo extra se rechaza. Enlaces y campos del catalogo publico anterior no cambian.

GET asientos devuelve datos.salida_id, estado, fecha_hora_salida, precio_unitario (cadena decimal), moneda PEN, hora_servidor, asientos_totales, asientos_libres y asientos con numero/fila/columna/disponible. Una salida pasada o cancelada presenta disponibilidad falsa; una salida sin inventario presenta lista vacia. La lectura es una fotografia del momento, no bloquea el asiento: la reserva vuelve a comprobarlo atomicamente.

La reserva devuelve id, salida_id, estado, cantidad, precio_unitario, total (cadena decimal), moneda PEN, creado_en, vence_en, hora_servidor y asientos. Las fechas son ISO. hora_servidor permite calcular el tiempo restante sin confiar en el reloj del telefono. Una reserva pendiente vencida se presenta como EXPIRADA incluso antes de la siguiente limpieza SQL.

## Estados y errores

- 400: datos o JSON invalidos.
- 401: falta sesion o el token no es valido.
- 404: salida inexistente o reserva inexistente/ajena.
- 409: asiento ocupado, salida cerrada, reuso de solicitud con otros datos o cancelacion no permitida.
- 413: cuerpo demasiado grande.
- 415: formato de cuerpo no admitido.
- 503: servicio de base de datos o verificacion no disponible.

Los errores de negocio se traducen mediante una lista conocida. No se devuelve SQL, stack, UID, encabezado Authorization ni cuerpo de errores de Firebase. No se agrega una operacion de confirmacion: CONFIRMADA queda para un futuro pago verificado en el servidor.

## Verificacion y siguiente paso manual

En Git Bash, dentro de backend: npm test (HTTP, validadores, middleware, rechazo real del SDK a otro proyecto/algoritmo, rollback); npm run test:db (SQL y HTTP reales en esquema aislado). Las identidades positivas de estas pruebas estan simuladas mediante la dependencia inyectada. Aun no se valido una peticion positiva con el ID token real del telefono: se hara al conectar la pantalla Android. Las pruebas no crean reservas en public.

Para activar el codigo nuevo: detener el servidor anterior con Ctrl+C y ejecutar npm start. Abrir /api/v1/salidas/1/asientos en un navegador normal debe devolver 401 SESION_REQUERIDA, porque no envia un ID token. Es una comprobacion esperada del control de acceso, no un fallo de PostgreSQL. /api/v1/salud y la busqueda publica siguen disponibles.

El proximo incremento conectara Retrofit con un proveedor de ID token de la sesion actual y la pantalla XML de seleccion. El token se obtendra en memoria, sin registrarlo ni guardarlo en DataStore. Se validara el flujo completo con una sesion real, incluyendo 401, asiento perdido por concurrencia, vencimiento, reintento y cancelacion.

Referencias oficiales: https://firebase.google.com/docs/auth/admin/verify-id-tokens ; https://github.com/firebase/firebase-admin-node/blob/main/src/auth/token-verifier.ts ; https://www.postgresql.org/docs/current/explicit-locking.html


Actualizacion del cliente: Android ya conecta la seleccion y las reservas temporales con el ID token. Los detalles, limites y pruebas se documentan en sprint-4-android-asientos.md. La validacion positiva en dispositivo se registra en esa guia, sin copiar tokens o credenciales.


Actualizacion: pasajeros y resumen ya estan implementados y verificados. El contrato, procedimiento y evidencias actuales se encuentran en sprint-4-pasajeros-resumen.md. El pago y la emision de boletos siguen pendientes.
