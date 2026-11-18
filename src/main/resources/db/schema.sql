CREATE DATABASE IF NOT EXISTS sica CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE sica;

CREATE TABLE roles (id VARCHAR(32) PRIMARY KEY, nombre VARCHAR(100) NOT NULL UNIQUE);
CREATE TABLE permisos (id VARCHAR(32) PRIMARY KEY);
CREATE TABLE rol_permisos (
  rol VARCHAR(32) REFERENCES roles(id), permiso VARCHAR(32) REFERENCES permisos(id),
  PRIMARY KEY (rol, permiso),
  FOREIGN KEY (rol) REFERENCES roles(id), FOREIGN KEY (permiso) REFERENCES permisos(id)
);
CREATE TABLE empresas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, nit VARCHAR(32) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE usuarios (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, correo VARCHAR(254) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL, hash VARCHAR(100) NOT NULL, activo BOOLEAN NOT NULL DEFAULT TRUE,
  rol VARCHAR(32) NOT NULL, empresa BIGINT,
  FOREIGN KEY (rol) REFERENCES roles(id), FOREIGN KEY (empresa) REFERENCES empresas(id),
  CHECK (rol <> 'FUNCIONARIO' OR empresa IS NOT NULL)
);
CREATE TABLE persona_estados_acceso (id VARCHAR(24) PRIMARY KEY, nombre VARCHAR(100) NOT NULL);
CREATE TABLE personas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, documento VARCHAR(40) NOT NULL UNIQUE,
  nombre VARCHAR(150) NOT NULL, tipo ENUM('TRABAJADOR','INVITADO') NOT NULL,
  empresa BIGINT, foto VARCHAR(2048), estado VARCHAR(24) NOT NULL,
  FOREIGN KEY (empresa) REFERENCES empresas(id), FOREIGN KEY (estado) REFERENCES persona_estados_acceso(id),
  CHECK (tipo <> 'TRABAJADOR' OR empresa IS NOT NULL),
  INDEX (empresa, nombre)
);
CREATE TABLE visita_estados (id VARCHAR(32) PRIMARY KEY, nombre VARCHAR(100) NOT NULL);
CREATE TABLE visitas (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, persona BIGINT NOT NULL, empresa BIGINT NOT NULL,
  tipo ENUM('PREVIA','NO_ANUNCIADA','SIN_CARNET') NOT NULL, estado VARCHAR(32) NOT NULL,
  inicio DATETIME NOT NULL, vence DATETIME NOT NULL, entrada DATETIME, salida DATETIME,
  solicitado_por BIGINT NOT NULL, decidido_por BIGINT, decidido_en DATETIME,
  persona_dentro BIGINT GENERATED ALWAYS AS (CASE WHEN estado = 'DENTRO' THEN persona END) STORED,
  UNIQUE (persona_dentro), INDEX (empresa, estado, inicio), INDEX (persona, estado),
  FOREIGN KEY (persona) REFERENCES personas(id), FOREIGN KEY (empresa) REFERENCES empresas(id),
  FOREIGN KEY (estado) REFERENCES visita_estados(id),
  FOREIGN KEY (solicitado_por) REFERENCES usuarios(id), FOREIGN KEY (decidido_por) REFERENCES usuarios(id),
  CHECK (vence > inicio)
);
CREATE TABLE incidentes (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, visita BIGINT NOT NULL, reportado_por BIGINT NOT NULL,
  fecha DATETIME NOT NULL, descripcion VARCHAR(2000) NOT NULL,
  FOREIGN KEY (visita) REFERENCES visitas(id), FOREIGN KEY (reportado_por) REFERENCES usuarios(id),
  INDEX (fecha)
);
CREATE TABLE bitacora_auditoria (
  id BIGINT PRIMARY KEY AUTO_INCREMENT, usuario BIGINT, fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  accion VARCHAR(40) NOT NULL, tabla_afectada VARCHAR(40) NOT NULL,
  entidad BIGINT, detalles VARCHAR(1000) NOT NULL, INDEX (fecha),
  FOREIGN KEY (usuario) REFERENCES usuarios(id)
);
CREATE TRIGGER auditoria_sin_update BEFORE UPDATE ON bitacora_auditoria FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoría solo admite inserciones';
CREATE TRIGGER auditoria_sin_delete BEFORE DELETE ON bitacora_auditoria FOR EACH ROW
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La auditoría solo admite inserciones';
