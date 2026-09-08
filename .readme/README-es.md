<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de runtime nativo Node.js 24.5.0 para AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Idiomas

******

El README.md actual admite los siguientes idiomas:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-fr.md)
- Español [es] # actual
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### Introduccion

******

El plugin AutoJs6 Node.js Runtime proporciona a AutoJs6 un runtime nativo integrado de Node.js 24.5.0 para scripts Node.js y tareas de runtime de plugins.

******

### Funciones

******

- Proporciona el servicio de plugin `nodejs` con ID de plugin `nodejs` y motor `nodejs`.
- Expone ejecucion sincronica de scripts y precalentamiento del runtime al host mediante `org.autojs.plugin.nodejs.RUNTIME`.
- Se añadieron tiempos límite que cubren la cola y la ejecución de scripts, con el error `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sin límite definido, los scripts pueden seguir ejecutándose indefinidamente
- Se habilitaron `dgram` (UDP) y `http2` de forma predeterminada, con un error explícito de desactivación para `trace_events`
- Se añadió depuración local con `inspector`, activada explícitamente en compilaciones Debug, escuchando solo en localhost y conectándose mediante `adb forward`
- Admite codigo CommonJS/ESM, fuentes de modulos, directorio de trabajo, raiz de sandbox, variables de entorno y resultados stdout/stderr.
- Usa el linker nativo de V8 para entradas ESM y dynamic `import()`, conservando dependencias cíclicas, exports mutables y live bindings reexportados; CommonJS `require(esm)` mantiene su límite de interoperabilidad síncrona.
- Ofrece acceso al sistema de archivos similar al de escritorio, limitado por los permisos Android de la aplicación del plugin; `/proc`, `/sys` y `/dev` siempre se rechazan.
- Ejecuta la salida TypeScript suministrada por el host y puede solicitar compilación provider-v3 para `.ts`/`.mts`/`.cts` de proyecto creados durante la ejecución; el envío raw directo siempre falla de forma cerrada porque ya no existe un fallback de borrado ni un conmutador de compatibilidad.
- Proporciona broker de capacidades del host y live bridge con modulos de runtime como `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` y `autojs6:bridge-permissions`.
- Incluye proyectos `sample/nodejs` y el inventario de capacidades de API del host `docs/HOST-API.md`.
- Los metadatos del plugin, las instrucciones de uso, el README y el CHANGELOG estan localizados en espanol, frances, ruso, arabe, japones, coreano, ingles, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwan.

******

### Uso

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

Instala y activa el plugin en el centro de plugins de AutoJs6, luego inicia scripts Node.js con la directiva `"nodejs";`. Hay mas ejemplos en `sample/nodejs`.

******

### Inicio rápido

******

- **Instalación** — Descargue el APK para su ABI desde [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) (elija `universal` si tiene dudas) e instálelo, o compile localmente con `.\gradlew.bat :app:assembleDebug` e instale desde `app/build/outputs/apk/debug/`. Luego habilite este plugin en el centro de plugins de AutoJs6. En Android 11+, conceda al plugin acceso a todos los archivos si los scripts usan almacenamiento compartido; sin el permiso se espera `EACCES`.
- **Ejecución** — Cree un script en el editor de AutoJs6 cuya primera línea sea `"nodejs";` y escriba el resto como Node.js de escritorio (se admiten CommonJS/ESM, paquetes npm de JS puro y módulos de red integrados). Ejecute: la salida se transmite en vivo y el script puede detenerse en cualquier momento. Los `.ts`/`.mts`/`.cts` sin compilar deben convertirse primero a JavaScript en el host; el plugin no incluye `tsc`.
- **Cuando algo falla** — Los fallos del script imprimen la pila JS más un código de error de una línea (como `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) en la consola; para más detalles inspeccione el registro del proceso del plugin con `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`. La disponibilidad de las API del host está documentada en `docs/HOST-API.md`.

******

### Perfil Del Runtime

******

