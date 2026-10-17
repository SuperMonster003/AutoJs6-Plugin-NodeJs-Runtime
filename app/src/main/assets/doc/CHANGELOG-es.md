******

### Historial De Versiones

******

# v1.7.0

###### 2026/10/18

* `Nuevo` Los proyectos y scripts Node.js se empaquetan como aplicaciones con firma independiente, usan el plugin externo Node.js Runtime y muestran instrucciones de instalación cuando no está disponible
* `Nuevo` screen_metrics y autojs6:compat ofrecen cX/cY/cXy/cYx asíncronos con la pantalla actual y bases por ejecución, con comprobaciones de cobertura y ejemplo ESM
* `Nuevo` Node.js y Bun admiten OpenCC asíncrono, códecs de imagen, lectura de píxeles, transformaciones y búsqueda de colores; acceso a archivos de imagen de Bun autorizado por separado
* `Nuevo` Los Workers de Node.js usan OpenCC e imágenes mediante el runtime padre, con CJS, ESM, Workers anidados y transferencia binaria
* `Nuevo` Los Workers de Node.js ofrecen pinyin, pinyin4j, controles de consola y coordenadas, compartiendo el estado padre y conservando los flujos nativos
* `Nuevo` El hilo principal de Node.js y los Workers admiten grabacion H.264 MP4 con audio AAC de microfono, reproduccion o mixto, capturas simultaneas y limpieza de grabaciones Worker sin guardar
* `Nuevo` Node.js images admite filtros avanzados, conversión de color, búsquedas multicolor, círculos, métricas de similitud y carga de URL
* `Nuevo` Node.js images admite extracción y coincidencia SIFT/ORB con coordenadas de escena, geometría ObjectFrame y handles Mat/características por ejecución
* `Nuevo` Node.js images admite cuantización PNG y compresión con métricas de calidad/memoria, resultados binarios y salida mediante descriptor de archivo
* `Nuevo` Node.js UI y Compose admiten diseños nativos, componentes de datos, estado reactivo y eventos, con permisos independientes y cierre automático
* `Correccion` Pinyin/Pinyin4j llaman de forma asíncrona a los proveedores del anfitrión en lugar del pequeño diccionario local; los controles de consola Android se ejecutan en el anfitrión y devuelven Promises; se completan los tipos de gestos e input_observer
* `Correccion` Node.js images.captureScreen deja de enviar opciones de consentimiento al procesamiento de capturas, permitiendo capturas normales y durante la grabación
* `Dependencia` Se incorporaron las correcciones de seguridad de OpenSSL 3.5.9 al entorno Android de Node.js 24.21.0

# v1.6.0

###### 2026/10/09

