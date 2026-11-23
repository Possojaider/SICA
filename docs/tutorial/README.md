# Videotutorial de SICA

Tutorial narrado en español con capturas reales de JavaFX, datos de prueba,
subtítulos visibles y capítulos. La voz sintética utiliza Paulina de macOS.
Las pantallas se presentan como capturas explicadas, sin grabar el escritorio
personal ni utilizar información de la base operativa.

## Entrega

Los archivos generados están en `entregables/tutorial`:

- `SICA-videotutorial.mp4`: video Full HD, H.264 y audio AAC.
- `SICA-videotutorial.srt`: subtítulos externos.
- `Transcripcion.md`: explicación completa por capítulo.
- `LEEME.md`: índice con tiempos de inicio.
- `capitulos.json`: índice estructurado.
- `portada.png`: imagen de portada.

Incluye preparación, login, cuenta recordada, roles, panel, empresas, usuarios,
personas, restricciones, los cuatro escenarios de acceso, notificaciones,
decisiones con motivos, QR, incidentes, auditoría, reportes, exportación y
funcionamiento interno.

## Generar nuevamente

Requiere macOS con `say` y la voz Paulina instalada, Python y las dependencias:

```sh
python3 -m venv target/tutorial-venv
target/tutorial-venv/bin/pip install -r docs/tutorial/requirements.txt
target/tutorial-venv/bin/python docs/tutorial/generar_video.py
```

Las capturas utilizadas están incluidas en `capturas`. El guion editable está
en `guion.json`. Los archivos intermedios se guardan en `target/tutorial-video`.
Si se modifica el guion o una imagen después de renderizar, eliminar los
intermedios correspondientes antes de volver a generar para evitar reutilizar
una escena anterior.

## Actualizar capturas desde la aplicación

El modo de captura usa una base temporal con los ejemplos del proyecto y la
elimina al finalizar. No modifica la base operativa.

```sh
SICA_TUTORIAL_CAPTURE=true SICA_UI_TEST=true ./scripts/test-mysql -B -ntp \
  '-Dtest=UiRenderingTest#loginAndAllModuleViewsLoadRenderAndQueryRealMySql+allFourRolesSeeTheirAuthorizedNavigation'
```

Este comando produce las capturas en `target/ui`. Copiar a `capturas` las
imágenes que se quieran actualizar. Las imágenes del flujo QR y de la cuenta
recordada se generan con los casos correspondientes de `UiRenderingTest`.
