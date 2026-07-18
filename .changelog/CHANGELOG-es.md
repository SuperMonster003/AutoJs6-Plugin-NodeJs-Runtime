******

### Historial De Versiones

******

# v1.0.0

###### 2026/07/18

* `Nuevo` Se agrego el servicio de plugin de runtime Node.js con ID de plugin `nodejs`, motor `nodejs` y slot de runtime `node24_5`
* `Nuevo` Se proporciono el runtime nativo Node.js 24.5.0 mediante `libnode.so` y `libautojs6-node.so`
* `Nuevo` Se agrego descubrimiento de informacion del plugin mediante `org.autojs.plugin.INFO` e invocacion del runtime mediante `org.autojs.plugin.nodejs.RUNTIME`
* `Nuevo` Se admitio codigo CommonJS/ESM, fuentes de modulos, directorio de trabajo, raiz de sandbox, variables de entorno, resultados stdout/stderr y precalentamiento del runtime
* `Nuevo` Se agrego broker de capacidades del host y live bridge con modulos de runtime como `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` y `autojs6:bridge-permissions`
* `Nuevo` Se agregaron builds APK separados por ABI para `arm64-v8a`, `armeabi-v7a`, `x86_64` y un APK `universal`
* `Nuevo` Se agregaron proyectos `sample/nodejs`, una herramienta de diagnostico del resolver de Node y herramientas de verificacion del plan de build del runtime
* `Nuevo` Se agregaron metadatos del plugin, instrucciones de uso, README y recursos CHANGELOG localizados en espanol, frances, ruso, arabe, japones, coreano, ingles, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwan
* `Mejora` Agregados diagnosticos monotonicos por fase para la construccion de la fuente de ejecucion, bootstrap, ejecucion del script, creacion y recuperacion del resultado y limpieza por ejecucion, distinguiendo los estados omitido y no aplicable
