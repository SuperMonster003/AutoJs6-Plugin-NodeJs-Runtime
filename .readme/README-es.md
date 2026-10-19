<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de runtime nativo Node.js 24.21.0 para AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
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

El plugin AutoJs6 Node.js Runtime proporciona a AutoJs6 un runtime nativo integrado de Node.js 24.21.0 para scripts Node.js y tareas de runtime de plugins. En Android 17 o posterior, permita Dispositivos cercanos antes de activar este plugin en el centro de plugins de AutoJs6. También puede gestionar el permiso de red local en los ajustes del plugin. Sin permiso, el plugin permanece desactivado y el inicio automático se omite sin avisos. El permiso pertenece al plugin y es independiente del permiso de AutoJs6.

******

### Funciones

******

- Proporciona el servicio de plugin `nodejs` con ID de plugin `nodejs` y motor `nodejs`.
- Expone ejecucion sincronica de scripts y precalentamiento del runtime al host mediante `org.autojs.plugin.nodejs.RUNTIME`.
- Se añadieron tiempos límite que cubren la cola y la ejecución de scripts, con el error `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sin límite definido, los scripts pueden seguir ejecutándose indefinidamente
- Devuelve todos los módulos integrados de Node.js tal como Node los exporta, directamente desde el inventario `module.builtinModules` de Node (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` y el resto); solo `worker_threads`, `child_process`, `inspector` y los módulos de red nativos conservan una puerta a nivel de solicitud (desde 1.6.0).
- Depuración local con Inspector en Debug y Release mediante solicitud explícita del anfitrión, localhost y reenvío ADB
- Admite codigo CommonJS/ESM, fuentes de modulos, directorio de trabajo, raiz de sandbox, variables de entorno y resultados stdout/stderr.
- Ejecuta los módulos con los loaders CommonJS / ESM nativos de Node.js (`require`, `import()`, `import.meta`, `require(esm)`, exports / imports de paquetes son nativos); el runtime solo registra `module.registerHooks` para los módulos de runtime `autojs6:*`, las fuentes de módulos aportadas por el host y la barrera de builtins por solicitud (desde 1.6.0).
- Usa el módulo fs nativo de Node.js; la accesibilidad de los archivos la deciden únicamente los permisos de Android de la aplicación del plugin, sin denegación de `/proc`, `/sys` o `/dev` propia del runtime (desde 1.6.0).
- Ejecuta la salida TypeScript suministrada por el host y puede solicitar compilación provider-v3 para `.ts`/`.mts`/`.cts` de proyecto creados durante la ejecución; el envío raw directo siempre falla de forma cerrada porque ya no existe un fallback de borrado ni un conmutador de compatibilidad.
- Proporciona broker de capacidades del host y live bridge con modulos de runtime como `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` y `autojs6:bridge-permissions`.
- Incluye proyectos `sample/nodejs` y el inventario de capacidades de API del host `docs/HOST-API.md`.
- Los metadatos del plugin, las instrucciones de uso, el README y el CHANGELOG estan localizados en espanol, frances, ruso, arabe, japones, coreano, ingles, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwan.
- Incluye el lanzador de terminal multi-llamada `libnodexe.so` y un archivo npm / corepack declarados mediante los meta-data de manifest `NODE_CLI_*`, para que el terminal de AutoJs6 (host 6.8.0+) pueda ejecutar `node`, `npm`, `npx`, `corepack`, `yarn` y `pnpm` en un shell con su propio uid.
- La página de ajustes del plugin permite elegir 1-8 ejecuciones simultáneas (2 por defecto) y un límite de cola (sin límite por defecto); los scripts adicionales esperan en FIFO, los cambios conservan las tareas aceptadas e idleExitMs libera los procesos inactivos
- Inicio de scripts más rápido al evitar inicializaciones Web API innecesarias y activar por defecto la caché de compilación portátil nativa de Node para espacios de trabajo y Workers; reutilización entre copias e invalidación de fuentes modificadas a cargo de Node
- API database/sqlite del anfitrión para Bun y Workers de Node.js, con consultas parametrizadas, transacciones y persistencia.
- Muestras y suscripciones de sensores para Bun y Workers de Node.js, con cancelación y liberación automática de escuchas.
- Shell del anfitrión con flujos, autorización Shizuku y limpieza de procesos para Bun, Node.js y Workers.

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

