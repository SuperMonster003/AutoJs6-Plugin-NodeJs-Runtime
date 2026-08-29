# v1.2.0

###### 2026/08/26

* `Nuevo` Se habilitó el acceso al sistema de archivos similar al escritorio dentro de los permisos Android, manteniendo bloqueados `/proc`, `/sys` y `/dev`
* `Nuevo` Se añadieron `accessibility.swipe` y `accessibility.gesture` tras la capacidad dedicada `accessibility.gesture`
* `Correccion` TypeScript sin compilar ahora falla de forma cerrada si el host no aporta la salida del compilador; también se mapearon imports dinámicos de snapshot y se normalizaron las pilas generadas/importadas
* `Correccion` Se reemplazó el adaptador ESM parcial basado en snapshots por el linker nativo de V8, corrigiendo exports mutables que no se actualizaban mediante reexports cíclicos
* `Mejora` Se alinearon el contrato v2 host/plugin, los manifiestos de capacidades y el límite de responsabilidad del runtime solo en el plugin
* `Mejora` Se centralizaron en el repositorio del plugin los ejemplos de Node.js, las declaraciones de TypeScript, el asistente de proyectos, los valores predeterminados del runtime y las comprobaciones de alineación con el host, eliminando los conmutadores Gradle y los recursos de desarrollo duplicados del host
