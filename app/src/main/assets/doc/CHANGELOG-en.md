# v1.4.0

###### 2026/09/10

* `Feature` MediaInfo queries support zero-based streamNumber, countGet stream counts, and infoKind for units, descriptions and readable names; Rhino and Node preserve default first-stream TEXT queries and negotiate extended plugin capabilities
* `Fix` MediaInfo and image file paths now allow absolute paths, parent directories and valid filenames; recording outputs use the same Android file access rules with the updated host
* `Fix` Bridge capability errors now identify missing node.permissions declarations without requiring the diagnostic-only pro_compat_opt_in profile
* `Fix` The project validator no longer rejects absolute fs paths or parent directories as FS_OUTSIDE_SCOPE; Android determines actual file access
* `Improvement` Added standalone Android event and three-second audio recording projects, with project declarations and manual steps for screen capture, OCR, physical keys and MediaInfo
* `Improvement` Screen capture matching, screen OCR and three-second AAC recording passed manual device acceptance; the corresponding examples and Pro parity snippets using the same calls are now stable