* `Nuevo` La página de ajustes del plugin permite elegir 1-8 ejecuciones simultáneas (2 por defecto) y un límite de cola (sin límite por defecto); los scripts adicionales esperan en FIFO, los cambios conservan las tareas aceptadas e idleExitMs libera los procesos inactivos
* `Correccion` El asistente de proyectos deja de rechazar dependencias nativas, scripts de ciclo de vida npm, package exports e Inspector compatibles; conserva la comprobación de permisos declarados del anfitrión
* `Mejora` fs, fs/promises y node:sqlite son los módulos nativos de Node.js: desaparecen la denegación de /proc, /sys, /dev propia del runtime y su capa de validación de rutas, la accesibilidad de los archivos la deciden únicamente los permisos de Android, las API de archivos asíncronas se ejecutan en el pool de hilos de libuv en lugar de bloquear el bucle de eventos, y los errores de ruta llevan los códigos propios de Node.js (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH y ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED ya no los producen las operaciones de archivos ni de base de datos)
* `Mejora` process.cwd y process.chdir tienen el comportamiento nativo de Node.js; una ejecución entra en su directorio de trabajo con un chdir real y las rutas relativas las resuelve el propio Node.js
* `Mejora` ATTACH de node:sqlite (incluidos nombres de archivo por parámetro enlazado o expresión), VACUUM INTO, los PRAGMA de directorio y setAuthorizer son comportamiento nativo de SQLite; enableLoadExtension y allowExtension siguen las comprobaciones de estado propias de Node.js
* `Mejora` La biblioteca de ejecución de Node.js se compila con los datos ICU completos (--with-intl=full-icu), de modo que Intl y el formato regional coinciden con el Node.js de escritorio y NODE_ICU_DATA ya no es necesario
* `Mejora` crypto.createPrivateKey y las API de firma aceptan claves OpenSSL STORE en forma de URL file: (se elimina el parche de desactivación exclusivo de Android)
* `Mejora` Todos los módulos integrados de Node.js (cluster, domain, repl, trace_events, wasi, constants, node:sea y el resto) se devuelven tal como Node los exporta, directamente desde el inventario module.builtinModules de Node; require, require.resolve, import, process.getBuiltinModule y module.isBuiltin se comportan como en escritorio, y solo worker_threads, child_process, inspector y los módulos de red nativos conservan una puerta a nivel de solicitud; node:wasi queda disponible junto con la eliminación del límite /proc (en la libnode actual, compilada sin trazado, trace_events lanza ERR_TRACE_EVENTS_UNAVAILABLE exactamente como Node)
* `Mejora` process conserva solo la gestión del ciclo de vida de exit; dlopen, abort, kill, umask, binding, report, features y las llamadas de identidad POSIX usan Node; os es nativo, homedir y tmpdir siguen HOME/TMPDIR y userInfo devuelve el usuario Android de la aplicación
* `Mejora` supportedAbis en la información del plugin (INFO) y en los diagnósticos de ejecución procede ahora de la meta-data de manifiesto org.autojs.plugin.contract.SUPPORTED_ABIS escrita en la compilación (plugin nativeAlignment de AutoJs6-Gradle-Platform-Versions 1.9.0 o posterior), es decir, todas las ABI para las que se compila el plugin y no el split instalado; sin esa meta-data recurre a las ABI empaquetadas en los APK instalados, y se eliminan las constantes ABI escritas a mano
* `Mejora` La carga de módulos es el loader CommonJS / ESM nativo de Node.js: require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta y require(esm) son nativos, y la entrada se ejecuta como módulo principal de Node (require.main === module, __filename / process.argv[1] son realpaths); el runtime solo registra module.registerHooks que sirven los módulos de runtime autojs6:* y los shims de compatibilidad (esquema autojs6:; un paquete npm instalado sigue ocultando al shim del mismo nombre), la barrera de builtins por solicitud, las fuentes de módulos adjuntas a la solicitud / materializadas por el provider / de la instantánea TypeScript y la fuente en línea de la entrada; desaparecen el linker vm.SourceTextModule, el resolver propio, la reescritura de import() dinámico y las guardas eval / Function, y las claves de solicitud esmEnabled / dynamicImportEnabled ya no tienen efecto
* `Mejora` Los Workers nativos admiten anidación, MessagePort, stdio, argv, execArgv y precargas heredadas; se eliminan los límites del plugin sobre cantidad y tiempo de arranque, resourceLimits aporta valores predeterminados modificables y WorkerPool no limita por defecto la cola ni la duración de las tareas; Inspector sigue la solicitud y la compilación
* `Mejora` autojs6:bridge.callAutoJs está disponible en Workers y Workers anidados mediante MessagePorts de sus padres, conservando permisos, errores, tiempos de espera y cancelación del broker del host; worker-host-bridge demuestra la lectura de información real del dispositivo e incluye declaraciones TypeScript para la API
* `Mejora` Las entradas y los módulos importados .ts / .mts / .cts siguen necesitando JavaScript generado por el Compiler del host; se mantienen el mapeo de instantáneas y la compilación bajo demanda del provider v3. Los errores nativos de Node sustituyen los antiguos códigos AutoJs6 de ESM e importación dinámica.
* `Mejora` Los scripts principales y Workers cargan addons nativos Android mediante Node; process.dlopen y .node son nativos y no requieren listas propias de firmas o hashes; native-addon incluye tres ABI alineadas a 16 KB y compilación NDK/CMake sin conexión; ABI incorrectas o binarios dañados producen errores Node como ERR_DLOPEN_FAILED, sin incluir NDK ni Python en el dispositivo
* `Mejora` Inspector funciona en Debug y Release con permiso explícito del anfitrión; inspector e inspector/promises conservan los módulos nativos de Node, con Session en scripts y Workers, escucha solo en 127.0.0.1 y sin esperas bloqueantes; sin permiso url() devuelve undefined y se impiden la activación y la creación de Session
* `Mejora` install/update/remove de package_manager aceptan especificaciones npm y ejecutan el npm incluido en el espacio de trabajo; npm.cli/exec/runScript/pack usan la CLI real, con registry/proxy/ciclo de vida según npm y .npmrc del proyecto, salida en tiempo real y códigos de error npm; los archivos CLI se preparan al primer uso y las solicitudes de objeto conservan el contrato del almacén local del anfitrión
* `Mejora` Inicio de scripts más rápido al evitar inicializaciones Web API innecesarias y activar por defecto la caché de compilación portátil nativa de Node para espacios de trabajo y Workers; reutilización entre copias e invalidación de fuentes modificadas a cargo de Node
* `Mejora` Los metadatos describen las funciones implementadas sin estados provisionales; los ejemplos UI indican los proveedores anfitriones necesarios y se eliminan los campos de mercado y actualización de plugins sin plan

