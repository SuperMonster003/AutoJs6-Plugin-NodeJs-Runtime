# v1.4.0

###### 2026/09/10

* `Nuevo` Las consultas MediaInfo admiten streamNumber desde 0, countGet e infoKind para unidades, descripciones y nombres legibles; Rhino y Node mantienen TEXT del primer flujo por defecto y negocian las capacidades del plugin
* `Correccion` Las rutas de MediaInfo e imágenes admiten rutas absolutas, directorios superiores y nombres válidos; las grabaciones siguen las mismas reglas de acceso de Android con el anfitrión actualizado
* `Correccion` Los errores de capacidades del puente indican las declaraciones node.permissions que faltan sin exigir el perfil pro_compat_opt_in, usado solo para diagnóstico
* `Correccion` El validador de proyectos deja de rechazar rutas fs absolutas o directorios superiores con FS_OUTSIDE_SCOPE; Android determina el acceso real
* `Mejora` Proyectos independientes de eventos Android y grabación de audio de tres segundos, con declaraciones y pasos manuales para captura, OCR, teclas físicas y MediaInfo
