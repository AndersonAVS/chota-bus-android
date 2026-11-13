# Autenticación y perfiles

Decisión del usuario: Firebase Authentication para identidad y Cloud Firestore para perfiles. SQL se mantiene previsto para rutas, viajes, asientos, pagos y compras. Esta nota alternativa registra el ajuste sin modificar los documentos maestros. El catálogo maestro que solicita usuarios en SQL deberá revisarse académicamente mediante esta equivalencia propuesta, no darse por cumplido por la sola integración de Firestore.

## Configuración en la consola

1. Habilitar Authentication con correo y contraseña.
2. Crear Cloud Firestore Standard en el proyecto correspondiente a app/google-services.json, con base predeterminada (default). Elegir la ubicación según la política del proyecto.
3. Iniciar con acceso restringido y publicar el contenido de firestore.rules desde la pestaña Reglas. No utilizar reglas abiertas de prueba.
4. Ejecutar la aplicación desde Android Studio. El archivo local de reglas no se publica automáticamente.

## Datos y comportamiento

Cada perfil se almacena en usuarios/{uid}: id, nombreCompleto, dni, celular, correo y creadoEn. Nunca se almacena la contraseña en Firestore. La caché del perfil es de memoria para no conservar estos documentos en el disco del dispositivo. Las consultas de perfil requieren respuesta del servidor.

El registro es una operación entre dos servicios y no es atómico. Si la identidad se crea y el perfil falla, la aplicación informa que hay un perfil pendiente. Volver a Crear cuenta con el mismo correo y contraseña permite completar el perfil tras reautenticar, sin sobrescribir uno existente. Si se perdió la sesión, iniciar sesión primero y luego completar el registro siguiendo el mensaje mostrado.

El inicio se habilita únicamente después de comprobar identidad y perfil. La recuperación muestra una respuesta genérica después de la respuesta del servicio. Los formularios impiden envíos repetidos mientras la operación está en curso. Las consultas tienen un tiempo máximo de espera; un vencimiento no prueba que un servicio remoto no haya procesado la solicitud.

## Comprobación manual pendiente

- Crear una cuenta de prueba y verificar identidad y documento con el mismo uid.
- Cerrar sesión, entrar nuevamente y reabrir la aplicación.
- Enviar recuperación y comprobar el enlace recibido en el correo de prueba.
- Probar contraseña errónea, desconexión y falta de permisos.
- Interrumpir el guardado del perfil y comprobar que puede completarse.
- Verificar en el simulador de reglas: acceso propio permitido; perfil ajeno y listado general rechazados; campos adicionales como rol rechazados.

Las pruebas unitarias verifican la coordinación del ViewModel mediante un repositorio de prueba. No sustituyen estas pruebas de servicios reales ni publican reglas en la nube. Términos y política de privacidad siguen pendientes de contenido real; no deben utilizarse datos personales reales en la demostración hasta resolverlos.

## Compatibilidad de dependencias

Se utiliza Firebase BoM 34.7.0 (Authentication 24.0.1 y Firestore 26.0.2) para conservar compatibilidad con Kotlin 2.2.10. BoM 35.2.0 resolvió Authentication 25.0.0, cuya metadata Kotlin 2.4.0 no es compatible con el compilador actual. Una actualización futura debe evaluarse y comprobarse conjuntamente.
