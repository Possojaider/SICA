# Guion del videotutorial de SICA

## SICA · Guía completa

Bienvenido al videotutorial de SICA, el Sistema Integrado de Control de Acceso para Zona Acme. Veremos cómo utilizar la aplicación, desde el inicio de sesión hasta los reportes y la auditoría. También explicaremos cómo se relacionan las personas, las empresas y las autorizaciones. Este video utiliza capturas reales de la aplicación con datos de prueba. Puedes pausar en cada formulario para seguir los pasos a tu ritmo.

## Antes de comenzar

Antes de abrir SICA, comprueba que Docker Desktop y el servicio MySQL del proyecto estén funcionando. En la carpeta del proyecto puedes iniciar la base con docker compose up, guion d, guion guion wait. Después abre la aplicación SICA punto app, o utiliza el script de inicio del proyecto. Todas las estaciones deben conectarse a la misma base de datos y utilizar la zona horaria configurada. Si aparece un error de conexión, revisa primero estos servicios y la configuración local.

## Iniciar y cerrar sesión

En la pantalla de acceso escribe el correo y la contraseña de una cuenta activa. Pulsa Iniciar sesión y espera la verificación. El menú que aparece depende de los permisos del usuario. Una contraseña incorrecta o una cuenta inactiva impiden entrar. Para salir de tu cuenta utiliza Cerrar sesión, en la parte inferior del menú lateral. El cierre invalida la sesión y vuelve al formulario de acceso.

## Recordar la cuenta

En macOS puedes marcar Recordar usuario y contraseña antes de iniciar sesión. Los datos se guardan en el llavero del sistema después de una autenticación correcta. Al volver a abrir el acceso, el formulario puede completarse automáticamente, pero todavía debes iniciar sesión. Mostrar y Ocultar cambian la visibilidad de la contraseña. Para quitar la copia guardada utiliza Olvidar datos guardados. Si falla el llavero, la aplicación permite continuar con el acceso manual.

## Los cuatro roles

SICA tiene cuatro roles iniciales. El superusuario administra usuarios, empresas y toda la operación. El supervisor gestiona personas, accesos, incidentes, reportes y auditoría. El guarda registra invitados, solicita autorizaciones y controla entradas y salidas. El funcionario trabaja con los invitados, decisiones y reportes de su propia empresa. Los permisos se consultan en MySQL antes de cada operación. Por eso dos usuarios pueden ver menús diferentes, y ocultar un botón nunca sustituye la validación del sistema.

## Panel y navegación

El panel principal muestra personas dentro, solicitudes pendientes vigentes, autorizaciones aprobadas vigentes e incidentes del día. El menú lateral abre los módulos disponibles. En las tablas puedes buscar por los campos del módulo, aplicar fechas y seleccionar una fila antes de ejecutar una acción. Actualizar consulta nuevamente la base de datos. Las pantallas también se actualizan periódicamente, aproximadamente cada cinco segundos más el tiempo de consulta. Las operaciones se procesan en segundo plano para mantener la interfaz utilizable.

## Crear una empresa

Para preparar la operación, el superusuario abre Empresas y pulsa Nuevo. Completa el nombre, el contacto principal y, si corresponde, el NIT. Conserva el estado Activa para que pueda utilizarse como empresa anfitriona. Pulsa Guardar y comprueba que aparezca en la tabla. Para modificarla, selecciona su fila y utiliza Editar. Dar de baja la desactiva y conserva el historial. Una empresa inactiva impide nuevos ingresos asociados a ella.

## Crear usuarios y asignar roles

En Usuarios, pulsa Nuevo y completa correo, nombre, contraseña, rol y estado. La contraseña necesita al menos doce caracteres y admite como máximo setenta y dos bytes. Si eliges Funcionario, debes asignarle una empresa activa. Guarda y verifica el registro. Al editar un usuario, dejar vacía la contraseña conserva la actual. Dar de baja impide que esa cuenta siga operando. El sistema protege tu propia cuenta para evitar que desactives tu sesión administrativa o cambies tu rol accidentalmente.

## Registrar personas