# v1.5.7

###### 2026/10/05

* `Mejora` Los iconos del centro de plugins usan los tamaños, posiciones, imágenes claras y oscuras y fondos circulares ajustados en Icon Studio, conservando fuentes y parámetros reproducibles
* `Mejora` Los iconos de información de la aplicación de Android comparten las imágenes y los fondos claros y oscuros de Icon Studio, conservando las imágenes transparentes del centro de plugins y las opciones del lanzador

# v1.5.6

###### 2026/09/19

* `Nuevo` Un error fatal de Node.js en una ranura del runtime (heap de JavaScript agotado, comprobación interna fallida) ahora deja el informe de diagnóstico propio de Node en la caché del plugin (crash/<executionId>.json, escrito antes del abort; no se instala ningún manejador de señales): el resultado de fallo lleva su evento, cifras del heap, pila JS y ruta en slotExit.crash y en el mensaje de error (por ejemplo CRASH_NATIVE (SIGABRT; see the logcat tombstone; Node.js fatal error: Allocation failed - JavaScript heap out of memory; JS heap 61.9 MB used of 64.0 MB limit; report .../crash/<executionId>.json)), y getRuntimeInfo expone el informe más reciente como lastCrash incluso tras reiniciar el despachador; la nueva opción de solicitud maxOldGenerationSizeMb limita el heap JS de una ejecución (generación vieja de V8, en MB), que es también como la prueba de regresión reproduce el fallo
* `Correccion` Se corrigió la muerte de la ranura del runtime por agotamiento del heap de V8 (informado como CRASH_NATIVE / SIGABRT) al hacer require de un módulo CommonJS grande: el enmascarado de comentarios y cadenas del cargador concatenaba carácter a carácter, por lo que un módulo de 22 MiB necesitaba unos 700 MB de heap; ahora emite tramos con el mismo resultado (unos 44 MB) y los escaneos de import / export de CommonJS saltan entre candidatos en lugar de comprobar cada carácter
* `Correccion` Advertencias de lectura de SDK XML v4 con AGP 9.1 y comprobaciones de alineación nativa de APK activadas por error al ensamblar pruebas unitarias JVM, mediante los plugins de compilación compartidos 1.8.3

# v1.5.5

###### 2026/09/18

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

# v1.5.2

###### 2026/09/15

* `Mejora` compileSdk sube a 37 (Android 17); targetSdk se mantiene en 36 hasta verificar el comportamiento que depende del objetivo

# v1.5.1

###### 2026/09/15

* `Correccion` El texto de errores fatales y advertencias de Node se escribe en el stderr real en Android además de en logcat, de modo que un terminal muestra las excepciones no capturadas en lugar de salir en silencio
* `Mejora` `libnode.so` recompilado con `--with-intl=small-icu`: `Intl` y los escapes de propiedades Unicode (`\p{...}`) en expresiones regulares están disponibles con datos de configuración regional solo en inglés (`NODE_ICU_DATA` acepta un archivo de datos ICU completo), por lo que corepack puede ejecutar pnpm 11 y Yarn Berry en el terminal de AutoJs6

# v1.5.0

###### 2026/09/14

* `Nuevo` Lanzador de terminal multi-llamada `libnodexe.so` (node / npm / npx / corepack / yarn / pnpm) empaquetado para cada ABI con `DT_RUNPATH $ORIGIN` y alineación de páginas de 16 KB
* `Nuevo` Archivo de npm 11.19.0 y corepack 0.36.0 tomado de la distribución oficial de Node.js 24.21.0, declarado mediante los meta-data de manifest `NODE_CLI_*` (schema 1) y reflejado en runtimeInfo / PluginInfo como la capacidad `nodeCli`

