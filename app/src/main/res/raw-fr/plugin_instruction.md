# AutoJs6 Node.js Runtime

Ce plugin fournit le runtime natif Node.js 24.5.0 pour AutoJs6.

Installez et activez le plugin dans le centre de plugins AutoJs6, puis demarrez un script avec:

```js
"nodejs";

console.log(process.version);
```

Le slot de runtime est `node24_5`. Il prend en charge les builds `arm64-v8a`, `armeabi-v7a` et `x86_64`, ainsi qu'un APK `universal`.

Les exemples de projets se trouvent dans `sample/nodejs`.
