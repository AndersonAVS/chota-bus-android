# API local de Chota Bus — Sprint 3

Este servidor conecta PostgreSQL con el futuro cliente Retrofit de Android. Firebase conserva autenticacion y perfiles. Esta entrega implementa salud, lectura de terminales y busqueda de salidas; reservas y pagos siguen pendientes.

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
