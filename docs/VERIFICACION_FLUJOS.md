# Verificación de flujos de SICA

Fecha: 19 de noviembre de 2026.

Se ejecutaron **56 pruebas, sin fallos, errores ni omisiones**, con Java 21,
MySQL 8.4, JavaFX y el llavero nativo de macOS.

```sh
SICA_UI_TEST=true SICA_KEYCHAIN_TEST=true ./scripts/test-mysql -B -ntp
./scripts/package-desktop -DskipTests
```

El registro completo se encuentra en `target/verification.log`. Los resultados
por prueba están en `target/surefire-reports` y las capturas automáticas en
`target/ui`. Las bases de las pruebas son temporales y se eliminan al finalizar.

## Flujos comprobados

| Flujo | Evidencia | Resultado |
|---|---|---|
| Autenticación, cierre de sesión, cuentas inactivas y sesiones revocadas | AuthorizationTest, MySqlIntegrationTest y prueba nativa | Correcto |
| Recordar, recuperar y olvidar credenciales; mostrar contraseña; fallo del llavero | UiRenderingTest y MacKeychainLoginStoreTest | Correcto |
| Roles, permisos dinámicos y alcance por empresa | AuthorizationTest, MySqlIntegrationTest y navegación de los cuatro roles | Correcto |
| Crear, editar y dar de baja empresas, personas y usuarios | Formularios JavaFX conectados a MySQL; conservación de contraseña al editar | Correcto |
| Invitado prerregistrado y entrada/salida | MySqlIntegrationTest | Correcto |
| Invitado no anunciado, notificaciones, aprobación y rechazo con motivo | MySqlIntegrationTest y flujo desde dos interfaces JavaFX | Correcto |
| Trabajador sin carnet y límites del día autorizado | AccessPolicyTest y MySqlIntegrationTest | Correcto |
| Salida olvidada y prevención de entradas abiertas duplicadas | MySqlIntegrationTest, concurrencia y rollback de auditoría | Correcto |
| Solicitudes rechazadas, vencidas, personas restringidas y empresas inactivas | MySqlIntegrationTest | Se impide el ingreso |
| QR temporal, renovación, uso único y lecturas concurrentes | QrCodecTest y MySqlIntegrationTest | Correcto |
| Emisión y guardado de PNG; ingreso seleccionando ese archivo | Aplicación empaquetada y selectores nativos de macOS | Correcto |
| Incidentes, reportes, filtros, búsquedas y CSV | MySqlIntegrationTest, CsvExporterTest y exportación nativa | Correcto |
| Auditoría inmutable y transacciones ante fallos de auditoría/notificación | MySqlIntegrationTest, con cuenta JDBC restringida | Correcto |
| Dos procesos independientes contra la misma base | Dos JVM: notificación, aprobación, entrada, incidente y salida | Correcto |
| Fotografía descargada por HTTPS y ventana sin recorte | Aplicación empaquetada, imagen pública de prueba y regresión de tamaño | Correcto |
| Esquema académico, datos repetibles e importación del esquema anterior | ProvidedSchemaIntegrationTest | Correcto |

## Fallos corregidos durante esta revisión

1. La revocación del permiso de la pantalla actual impedía completar la consulta
   y actualizar el menú. Ahora se leen los permisos antes de cargar los datos y
   se abre Notificaciones cuando la pantalla deja de estar autorizada.
2. Cambiar el informe durante una consulta podía descartar la nueva selección.
   Ahora se conserva una actualización pendiente y se ignoran resultados de la
   selección anterior. La reconstrucción del menú no dispara consultas anidadas.
3. Un funcionario al que se otorgaba PERSONAS podía trasladar o dar de baja una
   persona de otra empresa. Ahora se comprueba la empresa del registro original
   antes de editarlo o darlo de baja.
4. El diálogo de fotografía calculaba su tamaño antes de descargar la imagen.
   Ahora reserva el espacio desde el inicio, limita la imagen conservando su
   proporción y mantiene visible el botón de cierre.

## Aceptación nativa

Se utilizó `target/desktop-20261119-121013/SICA.app` con una base temporal aislada.
Se comprobaron login/logout, CSV, PNG, ingreso con imagen QR y carga HTTPS.
Se conservaron copias de los archivos exportados en `target/native-reporte.csv`
y `target/native-credencial.png`; se comprobó el contenido CSV y el formato PNG.
La instancia de aceptación se cerró y su base temporal se eliminó.

