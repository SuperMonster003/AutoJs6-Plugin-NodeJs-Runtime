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
- Se habilitaron `dgram` (UDP) y `http2` de forma predeterminada, con un error explícito de desactivación para `trace_events`
- Se añadió depuración local con `inspector`, activada explícitamente en compilaciones Debug, escuchando solo en localhost y conectándose mediante `adb forward`
- Admite codigo CommonJS/ESM, fuentes de modulos, directorio de trabajo, raiz de sandbox, variables de entorno y resultados stdout/stderr.
- Usa el linker nativo de V8 para entradas ESM y dynamic `import()`, conservando dependencias cíclicas, exports mutables y live bindings reexportados; CommonJS `require(esm)` mantiene su límite de interoperabilidad síncrona.
- Ofrece acceso al sistema de archivos similar al de escritorio, limitado por los permisos Android de la aplicación del plugin; `/proc`, `/sys` y `/dev` siempre se rechazan.
- Ejecuta la salida TypeScript suministrada por el host y puede solicitar compilación provider-v3 para `.ts`/`.mts`/`.cts` de proyecto creados durante la ejecución; el envío raw directo siempre falla de forma cerrada porque ya no existe un fallback de borrado ni un conmutador de compatibilidad.
- Proporciona broker de capacidades del host y live bridge con modulos de runtime como `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` y `autojs6:bridge-permissions`.
- Incluye proyectos `sample/nodejs` y el inventario de capacidades de API del host `docs/HOST-API.md`.
- Los metadatos del plugin, las instrucciones de uso, el README y el CHANGELOG estan localizados en espanol, frances, ruso, arabe, japones, coreano, ingles, chino simplificado, chino tradicional de Hong Kong y chino tradicional de Taiwan.
- Incluye el lanzador de terminal multi-llamada `libnodexe.so` y un archivo npm / corepack declarados mediante los meta-data de manifest `NODE_CLI_*`, para que el terminal de AutoJs6 (host 6.8.0+) pueda ejecutar `node`, `npm`, `npx`, `corepack`, `yarn` y `pnpm` en un shell con su propio uid.

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
- Intl: ICU 78 solo con datos de configuración regional en inglés (`--with-intl=small-icu`); `Intl`, los escapes de propiedades Unicode en expresiones regulares y la salida de error propia de Node en stderr funcionan en el terminal de AutoJs6, y `NODE_ICU_DATA` puede apuntar a un archivo de datos ICU completo.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` y `universal`.
- Sistema de archivos: se puede acceder a las rutas permitidas por Android; `/proc`, `/sys` y `/dev` son límites estrictos.
- TypeScript: acepta salida del host y puede solicitar compilación provider-v3 de archivos creados durante la ejecución; TypeScript raw directo devuelve `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- Linker ESM: `vm.SourceTextModule` / `vm.SyntheticModule`, con live bindings nativos y dependencias cíclicas.
- Capacidades: ejecucion sincronica de scripts, bundle transport, runtime nativo integrado, broker de capacidades del host, host capability live bridge.
- Los flujos de archivos, gzip/deflate/Brotli y la ejecución básica de VM son estables; Debug Inspector, activado explícitamente, es estable dentro de localhost.
- La ejecución integrada deja de imprimir avisos ExperimentalWarning; conserva los eventos warning, advertencias normales, avisos de obsolescencia y errores. La estabilidad de las API de Node y los valores predeterminados del terminal no cambian.

******

### Historial De Versiones

******

# v1.5.5

###### 2026/09/17

