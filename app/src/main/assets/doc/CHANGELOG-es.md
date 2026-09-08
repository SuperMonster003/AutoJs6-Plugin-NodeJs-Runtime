# v1.3.0

###### Sin publicar

* `Nuevo` Las suscripciones del puente Node.js envían eventos de sensores, WebSocket, interfaz, ventanas flotantes y entrada mediante callbacks existentes, con on/once/off, colas limitadas y compatibilidad con drainEvents
* `Nuevo` Opción idleExitMs para cerrar el proceso Node.js inactivo y reconectar el siguiente script, con diagnóstico idleForMs y permanencia en memoria por defecto
* `Nuevo` Los objetos globales fetch, Request, Response, Headers, FormData y WebSocket usan las API web nativas de Node; autojs6:fetch y autojs6:websocket mantienen la pila de red del host
* `Nuevo` Añadido node:sqlite nativo con control de rutas, CRUD, transacciones y copias; reporteros nativos node:test y ejemplo ejecutable, con zod, cheerio, date-fns, mqtt y ws en el corpus npm sin conexión
* `Nuevo` Entrada de consola con process.stdin / readline y mensajes JSON por ejecución mediante autojs6:host; postMessage v3 compatible con anfitriones anteriores
* `Nuevo` Las sesiones de captura de Node.js usan el consentimiento de Android y el servicio en primer plano existente, con identificadores de imagen, guardado PNG/JPEG/WebP y limpieza al detenerse o terminar el script
* `Nuevo` Los identificadores de imagen de Node.js admiten recorte, redimensionado, escala de grises, umbralización y búsqueda de plantillas o colores mediante el motor anfitrión, con imágenes independientes y limpieza al terminar
* `Nuevo` Node.js image.toBytes transfiere píxeles PNG o RGBA mediante descriptores de archivo a Buffer nativos, por JNI o el puente de archivos, y libera los adjuntos tras su uso, al vencer el plazo o al terminar
* `Correccion` Corregida la acumulación de solicitudes y respuestas del puente en scripts residentes: se eliminan las solicitudes completadas y se conservan las últimas 32 respuestas con un límite de tamaño para los diagnósticos
* `Correccion` Corregidos los resultados exitosos prematuros que perdían errores asíncronos nativos y códigos de salida tardíos; la finalización sigue la salida final del bucle Node
* `Mejora` Se redujo la latencia del puente con transporte JNI/Binder predeterminado y respuestas mediante el bucle de eventos de Node, conservando la alternativa por archivos y los límites de llamadas pendientes
* `Mejora` Restauradas las exportaciones nativas Node.js de stream, crypto, timers, util, node:test y módulos relacionados, manteniendo los límites del sistema de archivos y los directorios del host
* `Mejora` Los workers nativos usan el paralelismo de CPU (hasta ocho), heredan los ajustes de red y archivos y admiten límites por solicitud; las tareas del pool no tienen plazo predeterminado y los ejemplos CPU y WASM ejecutan workers reales
* `Mejora` WASI nativo sigue desactivado para conservar los límites del sistema de archivos; se eliminan los dos ejemplos WASI desactivados y se mantienen WebAssembly y los workers WASM