- Slot de runtime: `node24_5`.
- ID de plugin: `nodejs`, motor: `nodejs`.
- Accion del servicio runtime: `org.autojs.plugin.nodejs.RUNTIME`.
- Bibliotecas nativas de runtime: `libnode.so` y `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` y `universal`.
- Sistema de archivos: se puede acceder a las rutas permitidas por Android; `/proc`, `/sys` y `/dev` son límites estrictos.
- TypeScript: acepta salida del host y puede solicitar compilación provider-v3 de archivos creados durante la ejecución; TypeScript raw directo devuelve `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- Linker ESM: `vm.SourceTextModule` / `vm.SyntheticModule`, con live bindings nativos y dependencias cíclicas.
- Capacidades: ejecucion sincronica de scripts, bundle transport, runtime nativo integrado, broker de capacidades del host, host capability live bridge.

******

### Historial De Versiones

******

# v1.2.0

###### 2026/08/29

* `Nuevo` Se agregó `capabilities()` al facade `mediainfo` y selección explícita de snapshots v1/v2 del plugin a su entrada invocable y `read()`; omitir schema continúa devolviendo el snapshot Node v1 existente propiedad del host
* `Nuevo` Se añadió module-source provider v3 para compilar bajo demanda archivos `.ts/.mts/.cts` creados durante la ejecución mediante PFD acotados por bytes y SHA-256, con un presupuesto de compilación independiente de 30 s, rechazo estable de rutas, enlaces simbólicos y ambigüedad, además de diagnósticos TypeScript y mapeo de pila Source Map del host
* `Nuevo` Se habilitó el acceso al sistema de archivos similar al escritorio dentro de los permisos Android, manteniendo bloqueados `/proc`, `/sys` y `/dev`
* `Nuevo` Se añadieron `accessibility.swipe` y `accessibility.gesture` tras la capacidad dedicada `accessibility.gesture`
* `Nuevo` Se añadieron tiempos límite que cubren la cola y la ejecución de scripts, con el error `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sin límite definido, los scripts pueden seguir ejecutándose indefinidamente
* `Nuevo` Se habilitaron `dgram` (UDP) y `http2` de forma predeterminada, con un error explícito de desactivación para `trace_events`
* `Nuevo` Se añadió depuración local con `inspector`, activada explícitamente en compilaciones Debug, escuchando solo en localhost y conectándose mediante `adb forward`
* `Correccion` TypeScript sin compilar ahora falla de forma cerrada si el host no aporta la salida del compilador; también se mapearon imports dinámicos de snapshot y se normalizaron las pilas generadas/importadas
* `Correccion` Se reemplazó el adaptador ESM parcial basado en snapshots por el linker nativo de V8, corrigiendo exports mutables que no se actualizaban mediante reexports cíclicos
* `Correccion` Se corrigieron las importaciones ESM de las fachadas de compatibilidad de AutoJs6 y el sondeo de sufijos TypeScript inexistentes, manteniendo la prioridad de los paquetes npm instalados
* `Mejora` Se eliminó el fallback legacy de borrado TypeScript basado en regex y su conmutador de solicitud; `.ts/.mts/.cts` raw ahora siempre requieren salida del compilador del host
* `Mejora` Se alinearon el contrato v2 host/plugin, los manifiestos de capacidades y el límite de responsabilidad del runtime solo en el plugin
* `Mejora` Se centralizaron en el repositorio del plugin los ejemplos de Node.js, las declaraciones de TypeScript, el asistente de proyectos, los valores predeterminados del runtime y las comprobaciones de alineación con el host, eliminando los conmutadores Gradle y los recursos de desarrollo duplicados del host
* `Mejora` Se amplió la cobertura npm verificada a 15 paquetes, incorporando axios, express y los paquetes ESM-only nanoid, p-limit y yocto-queue
* `Mejora` El campo de solicitud `executionMode` pasa a determinar el modo del ciclo de vida y el campo sin efecto `runtimeAdapter` queda obsoleto

# v1.1.0

###### 2026/08/18

