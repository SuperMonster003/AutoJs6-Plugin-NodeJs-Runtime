# v1.3.0

###### Sin publicar

* `Nuevo` Las suscripciones del puente Node.js envían eventos de sensores, WebSocket, interfaz, ventanas flotantes y entrada mediante callbacks existentes, con on/once/off, colas limitadas y compatibilidad con drainEvents
* `Nuevo` Opción idleExitMs para cerrar el proceso Node.js inactivo y reconectar el siguiente script, con diagnóstico idleForMs y permanencia en memoria por defecto
* `Correccion` Corregida la acumulación de solicitudes y respuestas del puente en scripts residentes: se eliminan las solicitudes completadas y se conservan las últimas 32 respuestas con un límite de tamaño para los diagnósticos
* `Correccion` Corregidos los resultados exitosos prematuros que perdían errores asíncronos nativos y códigos de salida tardíos; la finalización sigue la salida final del bucle Node
* `Mejora` Se redujo la latencia del puente con transporte JNI/Binder predeterminado y respuestas mediante el bucle de eventos de Node, conservando la alternativa por archivos y los límites de llamadas pendientes
* `Mejora` Restauradas las exportaciones nativas Node.js de stream, crypto, timers, util, node:test y módulos relacionados, manteniendo los límites del sistema de archivos y los directorios del host
