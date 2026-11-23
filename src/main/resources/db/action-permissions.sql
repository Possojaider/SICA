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
