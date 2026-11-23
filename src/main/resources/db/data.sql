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

INSERT INTO permisos (nombre_permiso,descripcion,codigo) VALUES
 ('usuarios_leer','Leer usuarios','USUARIOS_LEER'),
 ('usuarios_crear','Crear usuarios','USUARIOS_CREAR'),
 ('usuarios_editar','Editar usuarios','USUARIOS_EDITAR'),
 ('usuarios_baja','Baja usuarios','USUARIOS_BAJA'),
 ('empresas_leer','Leer empresas','EMPRESAS_LEER'),
 ('empresas_crear','Crear empresas','EMPRESAS_CREAR'),
 ('empresas_editar','Editar empresas','EMPRESAS_EDITAR'),
 ('empresas_baja','Baja empresas','EMPRESAS_BAJA'),
 ('trabajadores_crear','Crear trabajadores','TRABAJADORES_CREAR'),
 ('personas_editar','Editar personas','PERSONAS_EDITAR'),
 ('personas_baja','Baja personas','PERSONAS_BAJA')
ON DUPLICATE KEY UPDATE id=id;
INSERT IGNORE INTO rol_permisos (rol_id,permiso_id)
 SELECT rp.rol_id,p.id FROM rol_permisos rp
 JOIN permisos old ON old.id=rp.permiso_id CROSS JOIN permisos p
 WHERE (old.codigo='USUARIOS' AND p.codigo='USUARIOS_LEER') OR (old.codigo='USUARIOS' AND p.codigo='USUARIOS_CREAR') OR (old.codigo='USUARIOS' AND p.codigo='USUARIOS_EDITAR') OR (old.codigo='USUARIOS' AND p.codigo='USUARIOS_BAJA') OR (old.codigo='EMPRESAS' AND p.codigo='EMPRESAS_LEER') OR (old.codigo='EMPRESAS' AND p.codigo='EMPRESAS_CREAR') OR (old.codigo='EMPRESAS' AND p.codigo='EMPRESAS_EDITAR') OR (old.codigo='EMPRESAS' AND p.codigo='EMPRESAS_BAJA') OR (old.codigo='PERSONAS' AND p.codigo='TRABAJADORES_CREAR') OR (old.codigo='PERSONAS' AND p.codigo='PERSONAS_EDITAR') OR (old.codigo='PERSONAS' AND p.codigo='PERSONAS_BAJA');
DELETE rp FROM rol_permisos rp JOIN permisos p ON p.id=rp.permiso_id
 WHERE p.codigo IN ('USUARIOS','EMPRESAS','PERSONAS');
INSERT IGNORE INTO migraciones_esquema (version,origen) VALUES ('permisos-por-accion-v1',DATABASE());
