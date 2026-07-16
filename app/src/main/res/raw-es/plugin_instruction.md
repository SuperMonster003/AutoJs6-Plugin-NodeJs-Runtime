# AutoJs6 Node.js Runtime

Este plugin proporciona el runtime nativo Node.js 24.5.0 para AutoJs6.

Instala y activa el plugin en el centro de plugins de AutoJs6, luego inicia un script con:

```js
"nodejs";

console.log(process.version);
```

El slot de runtime es `node24_5`. Admite builds `arm64-v8a`, `armeabi-v7a` y `x86_64`, ademas de un APK `universal`.

Los proyectos de ejemplo estan en `sample/nodejs`.