# v1.4.2

###### 2026/09/13

* `Correccion` Informar solo de las ABI nativas presentes en el APK instalado
* `Correccion` Usar fechas de compilación en inglés independientemente del idioma de la máquina
* `Correccion` Metadatos de versión coherentes en los paquetes y archivos de publicación
* `Correccion` Compatibilidad de archivos del espacio de trabajo en Android 7 con aislamiento de descriptores

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

# v1.3.0

###### 2026/09/09

* `Nuevo` Las suscripciones del puente Node.js envían eventos de sensores, WebSocket, interfaz, ventanas flotantes y entrada mediante callbacks existentes, con on/once/off, colas limitadas y compatibilidad con drainEvents
* `Nuevo` Opción idleExitMs para cerrar el proceso Node.js inactivo y reconectar el siguiente script, con diagnóstico idleForMs y permanencia en memoria por defecto
* `Nuevo` Los objetos globales fetch, Request, Response, Headers, FormData y WebSocket usan las API web nativas de Node; autojs6:fetch y autojs6:websocket mantienen la pila de red del host
* `Nuevo` Añadido node:sqlite nativo con control de rutas, CRUD, transacciones y copias; reporteros nativos node:test y ejemplo ejecutable, con zod, cheerio, date-fns, mqtt y ws en el corpus npm sin conexión
* `Nuevo` Entrada de consola con process.stdin / readline y mensajes JSON por ejecución mediante autojs6:host; postMessage v3 compatible con anfitriones anteriores
* `Nuevo` Las sesiones de captura de Node.js usan el consentimiento de Android y el servicio en primer plano existente, con identificadores de imagen, guardado PNG/JPEG/WebP y limpieza al detenerse o terminar el script
* `Nuevo` Los identificadores de imagen de Node.js admiten recorte, redimensionado, escala de grises, umbralización y búsqueda de plantillas o colores mediante el motor anfitrión, con imágenes independientes y limpieza al terminar
* `Nuevo` Node.js image.toBytes transfiere píxeles PNG o RGBA mediante descriptores de archivo a Buffer nativos, por JNI o el puente de archivos, y libera los adjuntos tras su uso, al vencer el plazo o al terminar
* `Nuevo` Las API Node device ofrecen información actual del sistema, pantalla, batería y memoria, control de brillo y pantalla encendida por tiempo limitado; media permite ajustar el volumen según los permisos de Android
* `Nuevo` autojs6:events observa notificaciones Android, Toast externos, teclas de accesibilidad y eventos de pantalla o batería mediante callbacks, con permisos explícitos y limpieza automática; events nativo de Node mantiene su compatibilidad
* `Nuevo` Node app admite servicios Android, broadcasts y consultas de aplicaciones instaladas; dialogs ofrece entrada de texto, selección y ventanas de progreso que se cierran al terminar el script, y keys incorpora acciones del sistema de accesibilidad
* `Nuevo` Node recorder admite grabación AAC con permiso de micrófono, notificación, límite de duración y limpieza al terminar el script; ui.overlay admite cambios de propiedades, arrastre y envío de eventos
* `Nuevo` Los scripts Node pueden ejecutarse en dos procesos independientes con una cola FIFO compartida y cancelación y entrada dirigidas a cada ejecución
* `Correccion` Corregida la acumulación de solicitudes y respuestas del puente en scripts residentes: se eliminan las solicitudes completadas y se conservan las últimas 32 respuestas con un límite de tamaño para los diagnósticos
* `Correccion` Corregidos los resultados exitosos prematuros que perdían errores asíncronos nativos y códigos de salida tardíos; la finalización sigue la salida final del bucle Node
* `Correccion` Las URL de claves OpenSSL STORE eludían las restricciones del sistema de archivos (leer los bytes de la clave mediante node:fs)
* `Mejora` Se redujo la latencia del puente con transporte JNI/Binder predeterminado y respuestas mediante el bucle de eventos de Node, conservando la alternativa por archivos y los límites de llamadas pendientes
* `Mejora` Restauradas las exportaciones nativas Node.js de stream, crypto, timers, util, node:test y módulos relacionados, manteniendo los límites del sistema de archivos y los directorios del host
* `Mejora` Los workers nativos usan el paralelismo de CPU (hasta ocho), heredan los ajustes de red y archivos y admiten límites por solicitud; las tareas del pool no tienen plazo predeterminado y los ejemplos CPU y WASM ejecutan workers reales
* `Mejora` WASI nativo sigue desactivado para conservar los límites del sistema de archivos; se eliminan los dos ejemplos WASI desactivados y se mantienen WebAssembly y los workers WASM
* `Mejora` Los ejemplos OCR solicitan el consentimiento de Android y reconocen imágenes reales; los plugins OCR o de códigos de barras no disponibles devuelven un error unavailable legible, y los fallos de reconocimiento siguen siendo errores
* `Mejora` Los scripts de Node admiten inicio asíncrono negociado, liberan los hilos Binder durante ejecuciones prolongadas y devuelven los cambios del espacio de trabajo al finalizar, con compatibilidad para hosts síncronos anteriores
* `Dependencia` Actualización de Node.js 24.5.0 → 24.21.0 con bibliotecas Android compiladas desde el código fuente para las tres ABI

