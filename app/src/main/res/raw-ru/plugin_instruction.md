# AutoJs6 Node.js Runtime

Этот плагин предоставляет нативную среду выполнения Node.js 24.5.0 для AutoJs6.

Установите и включите плагин в центре плагинов AutoJs6, затем запустите скрипт так:

```js
"nodejs";

console.log(process.version);
```

Слот среды выполнения: `node24_5`. Поддерживаются сборки `arm64-v8a`, `armeabi-v7a` и `x86_64`, а также APK `universal`.

Примеры проектов находятся в `sample/nodejs`.