- Slot de runtime: `node24_21`.
- ID de plugin: `nodejs`, motor: `nodejs`.
- Accion del servicio runtime: `org.autojs.plugin.nodejs.RUNTIME`.
- Bibliotecas nativas de runtime: `libnode.so`, `libautojs6-node.so` y `libnodexe.so`.
- Intl: ICU 78 con datos regionales completos (`--with-intl=full-icu` desde 1.6.0; de 1.5.1 a 1.5.7, small-icu solo en inglés); `Intl`, el formato regional, los escapes de propiedades Unicode en expresiones regulares y la salida de error de Node en stderr coinciden con el Node de escritorio, sin necesidad de `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` y `universal`.
- Sistema de archivos: módulo fs nativo de Node.js; cualquier ruta del dispositivo permitida por los permisos de Android es accesible y el runtime no conserva ninguna denegación propia.
- TypeScript: acepta la salida del host, aplica instantáneas precompiladas y puede solicitar compilación provider v3; los módulos TypeScript sin compilar en disco se cargan con la eliminación de tipos nativa de Node, mientras que una entrada TypeScript sin compilar enviada directamente sigue devolviendo `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM: loader nativo de Node.js con `module.registerHooks`; enlaces vivos, dependencias cíclicas, `import.meta`, URL `data:` y `require(esm)` como en escritorio.
- Capacidades: ejecucion sincronica de scripts, bundle transport, runtime nativo integrado, broker de capacidades del host, host capability live bridge.
- Los flujos de archivos, gzip/deflate/Brotli y la ejecución básica de VM son estables; Inspector (Debug/Release), activado explícitamente, es estable dentro de localhost.
- La ejecución integrada deja de imprimir avisos ExperimentalWarning; conserva los eventos warning, advertencias normales, avisos de obsolescencia y errores. La estabilidad de las API de Node y los valores predeterminados del terminal no cambian.
- Los Workers nativos admiten anidación, MessagePort, stdio, argv, execArgv y precargas heredadas; se eliminan los límites del plugin sobre cantidad y tiempo de arranque, resourceLimits aporta valores predeterminados modificables y WorkerPool no limita por defecto la cola ni la duración de las tareas; Inspector sigue la solicitud y la compilación
- Los scripts principales y Workers cargan addons nativos Android mediante Node; process.dlopen y .node son nativos y no requieren listas propias de firmas o hashes; native-addon incluye tres ABI alineadas a 16 KB y compilación NDK/CMake sin conexión; ABI incorrectas o binarios dañados producen errores Node como ERR_DLOPEN_FAILED, sin incluir NDK ni Python en el dispositivo
- Inspector funciona en Debug y Release con permiso explícito del anfitrión; inspector e inspector/promises conservan los módulos nativos de Node, con Session en scripts y Workers, escucha solo en 127.0.0.1 y sin esperas bloqueantes; sin permiso url() devuelve undefined y se impiden la activación y la creación de Session
- install/update/remove de package_manager aceptan especificaciones npm y ejecutan el npm incluido en el espacio de trabajo; npm.cli/exec/runScript/pack usan la CLI real, con registry/proxy/ciclo de vida según npm y .npmrc del proyecto, salida en tiempo real y códigos de error npm; los archivos CLI se preparan al primer uso y las solicitudes de objeto conservan el contrato del almacén local del anfitrión

******

### Historial De Versiones

******

# v1.21.0

###### 2026/10/19

* `Nuevo` Objetos Android para Node.js, Bun y Workers, con Paint, ColorStateList, colores del tema y URI FileProvider
* `Nuevo` API persistentes de calendario e IntentTask para Node.js y Bun, con tareas diarias, semanales, mensuales, anuales, únicas, de cuenta atrás y de difusión

# v1.20.0

###### 2026/10/19

* `Nuevo` Control del dispositivo y diálogos para Bun y Workers de Node.js, con brillo, volumen y limpieza independiente de ventanas

# v1.19.0

###### 2026/10/19

* `Nuevo` Eventos y notificaciones del anfitrión para Bun y Workers de Node.js, con limpieza independiente de suscripciones y notificaciones

##### Para mas historial de versiones

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-es.md)

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
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
