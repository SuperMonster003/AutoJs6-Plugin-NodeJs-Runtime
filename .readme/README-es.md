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

El plugin AutoJs6 Node.js Runtime proporciona a AutoJs6 un runtime nativo integrado de Node.js 24.21.0 para scripts Node.js y tareas de runtime de plugins.

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

- Slot de runtime: `node24_21`.
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

# v1.4.2

###### 2026/09/13

* `Correccion` Informar solo de las ABI nativas presentes en el APK instalado

# v1.4.1

###### 2026/09/13

* `Mejora` Verificación de compilación de la alineación de páginas de 16 KB en bibliotecas nativas de 64 bits, con controles del contrato manifest e informes JSON
* `Mejora` Activación del host, metadatos, documentación traducida y recopilación de APK firmados conforme a las convenciones comunes

# v1.4.0

###### 2026/09/10

* `Nuevo` Las consultas MediaInfo admiten streamNumber desde 0, countGet e infoKind para unidades, descripciones y nombres legibles; Rhino y Node mantienen TEXT del primer flujo por defecto y negocian las capacidades del plugin
* `Correccion` Las rutas de MediaInfo e imágenes admiten rutas absolutas, directorios superiores y nombres válidos; las grabaciones siguen las mismas reglas de acceso de Android con el anfitrión actualizado
* `Correccion` Los errores de capacidades del puente indican las declaraciones node.permissions que faltan sin exigir el perfil pro_compat_opt_in, usado solo para diagnóstico
* `Correccion` El validador de proyectos deja de rechazar rutas fs absolutas o directorios superiores con FS_OUTSIDE_SCOPE; Android determina el acceso real
* `Mejora` Proyectos independientes de eventos Android y grabación de audio de tres segundos, con declaraciones y pasos manuales para captura, OCR, teclas físicas y MediaInfo
* `Mejora` La búsqueda en capturas, el OCR y la grabación AAC de tres segundos superaron la validación manual en un dispositivo; sus ejemplos y fragmentos de paridad Pro con las mismas llamadas son estables
* `Mejora` El ejemplo de validación de eventos indica cómo desactivar temporalmente el atajo de parada con subir volumen; el host actualizado lee node.timeoutMs de project.json para permitir esperas de más de cinco segundos

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