Esta revisión valida los escenarios enumerados en macOS. El guardado de
credenciales usa el llavero de macOS; en otros sistemas permanece disponible
el inicio de sesión manual. En esa entrega quedaban pendientes la publicación en GitHub y la conversión de los
formularios programáticos a FXML; ambos se abordan en el cierre descrito al final de este informe.

## Videotutorial integrado

Tras incorporar el botón de información y el reproductor se ejecutó nuevamente
la suite completa: 58 pruebas, sin fallos, errores ni omisiones. Las dos pruebas
nuevas verifican que el MP4 y sus 25 capítulos estén incluidos en recursos y
que la interfaz reproduzca, pause, salte a un capítulo, cambie la velocidad,
evite ventanas duplicadas y libere el reproductor al cerrar la ayuda o la sesión.
También se comprobó que el acceso a ayuda está disponible para los cuatro roles.

Se abrió también el paquete `target/desktop-20261119-123848/SICA.app`, se inició
sesión con la cuenta de ejemplo, se utilizó el botón de información y se
confirmaron los fotogramas del video, los subtítulos y el avance del tiempo
dentro del reproductor integrado. Después se pausó, se cerró la ayuda y se
cerró sesión. La prueba utilizó un perfil temporal sin credenciales recordadas.

## Revisión del 23 de noviembre de 2026

Se ejecutó nuevamente la suite completa con `SICA_UI_TEST=true` y
`SICA_KEYCHAIN_TEST=true`: **58 pruebas, cero fallos, cero errores y cero
omisiones**. Registro: `target/verification-20261123.log`.

La ejecución cubrió login/logout, usuarios inactivos, roles y revocación de
permisos, CRUD desde formularios, los cuatro escenarios de acceso, decisiones
con motivo, notificaciones, QR, concurrencia, dos procesos independientes,
incidentes, auditoría, reportes, CSV, fotografías, cuenta recordada y
reproducción/cierre del videotutorial integrado.

Docker estaba detenido al comenzar. Se inició Docker Desktop y el contenedor
MySQL quedó saludable. La cuenta real de aplicación pudo consultar
`sica_acme`: siete usuarios activos, tres empresas activas, seis personas,
cero visitas abiertas y cero personas con visitas abiertas duplicadas.
La inspección de la base operativa fue de solo lectura.

Hay dos autorizaciones antiguas vencidas y ninguna aprobación ni solicitud
pendiente vigente. Para practicar nuevos ingresos hay que crear una visita o
solicitud con fechas vigentes y obtener su aprobación cuando corresponda.
No se extendieron autorizaciones ni se modificaron datos operativos.

Se compararon mediante SHA-256 el video, el índice de capítulos y el FXML de
ayuda incluidos en el paquete `target/desktop-20261119-123848/SICA.app` con
los recursos actuales: coinciden. No se necesitaron cambios de código en esta
revisión. Estas comprobaciones cubren los escenarios automatizados descritos;
no equivalen a una garantía de ausencia de fallos en cualquier entorno.

## Cierre de pendientes funcionales y académicos

El 23 de noviembre se completaron el vencimiento persistido con auditoría y
revocación del QR, la conservación de empresas inactivas al editar, la lectura
y escritura del llavero con un trabajador independiente y espera acotada, y
los formularios definidos en FXML. También se conserva el estado BAJA al
editar una persona archivada.

Los permisos de usuarios, empresas y personas distinguen consulta, creación,
edición y baja. Crear trabajadores tiene su propio permiso adicional. La
migración convierte las asignaciones previas una sola vez y conserva las
revocaciones posteriores. No cambia datos operativos ni contraseñas.

La aplicación se separó en fachada autorizada, servicios de directorio y
visitas, validaciones y política de alcance. Los puertos de persistencia se
dividieron por capacidad y las implementaciones JDBC comparten la conexión
de la unidad transaccional. Navegación, fotografía, formularios y reproducción
de video tienen adaptadores de presentación separados.

La validación completa pasó **68 pruebas, cero fallos, cero errores y cero
omisiones**. Incluye pruebas nuevas de expiración idempotente entre instancias,
rollback cuando falla su auditoría, alcance de empresa, permisos revocables por
acción, migración repetida, edición con empresa inactiva, conservación de BAJA,
lectura del llavero bloqueada y escritura que excede el tiempo de espera.

Se incluyó un workflow de GitHub Actions para compilar y probar con Java 21 y
MySQL 8.4 en Linux. Las pruebas de interfaz y llavero son opcionales por entorno
y se ejecutaron localmente en macOS dentro de la suite completa indicada.