En Personas, pulsa Nuevo para registrar un trabajador o un invitado según tus permisos. Escribe el documento único y el nombre. Los trabajadores deben estar asociados a una empresa. Selecciona el estado de acceso y, si tienes una fotografía, agrega una dirección HTTPS. Guarda y busca el documento para verificar el alta. Un documento duplicado genera un aviso y conserva el formulario para corregirlo. El funcionario registra invitados dentro del alcance de su empresa.

## Editar, restringir y dar de baja

Selecciona una persona en la tabla para editar sus datos o revisar su fotografía. El usuario autorizado puede cambiar el acceso a Restringido para impedir entradas. Dar de baja conserva el registro con estado Baja y mantiene las visitas e incidentes anteriores. Si después necesitas habilitar nuevamente a la persona, un usuario con permisos de gestión debe editar y guardar un estado permitido. La restricción y la empresa se vuelven a comprobar al intentar ingresar, aunque la visita ya estuviera aprobada.

## Escenario A · Visita prevista

Para una visita anunciada, registra primero al invitado. Después abre Registro de visitas y pulsa Registrar visita. Selecciona la persona, la empresa anfitriona y el escenario PREVIA. Indica inicio y fin con el formato que muestra el formulario. La placa es opcional. Al guardar, la visita queda aprobada mediante el permiso de prerregistro. Esa aprobación todavía no significa que la persona esté dentro. El ingreso solo puede registrarse durante el intervalo autorizado.

## Entrada y salida del invitado

El guarda abre Entradas y salidas y busca el documento del visitante. Selecciona la visita aprobada, verifica sus datos y revisa la fotografía si existe. Pulsa Registrar entrada y confirma. El estado cambia a Dentro y se guarda la fecha de entrada. Cuando la persona sale, selecciona esa misma visita y utiliza Registrar salida. El estado pasa a Cerrada y se registra la hora. Una visita cerrada necesita una nueva autorización para un ingreso posterior.

## Escenario B · No anunciado

Si el invitado no estaba anunciado, registra sus datos y crea una visita con el escenario NO ANUNCIADA. Selecciona la empresa que lo recibirá y un intervalo válido. Al guardar, la solicitud queda Pendiente y se generan notificaciones para quienes pueden decidir. El guarda todavía no puede registrar la entrada. La persona debe esperar la aprobación del funcionario autorizado. La actualización periódica permite ver la decisión desde otra instancia conectada a la misma base.

## Aprobar o rechazar

El funcionario entra a Aprobar solicitudes y selecciona una solicitud pendiente de su empresa. Pulsa Aprobar o Rechazar. En ambos casos escribe un motivo obligatorio que explique la decisión. Al guardar se conservan el usuario responsable, la fecha y el motivo. La decisión genera avisos para el solicitante y los usuarios autorizados de acceso. Una solicitud rechazada o vencida impide el ingreso. Si necesitas cambiar una situación ya resuelta, registra una nueva solicitud con sus propios datos y autorización.

## Consultar las notificaciones

Cada usuario dispone de su propia bandeja de notificaciones. Aquí aparecen solicitudes y decisiones relacionadas con sus permisos y su empresa. Selecciona una fila y pulsa Marcar como leída para guardar su lectura. Consultar visita abre los detalles asociados cuando todavía tienes autorización para verlos. La lectura no aprueba la solicitud ni registra una entrada. Esas acciones se realizan en sus módulos correspondientes. El contador del encabezado informa cuántos avisos permanecen sin leer.

## Escenario C · Sin carnet

Cuando un trabajador llega sin carnet, busca su registro existente y crea una solicitud SIN CARNET para su empresa. El inicio debe pertenecer al día actual y el final no puede superar la siguiente medianoche. La solicitud necesita aprobación del funcionario autorizado. Después el guarda registra la entrada dentro de ese intervalo. Este procedimiento no convierte al trabajador en invitado, ni autoriza automáticamente otros días. Si la autorización vence, debe solicitarse una nueva.

## Escenario D · Salida olvidada

