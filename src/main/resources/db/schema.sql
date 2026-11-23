CREATE DATABASE IF NOT EXISTS sica_acme CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE sica_acme;

-- Columnas originales del esquema académico y códigos estables para los casos de uso.
CREATE TABLE roles (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre_rol VARCHAR(50) UNIQUE NOT NULL,
  codigo VARCHAR(32) UNIQUE
);
CREATE TABLE permisos (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre_permiso VARCHAR(100) UNIQUE NOT NULL,
  descripcion TEXT,
  codigo VARCHAR(32) UNIQUE
);
CREATE TABLE rol_permisos (
  rol_id INT,
  permiso_id INT,
  PRIMARY KEY (rol_id, permiso_id),
  FOREIGN KEY (rol_id) REFERENCES roles(id),
  FOREIGN KEY (permiso_id) REFERENCES permisos(id)
);
CREATE TABLE empresas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  contacto_principal VARCHAR(100),
  nit VARCHAR(32) UNIQUE,
  esta_activa BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE usuarios (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  email VARCHAR(100) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  rol_id INT,
  esta_activo BOOLEAN DEFAULT TRUE,
  empresa_id INT,
  FOREIGN KEY (rol_id) REFERENCES roles(id),
  FOREIGN KEY (empresa_id) REFERENCES empresas(id)
);
CREATE TABLE persona_estados_acceso (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre_estado VARCHAR(50) UNIQUE NOT NULL,
  codigo VARCHAR(24) UNIQUE
);
CREATE TABLE visita_estados (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre_estado VARCHAR(50) UNIQUE NOT NULL,
  codigo VARCHAR(32) UNIQUE
);
CREATE TABLE personas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  documento_identidad VARCHAR(20) UNIQUE NOT NULL,
  empresa_id INT,
  tipo_persona ENUM('Trabajador','Invitado') NOT NULL,
  estado_acceso_id INT,
  url_foto VARCHAR(255),
  FOREIGN KEY (empresa_id) REFERENCES empresas(id),
  FOREIGN KEY (estado_acceso_id) REFERENCES persona_estados_acceso(id),
  INDEX (empresa_id, nombre)
);
CREATE TABLE visitas (
  id INT AUTO_INCREMENT PRIMARY KEY,
  persona_id INT,
  fecha_entrada DATETIME,
  fecha_salida DATETIME,
  estado_visita_id INT,
  vehiculo_placa VARCHAR(10),
  visita_aprobada_por INT,
  empresa_id INT,
  tipo_solicitud ENUM('PREVIA','NO_ANUNCIADA','SIN_CARNET') NOT NULL DEFAULT 'NO_ANUNCIADA',
  inicio_programado DATETIME,
  fin_programado DATETIME,
  solicitado_por_id INT,
  fecha_decision DATETIME,
  motivo_decision VARCHAR(1000),
  qr_hash CHAR(64) UNIQUE,
  persona_dentro INT GENERATED ALWAYS AS (
    CASE WHEN fecha_entrada IS NOT NULL AND fecha_salida IS NULL THEN persona_id END
  ) STORED,
  UNIQUE (persona_dentro),
  FOREIGN KEY (persona_id) REFERENCES personas(id),
  FOREIGN KEY (estado_visita_id) REFERENCES visita_estados(id),
  FOREIGN KEY (visita_aprobada_por) REFERENCES usuarios(id),
  FOREIGN KEY (solicitado_por_id) REFERENCES usuarios(id),
  FOREIGN KEY (empresa_id) REFERENCES empresas(id),
  INDEX (empresa_id, estado_visita_id, inicio_programado),
  INDEX (persona_id, estado_visita_id),
  INDEX idx_visitas_vencimiento (estado_visita_id, fin_programado, id),
  CHECK (fin_programado IS NULL OR inicio_programado IS NULL OR fin_programado > inicio_programado)
);
CREATE TABLE incidentes (
  id INT AUTO_INCREMENT PRIMARY KEY,
  visita_id INT,
  reportado_por_id INT,
  fecha DATETIME NOT NULL,
  descripcion TEXT NOT NULL,
  FOREIGN KEY (visita_id) REFERENCES visitas(id),
  FOREIGN KEY (reportado_por_id) REFERENCES usuarios(id),
  INDEX (fecha)
);
CREATE TABLE bitacora_auditoria (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  usuario_id INT,
  fecha_hora TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  accion_realizada VARCHAR(255) NOT NULL,
  tabla_afectada VARCHAR(100),
  registro_id_afectado INT,
  detalles TEXT,
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  INDEX (fecha_hora)
);
CREATE TRIGGER auditoria_sin_update BEFORE UPDATE ON bitacora_auditoria FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoría solo admite inserciones';
CREATE TRIGGER auditoria_sin_delete BEFORE DELETE ON bitacora_auditoria FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoría solo admite inserciones';

CREATE TABLE notificaciones (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  usuario_id INT NOT NULL,
  visita_id INT NOT NULL,
  evento VARCHAR(32) NOT NULL,
  mensaje VARCHAR(500) NOT NULL,
  fecha DATETIME NOT NULL,
  leida_en DATETIME,
  UNIQUE (usuario_id, visita_id, evento),
  INDEX (usuario_id, leida_en, id),
  FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
  FOREIGN KEY (visita_id) REFERENCES visitas(id)
);
CREATE TABLE migraciones_esquema (
  version VARCHAR(100) PRIMARY KEY,
  aplicada_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  origen VARCHAR(64)
);
