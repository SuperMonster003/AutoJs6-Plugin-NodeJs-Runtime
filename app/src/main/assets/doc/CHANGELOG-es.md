# v1.3.0

###### Sin publicar

* `Nuevo` Las suscripciones del puente Node.js envían eventos de sensores, WebSocket, interfaz, ventanas flotantes y entrada mediante callbacks existentes, con on/once/off, colas limitadas y compatibilidad con drainEvents
* `Correccion` Corregida la acumulación de solicitudes y respuestas del puente en scripts residentes: se eliminan las solicitudes completadas y se conservan las últimas 32 respuestas con un límite de tamaño para los diagnósticos
* `Mejora` Se redujo la latencia del puente con transporte JNI/Binder predeterminado y respuestas mediante el bucle de eventos de Node, conservando la alternativa por archivos y los límites de llamadas pendientes