Si una persona tiene una visita anterior abierta y presenta una nueva autorización aprobada, el guarda registra la nueva entrada normalmente. SICA detecta la inconsistencia, cierra la visita anterior como Salida olvidada, registra la regularización en auditoría y abre el nuevo ingreso. Todo ocurre dentro de una transacción. Así se evita dejar dos visitas abiertas para la misma persona. La visita nueva debe cumplir sus propias reglas de horario, aprobación y acceso.

## Emitir una credencial QR

Para emitir un QR, selecciona una visita aprobada y vigente en Registro de visitas o Solicitudes. Pulsa Emitir o renovar QR y confirma. Se abre la credencial temporal con su vencimiento, imagen y código. Utiliza Guardar imagen PNG para conservarla y entregarla por el procedimiento de tu organización. Emitir un QR nuevo invalida el anterior. La credencial permite un solo ingreso y no evita las comprobaciones de horario, persona o empresa.

## Registrar entrada con QR

En Entradas y salidas puedes utilizar Ingresar con QR para recibir el código de un lector USB que actúe como teclado, o pegar su texto. También puedes elegir Ingresar con imagen QR y seleccionar un archivo PNG o JPEG. SICA valida la credencial y registra la entrada si todo está autorizado. Un código usado, renovado o vencido se rechaza. Incluso una entrada manual consume el QR pendiente de esa visita. Para registrar la salida se utiliza el control normal de salida.

## Reportar un incidente

Para reportar un incidente desde Entradas y salidas, selecciona la visita y pulsa Reportar incidente. También puedes abrir Incidentes e indicar el identificador de la visita. Describe los hechos de forma concreta y guarda. El sistema agrega automáticamente el usuario que reporta y la fecha. El incidente debe estar asociado a una visita existente. Después puede consultarse y filtrarse según los permisos del usuario, y la operación también queda registrada en auditoría.

## Auditoría y trazabilidad

Auditoría permite revisar quién realizó una acción, cuándo ocurrió y qué tabla o registro fue afectado. Puedes buscar entradas, salidas, aprobaciones, rechazos y cambios de datos, entre otras acciones. La aplicación no permite modificar ni borrar esta bitácora. Las operaciones críticas y su auditoría se guardan en la misma transacción. Si falla el registro obligatorio de auditoría, la operación se cancela para evitar cambios sin trazabilidad.

## Reportes y exportación CSV

En Reportes elige personas dentro, historial, visitas por fecha o decisiones. Incidentes y auditoría aparecen cuando tu cuenta tiene esos permisos. Aplica una búsqueda y, si corresponde, fechas o estado. Los días Desde y Hasta se incluyen en el intervalo. Para descargar el resultado pulsa Exportar CSV y elige la ubicación. La exportación consulta datos reales con tus permisos actuales y conserva los filtros. El archivo puede abrirse en una hoja de cálculo.

## Cómo funciona internamente

Internamente, JavaFX presenta las pantallas y envía las acciones a los casos de uso. La capa de aplicación comprueba sesión, permisos, empresa y reglas del negocio. Un adaptador JDBC consulta y actualiza MySQL mediante transacciones. Las contraseñas se almacenan como hashes BCrypt. Los QR conservan únicamente su huella criptográfica en la base. Los trabajos de base de datos se ejecutan en segundo plano y las consultas periódicas mantienen informadas a las distintas estaciones.

## Resolver avisos frecuentes

Si no puedes entrar, revisa la aprobación, el horario de la visita, el acceso de la persona y el estado de las empresas. Si falta permiso, utiliza una cuenta autorizada o consulta al administrador. Si un formulario informa un duplicado, busca primero el documento o correo existente. Si no conecta con MySQL, comprueba el servicio y la configuración local. Cuando cambien tus permisos, la interfaz se actualizará; si la pantalla actual deja de estar autorizada, volverá a Notificaciones.

## Recorrido recomendado

Para practicar, sigue este orden. Prepara una empresa y las cuentas de trabajo. Registra una persona. Crea una visita prevista o una solicitud pendiente. Si corresponde, espera la aprobación con su motivo. Verifica la identidad, registra la entrada y luego la salida. Finalmente consulta las notificaciones, los incidentes, la auditoría y los reportes. Con este recorrido conoces el funcionamiento completo de SICA y las responsabilidades principales de cada rol.
