# API local de Chota Bus — Sprint 3

Este servidor conecta PostgreSQL con el futuro cliente Retrofit de Android. Firebase conserva autenticacion y perfiles. Esta entrega implementa catalogo publico y API autenticada de asientos, reservas, pasajeros y resumen; pagos y boletos siguen pendientes.

## Preparacion en Git Bash

Desde la raiz del proyecto:

```bash
cd backend
npm install
cp .env.example .env
```

Editar `.env` localmente: poner la clave propia de PostgreSQL en PGPASSWORD, y ajustar PGPORT si se instalo con otro puerto. El archivo esta excluido de Git. PGUSER=postgres sirve para la primera comprobacion local; antes de desplegar, crear un usuario exclusivo con permisos minimos. Nunca poner estas credenciales en Android.

```bash
npm start
```

Abrir http://127.0.0.1:3000/api/v1/salud y http://127.0.0.1:3000/api/v1/terminales en el navegador de la computadora. Salud debe indicar conectada. Una lista datos vacia es correcta hasta registrar terminales. PostgreSQL debe contener las tablas terminales, rutas y salidas creadas previamente en pgAdmin.

## Pruebas

`npm test` comprueba contratos HTTP, listas vacias y fallos de consulta con una base simulada. No confirma la conexion al PostgreSQL real: esa comprobacion requiere configurar .env y ejecutar el servidor.

El servidor escucha solo en 127.0.0.1 para esta etapa local. La conexion desde Android se preparara posteriormente; en produccion se necesitara HTTPS y configuracion de despliegue. Los endpoints actuales consultan un catalogo publico y no exponen perfiles.

Siguiente bloque: validar la consulta con PostgreSQL y conectar Retrofit. Los datos DEMO son ficticios y no representan informacion oficial de la empresa.

## Consulta de salidas

`GET /api/v1/salidas?origen=Chota&destino=Cajamarca&fecha=AAAA-MM-DD&pasajeros=1`

Origen y destino son ciudades diferentes (1 a 80 caracteres); fecha valida entre 2000 y 2099; pasajeros entero entre 1 y 9. Los filtros repetidos son invalidos. Respuesta 400 para filtros invalidos, 200 con datos vacios si no hay coincidencias y 503 si falla PostgreSQL. Las consultas son parametrizadas. La busqueda usa el dia calendario de America/Lima, excluye salidas pasadas y registros inactivos o cancelados. Devuelve hasta 100 resultados y hayMas para indicar truncamiento. El precio por pasajero es una cadena decimal en PEN; fechas en formato ISO y terminales anidados embarque/llegada con direccion y coordenadas opcionales.

LIMITACION: capacidad es la capacidad total del vehiculo, no asientos libres. El filtro de pasajeros solo comprueba esa capacidad. disponibilidadConfirmada=false lo indica expresamente. Antes de permitir compras se implementaran reservas, asientos ocupados y control transaccional; esta consulta no garantiza disponibilidad ni reserva un asiento.

### Prueba con datos ficticios

Abrir `backend/sql/002_datos_demo.sql` en Query Tool de pgAdmin, conectado a chota_bus, y ejecutar. Crea terminales con prefijo DEMO, tres rutas desde Chota y dos salidas por ruta para manana (hora de Peru). Todos los precios, duraciones, direcciones y capacidades son ficticios. No se incluyen coordenadas sin validar. El script no elimina registros y evita duplicados en ejecuciones secuenciales; no debe ejecutarse simultaneamente ni en produccion. Repetir otro dia agrega las salidas del nuevo dia de prueba.

La ultima consulta devuelve fecha_de_prueba. Usar ese valor en la URL de salidas; con Chota, Cajamarca y un pasajero deben aparecer dos salidas DEMO. Reiniciar `npm start` despues de modificar el codigo. La aplicacion Android aun no consume estos endpoints.

Referencias: https://node-postgres.com/features/queries y https://www.postgresql.org/docs/current/functions-datetime.html


## Sprint 4: modelo de reservas

Se agregan sql/005_modelo_reservas.sql y sql/006_vehiculos_asientos_demo.sql. La reconstruccion del esquema inicial en una base vacia queda en sql/001_esquema_inicial.sql. Sobre la base existente no repetir 001; 005 se ejecuta una sola vez. Las reservas estan implementadas como operaciones SQL transaccionales, aun sin endpoints HTTP de escritura. La guia completa y las consultas para pgAdmin estan en ../docs/sprint-4-modelo-reservas.md.

`npm run test:db` ejecuta las pruebas de concurrencia y restricciones en un esquema aislado de PostgreSQL, usando .env. No se incluyen en npm test, no crean reservas en public y limpian su propio esquema al finalizar. Mantener las credenciales locales fuera de Git.


## API autenticada de reservas

FIREBASE_PROJECT_ID=chota-bus en .env configura la verificacion del ID token con Firebase Admin. Reiniciar npm start para cargar el nuevo codigo. GET /api/v1/salidas/:id/asientos, POST /api/v1/reservas, GET /api/v1/reservas/:id y POST /api/v1/reservas/:id/cancelacion requieren Authorization: Bearer ID_TOKEN. El catalogo publico anterior sigue disponible. Un navegador sin token obtiene 401 en las rutas protegidas.

No se usa una clave privada para esta verificacion de firma, audiencia y vencimiento; no se comprueba revocacion inmediata en este incremento. El contrato, idempotencia, errores, compatibilidad Node y limites de la verificacion estan en ../docs/sprint-4-api-reservas.md. No se aceptan UID ni precios enviados por Android. La integracion del cliente Android sigue pendiente.


## Pasajeros y resumen

La migracion sql/007_pasajeros_reserva.sql ya se aplico en la base local. Agrega pasajeros por asiento y una operacion de guardado atomica con propietario y vigencia verificados. PUT /api/v1/reservas/:id/pasajeros guarda el formulario completo; GET /api/v1/reservas/:id/resumen devuelve el resumen privado. No aceptar UID o precios del formulario y no registrar cuerpos que contengan datos personales. Los endpoints siguen requiriendo ID token Firebase.

El cliente XML ya implementa formularios por asiento, validaciones, edicion y resumen con total SQL. Guia y evidencia: ../docs/sprint-4-pasajeros-resumen.md. Los siguientes pasos son pago verificado y boleto; guardar pasajeros no realiza cobros ni extiende el plazo de la reserva.
