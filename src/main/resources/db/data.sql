USE sica_acme;
INSERT INTO roles (nombre_rol,codigo) VALUES
 ('Superusuario','SUPER'),('Supervisor de Seguridad','SUPERVISOR'),
 ('Guarda de Seguridad','GUARDA'),('Funcionario de Empresa','FUNCIONARIO')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO permisos (nombre_permiso,descripcion,codigo) VALUES
 ('gestionar_usuarios','Crear, consultar, actualizar y desactivar usuarios','USUARIOS'),
 ('gestionar_empresas','Administrar las empresas del complejo','EMPRESAS'),
 ('gestionar_personas','Actualizar personas y restricciones de acceso','PERSONAS'),
 ('consultar_personas','Buscar trabajadores e invitados','PERSONAS_LEER'),
 ('crear_persona','Registrar personas según el alcance del rol','PERSONAS_CREAR'),
 ('consultar_visitas','Consultar visitas y solicitudes','VISITAS_LEER'),
 ('prerregistrar_invitado','Autorizar una visita anticipada','PRE_REGISTRAR'),
 ('registrar_visita','Crear solicitudes de visitantes y trabajadores sin carnet','SOLICITAR'),
 ('aprobar_rechazar_visita','Resolver solicitudes con un motivo','DECIDIR'),
 ('registrar_entrada_salida','Registrar entradas, salidas y regularizaciones','ACCESO'),
 ('registrar_consultar_incidentes','Registrar y consultar incidentes','INCIDENTES'),
 ('generar_reporte_auditoria','Consultar la bitácora de auditoría','AUDITORIA'),
 ('generar_reportes','Consultar y exportar informes de operación','REPORTES'),
 ('emitir_qr_visita','Emitir o renovar credenciales QR temporales','QR_EMITIR')
ON DUPLICATE KEY UPDATE id=id;
INSERT IGNORE INTO rol_permisos
 SELECT r.id,p.id FROM roles r CROSS JOIN permisos p WHERE r.codigo='SUPER';
INSERT IGNORE INTO rol_permisos
 SELECT r.id,p.id FROM roles r CROSS JOIN permisos p
 WHERE r.codigo='SUPERVISOR' AND p.codigo NOT IN ('USUARIOS','EMPRESAS','DECIDIR','PRE_REGISTRAR');
INSERT IGNORE INTO rol_permisos
 SELECT r.id,p.id FROM roles r CROSS JOIN permisos p
 WHERE r.codigo='GUARDA' AND p.codigo IN ('PERSONAS_LEER','PERSONAS_CREAR','VISITAS_LEER','SOLICITAR','ACCESO','INCIDENTES');
INSERT IGNORE INTO rol_permisos
 SELECT r.id,p.id FROM roles r CROSS JOIN permisos p
 WHERE r.codigo='FUNCIONARIO' AND p.codigo IN ('PERSONAS_LEER','PERSONAS_CREAR','VISITAS_LEER','PRE_REGISTRAR','DECIDIR','REPORTES','QR_EMITIR');
INSERT INTO persona_estados_acceso (nombre_estado,codigo) VALUES
 ('Activo','HABILITADO'),('Con Prohibicion de Ingreso','RESTRINGIDO'),('Baja','BAJA')
ON DUPLICATE KEY UPDATE id=id;
INSERT INTO visita_estados (nombre_estado,codigo) VALUES
 ('Dentro','DENTRO'),('Fuera','CERRADA'),('Pendiente de Aprobacion','PENDIENTE'),
 ('Aprobado','APROBADA'),('Rechazado','RECHAZADA'),('Expirado','EXPIRADA'),
 ('Cerrada por Sistema (Salida Olvidada)','SALIDA_OLVIDADA')
ON DUPLICATE KEY UPDATE id=id;
INSERT IGNORE INTO migraciones_esquema (version,origen) VALUES ('base-academica-v3',DATABASE());
