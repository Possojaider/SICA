USE sica;
INSERT INTO roles VALUES ('SUPER','Superusuario'),('SUPERVISOR','Supervisor de Seguridad'),
 ('GUARDA','Guarda de Seguridad'),('FUNCIONARIO','Funcionario de Empresa');
INSERT INTO permisos VALUES ('USUARIOS'),('EMPRESAS'),('PERSONAS'),('PERSONAS_LEER'),('PERSONAS_CREAR'),
 ('VISITAS_LEER'),('PRE_REGISTRAR'),('SOLICITAR'),('DECIDIR'),('ACCESO'),('INCIDENTES'),('AUDITORIA'),('REPORTES');
INSERT INTO rol_permisos SELECT 'SUPER', id FROM permisos;
INSERT INTO rol_permisos SELECT 'SUPERVISOR', id FROM permisos WHERE id NOT IN ('USUARIOS','EMPRESAS','DECIDIR','PRE_REGISTRAR');
INSERT INTO rol_permisos SELECT 'GUARDA', id FROM permisos WHERE id IN ('PERSONAS_LEER','PERSONAS_CREAR','VISITAS_LEER','SOLICITAR','ACCESO','INCIDENTES');
INSERT INTO rol_permisos SELECT 'FUNCIONARIO', id FROM permisos WHERE id IN ('PERSONAS_LEER','PERSONAS_CREAR','VISITAS_LEER','PRE_REGISTRAR','DECIDIR','REPORTES');
INSERT INTO persona_estados_acceso VALUES ('HABILITADO','Habilitado'),('RESTRINGIDO','Restringido'),('BAJA','Baja');
INSERT INTO visita_estados VALUES ('PENDIENTE','Pendiente'),('APROBADA','Aprobada'),('RECHAZADA','Rechazada'),
 ('DENTRO','Dentro'),('CERRADA','Cerrada'),('SALIDA_OLVIDADA','Cerrada por Sistema (Salida Olvidada)');
