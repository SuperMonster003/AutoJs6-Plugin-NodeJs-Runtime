# v1.2.0

###### 2026/08/25

* `Nuevo` Se habilitó el acceso al sistema de archivos similar al escritorio dentro de los permisos Android, manteniendo bloqueados `/proc`, `/sys` y `/dev`
* `Nuevo` Se añadieron `accessibility.swipe` y `accessibility.gesture` tras la capacidad dedicada `accessibility.gesture`
* `Correccion` TypeScript sin compilar ahora falla de forma cerrada si el host no aporta la salida del compilador; también se mapearon imports dinámicos de snapshot y se normalizaron las pilas generadas/importadas
* `Mejora` Se alinearon el contrato v2 host/plugin, los manifiestos de capacidades, el espejo de ejemplos y el límite de responsabilidad del runtime solo en el plugin