* `Nuevo` Se añadió streaming en vivo de stdout/stderr y cancelación cooperativa mediante `node::Stop`
* `Nuevo` Se sustituyó el rechazo BUSY por una cola serial limitada a tres esperas y se añadió el ciclo de vida de scripts residentes de larga duración
* `Nuevo` Se habilitaron por defecto los módulos de red nativos de Node, `worker_threads` y `child_process`, y se verificaron diez paquetes npm populares de JavaScript puro
* `Mejora` Se añadieron espacios direct-run y negociación tolerante v1..v2 del provider de fuentes, con códigos de error concisos y pilas JavaScript

# v1.0.0

###### 2026/07/18

* `Nuevo` Se agrego el servicio de plugin de runtime Node.js con ID de plugin `nodejs`, motor `nodejs` y slot de runtime `node24_5`
* `Nuevo` Se proporciono el runtime nativo Node.js 24.5.0 mediante `libnode.so` y `libautojs6-node.so`
* `Nuevo` Ejecucion del runtime Node.js en un proceso persistente independiente que reutiliza el estado Node/V8 global del proceso y crea un isolate y un Environment nuevos para cada ejecucion
* `Nuevo` Se agrego descubrimiento de informacion del plugin mediante `org.autojs.plugin.INFO` e invocacion del runtime mediante `org.autojs.plugin.nodejs.RUNTIME`
* `Nuevo` Se admitio codigo CommonJS/ESM, fuentes de modulos, directorio de trabajo, raiz de sandbox, variables de entorno, resultados stdout/stderr y precalentamiento del runtime
* `Nuevo` Se admitio el transporte de archivo de espacio de trabajo v2 por solicitud, con mapeo explicito de entradas, ejecucion en un espacio privado del plugin, escritura de resultados y manifiestos tombstone de eliminacion, sin escanear el sandbox del host
* `Nuevo` Admision de una unica ejecucion activa sin cola, con contrapresion `ERR_AUTOJS6_NODE_PLUGIN_BUSY` y cancelacion mediante reinicio del proceso
* `Nuevo` Se agrego broker de capacidades del host y live bridge con modulos de runtime como `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` y `autojs6:bridge-permissions`
* `Nuevo` Se agregaron builds APK separados por ABI para `arm64-v8a`, `armeabi-v7a`, `x86_64` y un APK `universal`
* `Nuevo` `libc++_shared.so` empaquetado con las bibliotecas del runtime Node.js en salidas APK separadas y `universal`
* `Nuevo` Se agregaron proyectos `sample/nodejs`, una herramienta de diagnostico del resolver de Node y herramientas de verificacion del plan de build del runtime
* `Nuevo` Se agregaron metadatos del plugin, instrucciones de uso, README y recursos CHANGELOG localizados en espanol, frances, ruso, arabe, japones, coreano, ingles, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwan
* `Correccion` La cancelacion mediante reinicio del proceso podia confirmar una instantanea parcial del espacio de trabajo mientras se retiraba el proceso de runtime
* `Correccion` Los descriptores de archivo del transporte de espacio de trabajo podian filtrarse al fallar la validacion del contrato de solicitud o la materializacion del espacio de trabajo, porque la propiedad no se completaba en todas las rutas de salida
* `Mejora` Metadatos y diagnosticos del contrato R5 para ABI/capacidades, estado del runtime persistente, admision, cancelacion y atribucion al proceso independiente
* `Mejora` Agregados diagnosticos monotonicos por fase para la construccion de la fuente de ejecucion, bootstrap, ejecucion del script, creacion y recuperacion del resultado y limpieza por ejecucion, distinguiendo los estados omitido y no aplicable

##### Para mas historial de versiones

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-es.md)

******

### Build

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Build Release:

```powershell
.\gradlew.bat :app:assembleRelease
```

Los parametros de build vienen de `version.properties`, el SDK minimo actual es 24 y el SDK objetivo es 36.

******

### Estructura De Recursos

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` contiene descripciones localizadas del plugin; `plugin_instruction.md` contiene instrucciones de uso mostradas por el host. README y CHANGELOG se generan desde fuentes JSON con `.python/generate_markdown.py`.

******

### Enlaces

******

- Documentacion de AutoJs6: https://docs.autojs6.com
- Proyecto oficial Node.js: https://github.com/nodejs/node
- Plan de build del runtime Node.js: tools/nodejs/runtime-build/README.md
