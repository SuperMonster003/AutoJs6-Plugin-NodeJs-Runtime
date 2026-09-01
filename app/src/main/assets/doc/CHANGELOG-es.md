# v1.2.0

###### 2026/08/29

* `Nuevo` Se agregó `capabilities()` al facade `mediainfo` y selección explícita de snapshots v1/v2 del plugin a su entrada invocable y `read()`; omitir schema continúa devolviendo el snapshot Node v1 existente propiedad del host
* `Nuevo` Se añadió module-source provider v3 para compilar bajo demanda archivos `.ts/.mts/.cts` creados durante la ejecución mediante PFD acotados por bytes y SHA-256, con un presupuesto de compilación independiente de 30 s, rechazo estable de rutas, enlaces simbólicos y ambigüedad, además de diagnósticos TypeScript y mapeo de pila Source Map del host
* `Nuevo` Se habilitó el acceso al sistema de archivos similar al escritorio dentro de los permisos Android, manteniendo bloqueados `/proc`, `/sys` y `/dev`
* `Nuevo` Se añadieron `accessibility.swipe` y `accessibility.gesture` tras la capacidad dedicada `accessibility.gesture`
* `Correccion` TypeScript sin compilar ahora falla de forma cerrada si el host no aporta la salida del compilador; también se mapearon imports dinámicos de snapshot y se normalizaron las pilas generadas/importadas
* `Correccion` Se reemplazó el adaptador ESM parcial basado en snapshots por el linker nativo de V8, corrigiendo exports mutables que no se actualizaban mediante reexports cíclicos
* `Correccion` Se corrigieron las importaciones ESM de las fachadas de compatibilidad de AutoJs6 y el sondeo de sufijos TypeScript inexistentes, manteniendo la prioridad de los paquetes npm instalados
* `Mejora` Se eliminó el fallback legacy de borrado TypeScript basado en regex y su conmutador de solicitud; `.ts/.mts/.cts` raw ahora siempre requieren salida del compilador del host
* `Mejora` Se alinearon el contrato v2 host/plugin, los manifiestos de capacidades y el límite de responsabilidad del runtime solo en el plugin
* `Mejora` Se centralizaron en el repositorio del plugin los ejemplos de Node.js, las declaraciones de TypeScript, el asistente de proyectos, los valores predeterminados del runtime y las comprobaciones de alineación con el host, eliminando los conmutadores Gradle y los recursos de desarrollo duplicados del host