# v1.2.0

###### 2026/08/29

* `Nuevo` Se agregó `capabilities()` al facade `mediainfo` y selección explícita de snapshots v1/v2 del plugin a su entrada invocable y `read()`; omitir schema continúa devolviendo el snapshot Node v1 existente propiedad del host
* `Nuevo` Se añadió module-source provider v3 para compilar bajo demanda archivos `.ts/.mts/.cts` creados durante la ejecución mediante PFD acotados por bytes y SHA-256, con un presupuesto de compilación independiente de 30 s, rechazo estable de rutas, enlaces simbólicos y ambigüedad, además de diagnósticos TypeScript y mapeo de pila Source Map del host
* `Nuevo` Se habilitó el acceso al sistema de archivos similar al escritorio dentro de los permisos Android, manteniendo bloqueados `/proc`, `/sys` y `/dev`
* `Nuevo` Se añadieron `accessibility.swipe` y `accessibility.gesture` tras la capacidad dedicada `accessibility.gesture`
* `Nuevo` Se añadieron tiempos límite que cubren la cola y la ejecución de scripts, con el error `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sin límite definido, los scripts pueden seguir ejecutándose indefinidamente
* `Nuevo` Se habilitaron `dgram` (UDP) y `http2` de forma predeterminada, con un error explícito de desactivación para `trace_events`
* `Nuevo` Se añadió depuración local con `inspector`, activada explícitamente en compilaciones Debug, escuchando solo en localhost y conectándose mediante `adb forward`
* `Nuevo` Se añadió una entrada de activación sin interfaz protegida por el permiso del complemento del anfitrión, se completaron las descripciones y se desactivó la copia de seguridad de datos
* `Correccion` TypeScript sin compilar ahora falla de forma cerrada si el host no aporta la salida del compilador; también se mapearon imports dinámicos de snapshot y se normalizaron las pilas generadas/importadas
* `Correccion` Se reemplazó el adaptador ESM parcial basado en snapshots por el linker nativo de V8, corrigiendo exports mutables que no se actualizaban mediante reexports cíclicos
* `Correccion` Se corrigieron las importaciones ESM de las fachadas de compatibilidad de AutoJs6 y el sondeo de sufijos TypeScript inexistentes, manteniendo la prioridad de los paquetes npm instalados
* `Mejora` Se eliminó el fallback legacy de borrado TypeScript basado en regex y su conmutador de solicitud; `.ts/.mts/.cts` raw ahora siempre requieren salida del compilador del host
* `Mejora` Se alinearon el contrato v2 host/plugin, los manifiestos de capacidades y el límite de responsabilidad del runtime solo en el plugin
* `Mejora` Se centralizaron en el repositorio del plugin los ejemplos de Node.js, las declaraciones de TypeScript, el asistente de proyectos, los valores predeterminados del runtime y las comprobaciones de alineación con el host, eliminando los conmutadores Gradle y los recursos de desarrollo duplicados del host
* `Mejora` Se amplió la cobertura npm verificada a 15 paquetes, incorporando axios, express y los paquetes ESM-only nanoid, p-limit y yocto-queue
* `Mejora` El campo de solicitud `executionMode` pasa a determinar el modo del ciclo de vida y el campo sin efecto `runtimeAdapter` queda obsoleto
* `Mejora` Se eliminaron diez ejemplos históricos que solo imprimían estados fijos y se indicó en los tipos la ausencia de proveedores del anfitrión para captura de pantalla, análisis de imágenes y grabación

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