* `Correccion` La versión y el resumen del catálogo de capacidades que informa runtimeInfo ahora derivan del runtime kit, corrigiendo los valores obsoletos 1.5.0 que 1.5.4 seguía informando
* `Correccion` worker_threads acepta new Worker(code, { eval: true }) como en Node en lugar de tratar el código como ruta de script
* `Mejora` La ruta de compilación TypeScript del host pasa a stable en el catálogo de capacidades y se eliminan los metadatos retirados de legacy stripping; el catálogo de ciclo de vida solo enumera modos de ejecución ejecutables, quitando la superficie packaged_long_running y los nombres reservados node_sandboxed / worker_computation; los metadatos de transporte, admisión y cancelación coinciden con el runtime real
* `Mejora` Los diagnósticos de autojs6:profile describen el runtime real en lugar de las marcas históricas partial / reserved / deferred: acceso a archivos limitado por Android, el subconjunto de process, los canales de worker, el pool de procesos y las decisiones de WASI / native addon
* `Mejora` Auditoría de ejemplos: packaged-esm, packaged-dynamic-import, require-esm, wasm-basic, wasm-plugin y desktop-parity-suite pasan a stable; las dos suites de paridad ejecutan sus fragmentos en lugar de imprimir texto de catálogo; compile-cache se marca como no aplicable y se elimina el ejemplo package-install, que solo mostraba metadatos
* `Mejora` La carga de módulos sigue el acceso a archivos de Android como en Node: require, import y Worker aceptan rutas absolutas, destinos en directorios superiores y URL file: (/proc, /sys y /dev siguen denegados); la búsqueda en node_modules permanece anclada al espacio de trabajo
* `Mejora` Los mensajes de Worker y los observadores de fs siguen los límites nativos de Node: se eliminan los topes de 64 KB por mensaje y 32 en cola, y las cuotas de 16 observadores / 64 eventos por segundo; solo la memoria de Android los limita
* `Mejora` Dentro de los workers process.exit() termina solo el hilo del worker como en Node y process.getBuiltinModule() sigue la lista de builtins permitidos del worker en lugar de estar deshabilitado
* `Mejora` Los workers resuelven especificadores de paquete sin ruta a través del node_modules del espacio de trabajo como en Node (condiciones exports, main, index, autorreferencia) en lugar de rechazarlos
* `Mejora` El import() dinámico dentro de los workers pasa por el cargador ESM parcial del worker (rutas locales, URL file:, paquetes del espacio de trabajo, builtins permitidos, with { type: "json" }) en lugar de rechazarse
* `Mejora` La muestra host-events pasa a stable tras superar la aceptación manual de tecla física y acceso a notificaciones
* `Mejora` La muestra scheduled-node-task imprime su política de ciclo de vida y puede conservar la tarea programada para una ejecución real de WorkManager; el modo scheduled obtiene regresión del lado del plugin
* `Mejora` El modo de ejecución scheduled pasa a available en el catálogo de capacidades tras una ejecución real del runner programado de WorkManager del host a través del plugin
* `Mejora` media.play() devuelve una sesión de reproducción (pause/resume/seekTo/stop/status) a través del servicio de música de scripts del host, y el nuevo módulo media_store expone un acceso acotado a MediaStore (capabilities/query/get/insert/update/delete/scanFile/exportFile) protegido por las capacidades media.playback / media.library / media.library.mutate; autojs6:compat.media añade los alias estilo Rhino playMusic; ambos requieren la compilación del host correspondiente
* `Mejora` Los ejemplos media-playback / media-library pasan a stable tras la aceptación manual con los proveedores de medios del host ya integrados; la instantánea 1.5.5 del catálogo de capacidades se publica en releases/nodejs-capability-catalog y la tarea de alineación con el host ahora la referencia
* `Mejora` El envoltorio fs elimina sus propias restricciones de opciones: las opciones fs / fd heredado / flags de los streams, watch({ recursive: true }), filtros cp asíncronos (cpSync conserva ERR_INVALID_RETURN_VALUE de Node), patrones glob absolutos / de directorio padre / '!' literal con arrays exclude, type / encoding de readableWebStream y los constructores Stats / Dirent / Dir siguen ahora el Node 24 nativo; filesystemProfile.advancedApis.recursiveWatch informa native
* `Mejora` readdir / opendir recursivos pasan a ser nativos (desaparecen el tope de 4096 entradas y la comprobación realpath por entrada; readdir('/') lista los nombres proc/sys/dev como Node), readlink / chmod / chown / utimes aceptan rutas absolutas y chmod sigue los enlaces simbólicos, y los errores de política fs se reducen a ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (límite duro, compartido con el cargador) / ERR_AUTOJS6_FS_SCOPED_PATH mientras los fallos fs ordinarios conservan solo su código Node
* `Mejora` el runtime elimina sus propios presupuestos de código de módulos (16 MiB por módulo, 64 MiB en total, 8192 módulos, recuento de peticiones al provider), una entrada CommonJS recibe __filename / require.main.filename / process.argv[1] absolutos como en Node con require.main.id '.', y fs.mkdtemp* pasa a ser nativo: devuelve el prefijo tal como lo escribió el llamador más el sufijo en la codificación pedida y ya no rechaza un directorio padre que sea enlace simbólico
* `Mejora` node:sqlite delega las operaciones de archivo del SQL a SQLite nativo: ATTACH con un nombre de archivo literal (incluidos los URI file:) se comprueba con el autorizador de SQLite contra el límite /proc, /sys, /dev en lugar de escanear el texto SQL, los destinos de VACUUM INTO pasan la misma comprobación a través del ATTACH interno de SQLite, los PRAGMA de directorio ya no se interceptan, las cadenas URI file: y la base de datos temporal vacía se abren, y setAuthorizer() se compone con esa comprobación (solo se sigue rechazando ATTACH con un nombre de archivo enlazado o calculado)
* `Mejora` Las cuotas del puente se delegan al host: las llamadas que superan la ventana maxPendingBridgeCalls de autojs6:bridge-limits ahora esperan en orden de llegada en lugar de fallar con ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT, el runtime ya no limita por su cuenta el número de handles de imagen, de solicitudes fetch controladas simultáneas ni de conexiones WebSocket controladas (el broker del host sigue aplicando su política), y require('fetch').policy.maxConcurrentRequests / require('websocket').policy.maxConnections se retiran
* `Mejora` Las fachadas puenteadas fetch / WebSocket / axios delegan sus topes al host: tiempos de espera, tamaño de respuesta, número de redirecciones, tamaños de mensaje y de cola y método HTTP se pasan al proveedor del host sin recortar (aplica su propia política), el runtime sigue hasta 20 redirecciones como Node, y los campos hard*/tamaños por defecto retirados de policy se sustituyen por limitsEnforcedBy
* `Mejora` opendir devuelve el fs.Dir perezoso nativo de Node (bufferSize, encoding y recursive van al opendir nativo, ENOENT / ENOTDIR / ERR_DIR_CLOSED son errores de Node; solo dir.path y parentPath conservan la escritura del llamador), y se retira la protección del runtime contra eliminar la raíz de alcance del sistema de archivos, de modo que rm / rmdir de la raíz lo deciden Node y Android como cualquier otra ruta (los códigos de política fs quedan en FS_NUL_BYTE y FS_PATH_ESCAPE)
* `Mejora` El transporte del provider del host adopta los tamaños por fuente / totales / de recuento de peticiones que el host anuncia mediante getNativeDiagnostics() (los valores integrados 16 MiB / 64 MiB / 139264 son solo un respaldo), los cuerpos de respuesta del fetch puenteado llegan por descriptor de archivo (bodyTransport "pfd") y por tanto solo los acota la política maxResponseBytes del host y no el tamaño de transacción de Binder, y una respuesta del host que supere el tamaño de transacción de Binder se notifica de inmediato como ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (rama del host node-m20-2-binder-body-pfd) en lugar de un tiempo de espera del puente
* `Mejora` Los cuerpos de petición del fetch puenteado y los mensajes WebSocket de más de 256 KiB llegan ahora al host como un descriptor de archivo de solo lectura (bodyTransport / messageTransport "pfd") en lugar de JSON base64 en línea, en hosts que anuncian bridgeRequestBinaryTransport=pfd (rama del host node-m20-2-binder-body-pfd), de modo que las subidas solo las acotan la política de peticiones del host (64 MiB) y maxMessageBytes y no el tamaño de transacción de Binder
* `Mejora` Las respuestas del fetch controlado ahora exponen la URL final del proveedor mediante Response.url (ResponseInit no incluye url, por lo que antes siempre era una cadena vacía)
* `Mejora` La interoperabilidad Java ahora reenvía todas las llamadas a la lista blanca declarativa del host (clase → constructores, métodos estáticos y de instancia, campos; el host los ejecuta por reflexión con primitivos JSON y handles de objetos), el runtime deja de mantener su propia tabla de clases y lee la publicada por el host como java.policy, se añaden getStatic() / describe() y las excepciones del propio miembro se reportan como ERR_AUTOJS6_JAVA_CALL_FAILED
* `Mejora` La precedencia de npm sobre los módulos del runtime se limita ahora a los módulos que sustituyen paquetes npm reales (axios, colors, mime, nanoid, opencc, undici): un paquete homónimo en node_modules ya no reemplaza las fachadas java, fetch, websocket, device ni las demás de AutoJs6, require.resolve sigue la misma regla que require, cualquier nombre de módulo del runtime puede importarse desde ESM, autojs6:profile publica la regla como moduleResolutionProfile y el proxy Packages de Rhino incorpora getStatic() / describe()
* `Mejora` Cuando un proceso de ranura del runtime muere en mitad de un script (eliminado por el low-memory killer, fallo nativo, kill directo), el resultado de error ahora informa el registro de salida del sistema en lugar de un simple DeadObjectException: el mensaje es, por ejemplo, LOW_MEMORY (killed by the system low-memory killer; rss 2.6 GB) o CRASH_NATIVE (SIGABRT; see the logcat tombstone), el resultado incluye slotExit y getRuntimeInfo incluye lastSlotExit (Android 11+; los sistemas anteriores informan que el motivo no está disponible)

# v1.5.4

###### 2026/09/17

* `Correccion` La ejecución integrada deja de imprimir avisos ExperimentalWarning; conserva los eventos warning, advertencias normales, avisos de obsolescencia y errores. La estabilidad de las API de Node y los valores predeterminados del terminal no cambian
* `Mejora` Los flujos de archivos, gzip/deflate/Brotli y la ejecución básica de VM son estables; Debug Inspector, activado explícitamente, es estable dentro de localhost

# v1.5.3

###### 2026/09/16

* `Mejora` Autorización de red local de Android 17 integrada en la activación y los ajustes del plugin, sin página de permisos en el lanzador; sin permiso, el plugin permanece desactivado y se omite el inicio automático sin avisos
* `Mejora` Compatibilidad con Android 17 (SDK 37), controles de permiso de red local propios del plugin y ayuda para recuperar el acceso

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
