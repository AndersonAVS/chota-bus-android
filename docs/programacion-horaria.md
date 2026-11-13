# Programacion de rutas cada hora

Actualizacion del 13 de noviembre de 2026 solicitada por el usuario: todas las combinaciones entre Chota, Cajamarca, Jaen y Lajas, con salida desde las 06:00 hasta las 18:00 cada hora exacta. Jaen se conserva como Jaén en el catalogo y la interfaz.

## Rutas disponibles

| Origen | Destinos |
| --- | --- |
| Chota | Cajamarca, Jaén, Lajas |
| Cajamarca | Chota, Jaén, Lajas |
| Jaén | Cajamarca, Chota, Lajas |
| Lajas | Cajamarca, Chota, Jaén |

Son doce rutas dirigidas. La lista ya incluye los regresos: no se agrega otra copia por decir viceversa. No se crean rutas desde una ciudad hacia si misma.

## Horarios y fechas

06:00, 07:00, 08:00, 09:00, 10:00, 11:00, 12:00, 13:00, 14:00, 15:00, 16:00, 17:00 y 18:00, en America/Lima. Son trece salidas diarias por ruta y 156 en conjunto. El limite de 18:00 corresponde a la salida, no a la llegada.

La ventana activa comprende hoy y los siguientes 29 dias. El servidor la completa al iniciar, revisa el cambio de dia de Lima cada minuto y tambien antes de las consultas. La generacion solo se ejecuta una vez por dia por proceso si tiene exito; los fallos permiten reintentar. Fuera de esa ventana no se prometen salidas generadas automaticamente. El calendario de Android conserva su rango anterior, pero la busqueda solo devuelve las salidas que esten programadas.

La busqueda publica excluye horas pasadas y salidas sin asientos suficientes para el grupo. Por eso buscar hoy por la tarde no mostrara las trece horas; para comprobar el horario completo se debe seleccionar manana u otro dia futuro dentro de la ventana. Un viaje agotado o cancelado tampoco se vuelve a habilitar para completar artificialmente la lista.

## Datos conservados y alcance de prueba

Se conservan IDs, horarios, vehiculos y estados de las salidas existentes. Se crean solo las combinaciones y horas que faltan y se completa inventario para salidas sin vehiculo. Las operaciones simultaneas del programador se serializan mediante un bloqueo transaccional para no duplicar la programacion. Las salidas canceladas se respetan y no se sustituyen por otra del mismo horario.

El horario y las rutas proceden de la instruccion del usuario. La tarifa S/ 30, la duracion de tres horas, los quince asientos y los vehiculos por salida permanecen como datos ficticios de desarrollo, pendientes de definir para el servicio real. El modelo asigna un vehiculo DEMO distinto por salida; no representa una planificacion de turnos de la flota fisica. Las direcciones postales mantienen su estado de validacion anterior. No se cambian compras, saldos, pasajeros ni boletos.

El programador no genera flota ficticia si NODE_ENV=production. Una integracion productiva necesitara su catalogo y operacion reales, separados de los datos DEMO.

## Archivos principales

- backend/sql/010_programacion_horaria.sql: funcion programar_salidas_demo, combinaciones, horarios, inventario y preservacion de datos anteriores.
- backend/src/programacion.js: control diario con zona de Lima y ejecucion compartida entre peticiones simultaneas.
- backend/src/server.js: primera generacion, revision periodica y limpieza del temporizador al cerrar.
- backend/src/app.js y backend/src/busqueda.js: renovacion antes de consultar salidas.
- backend/test/programacion.test.js y backend/integration/reservas.pg.js: pruebas del programador y SQL.

No se modificaron los selectores Android: SedesChotaBus ya admite las cuatro ciudades y excluye la seleccion de origen igual al destino. Los resultados consultan la API y reflejan las rutas nuevas al actualizar. Una consulta SQLite sin conexion puede conservar el horario anterior, con el aviso de datos guardados y la compra deshabilitada; reintentar con el servicio activo carga la programacion actual.

## Aplicacion y comprobacion

La migracion 010 ya se aplico en la base local despues de 009. La primera carga completo 4 674 salidas faltantes para alcanzar 4 680 en treinta dias, conservando las seis existentes de la programacion inicial. La comparacion anterior/posterior verifico que reservas, pagos, boletos y salidas asociadas a reservas no cambiaran. El usuario reinicio el servidor para activar la renovacion automatica.

En una reconstruccion del entorno, aplicar 010 despues de preparar el esquema 001/005/007/008/009 y las cuatro sedes DEMO de 002; respetar las guardas de los scripts de coordenadas. No repetir migraciones de creacion en una base existente. La funcion puede repetirse; devuelve el numero de salidas nuevas y no cobra ni emite boletos.

Pruebas aprobadas:

- 93 pruebas unitarias/HTTP de servidor.
- 54 pruebas PostgreSQL/HTTP en un esquema temporal aislado.
- Generacion de doce rutas y trece horas exactas por dia, inventario, llamadas simultaneas sin duplicados y preservacion de una compra pagada con sus boletos.
- Cambio de dia de Lima, recuperacion de errores y bloqueo del generador en produccion.
- Consulta real de las doce rutas en la API reiniciada para el 14/11/2026: trece horarios de 06:00 a 18:00 por ruta y cupos disponibles.

Para probar en Android: Inicio -> elegir cualquier origen y otro destino -> seleccionar manana -> Buscar viajes. No hace falta reinstalar la app para este cambio de datos/servidor. La reserva de asientos sigue comprobando disponibilidad en tiempo real.

Para repetir las pruebas desde Git Bash dentro de backend:

```bash
npm test
npm run test:db
```

Los documentos maestros no se modificaron. Las cifras de la revision integral anterior son historicas; esta guia registra la comprobacion especifica del nuevo horario.
