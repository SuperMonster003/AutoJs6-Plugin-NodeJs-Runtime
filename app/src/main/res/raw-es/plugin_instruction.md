# AutoJs6 Node.js Runtime

Este plugin proporciona el runtime nativo Node.js 26.13.0 para AutoJs6.

Instala y activa el plugin en el centro de plugins de AutoJs6, luego inicia un script con:

```js
"nodejs";

console.log(process.version);
```

El slot de runtime es `node26_13`. Admite builds `arm64-v8a`, `armeabi-v7a` y `x86_64`, ademas de un APK `universal`.

Los proyectos de ejemplo estan en `sample/nodejs`.

La página de ajustes del plugin permite elegir 1-8 ejecuciones simultáneas (2 por defecto) y un límite de cola (sin límite por defecto); los scripts adicionales esperan en FIFO, los cambios conservan las tareas aceptadas e idleExitMs libera los procesos inactivos
