USE sica_acme;

INSERT INTO empresas (nombre,contacto_principal,nit) VALUES
 ('Acme Logística','Laura Acme · logistica@acme.example','DEMO-900100001'),
 ('Acme Tecnología','Daniel Acme · tecnologia@acme.example','DEMO-900100002')
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO usuarios (nombre,email,password,rol_id,esta_activo,empresa_id)
VALUES ('Administrador de ejemplo','super@acme.example','$2a$12$fYLSas.cIH.A.fbWf1K7hu5Gs2AS3xrNGG.ryxn.sZa9BUt1SV3fu',(SELECT id FROM roles WHERE codigo='SUPER'),TRUE,NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO usuarios (nombre,email,password,rol_id,esta_activo,empresa_id)
VALUES ('Supervisor de ejemplo','supervisor@acme.example','$2a$12$fYLSas.cIH.A.fbWf1K7hu5Gs2AS3xrNGG.ryxn.sZa9BUt1SV3fu',(SELECT id FROM roles WHERE codigo='SUPERVISOR'),TRUE,NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO usuarios (nombre,email,password,rol_id,esta_activo,empresa_id)
VALUES ('Guarda de ejemplo','guarda@acme.example','$2a$12$fYLSas.cIH.A.fbWf1K7hu5Gs2AS3xrNGG.ryxn.sZa9BUt1SV3fu',(SELECT id FROM roles WHERE codigo='GUARDA'),TRUE,NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO usuarios (nombre,email,password,rol_id,esta_activo,empresa_id)
VALUES ('Funcionario de ejemplo','funcionario@acme.example','$2a$12$fYLSas.cIH.A.fbWf1K7hu5Gs2AS3xrNGG.ryxn.sZa9BUt1SV3fu',(SELECT id FROM roles WHERE codigo='FUNCIONARIO'),TRUE,(SELECT id FROM empresas WHERE nit='DEMO-900100001'))
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('Ana Trabajadora','ACME-T-001',(SELECT id FROM empresas WHERE nit='DEMO-900100001'),'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('Luis Trabajador','ACME-T-002',(SELECT id FROM empresas WHERE nit='DEMO-900100002'),'Trabajador',
 (SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('María Invitada','ACME-I-001',(SELECT id FROM empresas WHERE nit='DEMO-900100001'),'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('Pedro Invitado','ACME-I-002',(SELECT id FROM empresas WHERE nit='DEMO-900100001'),'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('Sofía Invitada','ACME-I-003',(SELECT id FROM empresas WHERE nit='DEMO-900100002'),'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE codigo='HABILITADO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO personas (nombre,documento_identidad,empresa_id,tipo_persona,estado_acceso_id,url_foto)
VALUES ('Invitado Restringido','ACME-I-004',(SELECT id FROM empresas WHERE nit='DEMO-900100002'),'Invitado',
 (SELECT id FROM persona_estados_acceso WHERE codigo='RESTRINGIDO'),NULL)
ON DUPLICATE KEY UPDATE id=id;

INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,inicio_programado,
 fin_programado,fecha_entrada,fecha_salida,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
SELECT p.id,p.empresa_id,'SIN_CARNET',(SELECT id FROM visita_estados WHERE codigo='DENTRO'),
 GREATEST(TIMESTAMP(CURRENT_DATE),DATE_SUB(NOW(),INTERVAL 30 MINUTE)),TIMESTAMP(DATE_ADD(CURRENT_DATE,INTERVAL 1 DAY)),GREATEST(TIMESTAMP(CURRENT_DATE),DATE_SUB(NOW(),INTERVAL 15 MINUTE)),NULL,(SELECT id FROM usuarios WHERE email='guarda@acme.example'),(SELECT id FROM usuarios WHERE email='funcionario@acme.example'),GREATEST(TIMESTAMP(CURRENT_DATE),DATE_SUB(NOW(),INTERVAL 20 MINUTE)),'Ejemplo de ingreso excepcional aprobado','ACM202'
FROM personas p WHERE p.documento_identidad='ACME-T-001'
 AND NOT EXISTS (SELECT 1 FROM visitas v WHERE v.persona_id=p.id);

INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,inicio_programado,
 fin_programado,fecha_entrada,fecha_salida,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
SELECT p.id,p.empresa_id,'PREVIA',(SELECT id FROM visita_estados WHERE codigo='APROBADA'),
 DATE_SUB(NOW(),INTERVAL 30 MINUTE),DATE_ADD(NOW(),INTERVAL 3 HOUR),NULL,NULL,(SELECT id FROM usuarios WHERE email='guarda@acme.example'),(SELECT id FROM usuarios WHERE email='funcionario@acme.example'),NOW(),'Ejemplo de reunión programada','ACM101'
FROM personas p WHERE p.documento_identidad='ACME-I-001'
 AND NOT EXISTS (SELECT 1 FROM visitas v WHERE v.persona_id=p.id);

INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,inicio_programado,
 fin_programado,fecha_entrada,fecha_salida,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
SELECT p.id,p.empresa_id,'NO_ANUNCIADA',(SELECT id FROM visita_estados WHERE codigo='PENDIENTE'),
 NOW(),DATE_ADD(NOW(),INTERVAL 1 HOUR),NULL,NULL,(SELECT id FROM usuarios WHERE email='guarda@acme.example'),NULL,NULL,NULL,NULL
FROM personas p WHERE p.documento_identidad='ACME-I-002'
 AND NOT EXISTS (SELECT 1 FROM visitas v WHERE v.persona_id=p.id);

INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,inicio_programado,
 fin_programado,fecha_entrada,fecha_salida,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
SELECT p.id,p.empresa_id,'SIN_CARNET',(SELECT id FROM visita_estados WHERE codigo='CERRADA'),
 TIMESTAMP(DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'08:00:00'),TIMESTAMP(DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'18:00:00'),TIMESTAMP(DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'08:15:00'),TIMESTAMP(DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'17:00:00'),(SELECT id FROM usuarios WHERE email='guarda@acme.example'),(SELECT id FROM usuarios WHERE email='super@acme.example'),TIMESTAMP(DATE_SUB(CURRENT_DATE,INTERVAL 1 DAY),'08:00:00'),'Ejemplo de visita histórica cerrada',NULL
FROM personas p WHERE p.documento_identidad='ACME-T-002'
 AND NOT EXISTS (SELECT 1 FROM visitas v WHERE v.persona_id=p.id);

INSERT INTO visitas (persona_id,empresa_id,tipo_solicitud,estado_visita_id,inicio_programado,
 fin_programado,fecha_entrada,fecha_salida,solicitado_por_id,visita_aprobada_por,fecha_decision,motivo_decision,vehiculo_placa)
SELECT p.id,p.empresa_id,'NO_ANUNCIADA',(SELECT id FROM visita_estados WHERE codigo='RECHAZADA'),
 NOW(),DATE_ADD(NOW(),INTERVAL 1 HOUR),NULL,NULL,(SELECT id FROM usuarios WHERE email='guarda@acme.example'),(SELECT id FROM usuarios WHERE email='super@acme.example'),NOW(),'Ejemplo de rechazo por ausencia del anfitrión',NULL
FROM personas p WHERE p.documento_identidad='ACME-I-003'
 AND NOT EXISTS (SELECT 1 FROM visitas v WHERE v.persona_id=p.id);

INSERT INTO incidentes (visita_id,reportado_por_id,fecha,descripcion)
SELECT v.id,u.id,DATE_SUB(NOW(),INTERVAL 1 DAY),'Ejemplo de incidente: revisión de documento deteriorado'
FROM visitas v JOIN personas p ON p.id=v.persona_id JOIN usuarios u ON u.email='supervisor@acme.example'
WHERE p.documento_identidad='ACME-T-002'
 AND NOT EXISTS (SELECT 1 FROM incidentes i WHERE i.visita_id=v.id AND i.descripcion='Ejemplo de incidente: revisión de documento deteriorado');

INSERT INTO notificaciones (usuario_id,visita_id,evento,mensaje,fecha)
SELECT u.id,v.id,'SOLICITUD','Ejemplo: solicitud pendiente de aprobación',NOW()
FROM usuarios u JOIN roles r ON r.id=u.rol_id JOIN visitas v
 JOIN personas p ON p.id=v.persona_id
WHERE u.email IN ('funcionario@acme.example','super@acme.example') AND p.documento_identidad='ACME-I-002'
ON DUPLICATE KEY UPDATE id=notificaciones.id;

INSERT INTO notificaciones (usuario_id,visita_id,evento,mensaje,fecha)
SELECT u.id,v.id,'DECISION','Ejemplo: solicitud rechazada por el anfitrión',NOW()
FROM usuarios u JOIN visitas v JOIN personas p ON p.id=v.persona_id
WHERE u.email='guarda@acme.example' AND p.documento_identidad='ACME-I-003'
ON DUPLICATE KEY UPDATE id=notificaciones.id;

INSERT INTO bitacora_auditoria (usuario_id,accion_realizada,tabla_afectada,detalles)
SELECT id,'CARGAR_DATOS_EJEMPLO','migraciones_esquema','Datos académicos de demostración insertados'
FROM usuarios WHERE email='super@acme.example'
 AND NOT EXISTS (SELECT 1 FROM bitacora_auditoria WHERE accion_realizada='CARGAR_DATOS_EJEMPLO');
