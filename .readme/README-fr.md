<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de runtime natif Node.js 24.21.0 pour AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues

******

Le README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.readme/README-ar.md)

******

### Introduction

******

Le plugin AutoJs6 Node.js Runtime fournit a AutoJs6 un runtime natif Node.js 24.21.0 embarque pour les scripts Node.js et les taches de runtime de plugins. Sous Android 17 ou version ultérieure, autorisez les appareils à proximité avant de permettre ce plugin dans le centre de plugins AutoJs6. Cette autorisation se gère aussi dans les paramètres du plugin. Sans autorisation, le plugin reste désactivé et le démarrage automatique est ignoré sans message. Cette autorisation appartient au plugin et est indépendante de celle de AutoJs6.

******

### Fonctionnalites

******

- Fournit le service de plugin `nodejs` avec l'ID de plugin `nodejs` et le moteur `nodejs`.
- Expose l'execution synchrone de scripts et le prechauffage du runtime a l'hote via `org.autojs.plugin.nodejs.RUNTIME`.
- Ajout d’un délai maximal couvrant l’attente et l’exécution des scripts, avec l’erreur `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sans délai défini, les scripts peuvent toujours s’exécuter indéfiniment
- Renvoie chaque module intégré de Node.js tel que Node l'exporte, directement depuis l'inventaire `module.builtinModules` de Node (`dgram`, `http2`, `trace_events`, `wasi`, `cluster`, `repl` et les autres) ; seuls `worker_threads`, `child_process`, `inspector` et les modules réseau bruts conservent un verrou au niveau de la requête (depuis 1.6.0).
- Débogage Inspector local dans Debug et Release sur demande explicite de l'hôte, via localhost et transfert ADB
- Prend en charge le code CommonJS/ESM, les sources de modules, le repertoire de travail, la racine de bac a sable, les variables d'environnement et les resultats stdout/stderr.
- Exécute les modules via les loaders CommonJS / ESM natifs de Node.js (`require`, `import()`, `import.meta`, `require(esm)`, exports / imports de paquets sont natifs) ; le runtime n'enregistre que des `module.registerHooks` pour les modules runtime `autojs6:*`, les sources de modules fournies par l'hôte et la barrière de builtins au niveau de la requête (depuis 1.6.0).
- Utilise le module fs natif de Node.js ; l'accessibilité des fichiers dépend uniquement des permissions Android de l'application du plugin, sans refus de `/proc`, `/sys` ou `/dev` propre au runtime (depuis 1.6.0).
- Exécute la sortie TypeScript fournie par l'hôte et peut demander une compilation provider-v3 pour les `.ts`/`.mts`/`.cts` de projet créés pendant l'exécution; l'envoi brut direct échoue toujours de façon fermée, sans fallback d'effacement ni commutateur de compatibilité.
- Fournit un agent de capacites hote et un live bridge avec des modules de runtime comme `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` et `autojs6:bridge-permissions`.
- Inclut les projets `sample/nodejs` et l'inventaire des capacités API hôte `docs/HOST-API.md`.
- Les metadonnees du plugin, les instructions d'utilisation, le README et le CHANGELOG sont localises en espagnol, francais, russe, arabe, japonais, coreen, anglais, chinois simplifie, chinois traditionnel de Hong Kong et chinois traditionnel de Taiwan.
- Fournit le lanceur de terminal multi-appel `libnodexe.so` et une archive npm / corepack déclarés via les meta-data manifest `NODE_CLI_*`, afin que le terminal AutoJs6 (hôte 6.8.0+) puisse exécuter `node`, `npm`, `npx`, `corepack`, `yarn` et `pnpm` dans un shell sous son propre uid.
- La page de réglages du plugin permet de choisir 1-8 exécutions simultanées (2 par défaut) et une limite de file (illimitée par défaut); les scripts excédentaires attendent en FIFO, les changements préservent les tâches acceptées et idleExitMs libère les processus inactifs
- Démarrage des scripts accéléré en évitant les initialisations Web API inutiles et en activant par défaut le cache de compilation portable natif de Node pour les espaces de travail et les Workers; réutilisation entre copies et invalidation des sources modifiées par Node

******

### Utilisation

******

```js
"nodejs";

console.log(process.version);
console.log("AutoJs6 Node.js runtime");
```

Installez et activez le plugin dans le centre de plugins AutoJs6, puis demarrez les scripts Node.js avec la directive `"nodejs";`. D'autres exemples sont disponibles dans `sample/nodejs`.

******

### Démarrage rapide

******

- **Installation** — Téléchargez l'APK correspondant à votre ABI depuis [Releases](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases) (choisissez `universal` en cas de doute) et installez-le, ou compilez localement avec `.\gradlew.bat :app:assembleDebug` puis installez depuis `app/build/outputs/apk/debug/`. Activez ensuite ce plugin dans le centre de plugins AutoJs6. Sous Android 11+, accordez au plugin l'accès à tous les fichiers si les scripts utilisent le stockage partagé; sans cette autorisation, `EACCES` est attendu.
- **Exécution** — Créez un script dans l'éditeur AutoJs6 dont la première ligne est `"nodejs";` et écrivez le reste comme du Node.js de bureau (CommonJS/ESM, paquets npm JS purs et modules réseau intégrés pris en charge). Lancez : la sortie s'affiche en direct et le script peut être arrêté à tout moment. Les fichiers `.ts`/`.mts`/`.cts` bruts doivent actuellement être compilés en JavaScript par l'hôte; le plugin n'intègre pas `tsc`.
- **En cas d'erreur** — Les échecs de script affichent la pile JS et un code d'erreur d'une ligne (par exemple `ERR_AUTOJS6_NODE_SCRIPT_CANCELLED`) dans la console ; pour plus de détails, consultez le journal du processus du plugin avec `adb logcat -s AutoJs6NodeBridge NodeJsRuntimePlugin`. La disponibilité des API hôte est documentée dans `docs/HOST-API.md`.

******

### Profil Du Runtime

******

- Slot de runtime: `node24_21`.
- ID de plugin: `nodejs`, moteur: `nodejs`.
- Action du service runtime: `org.autojs.plugin.nodejs.RUNTIME`.
- Bibliotheques natives de runtime: `libnode.so`, `libautojs6-node.so` et `libnodexe.so`.
- Intl : ICU 78 avec les données régionales complètes (`--with-intl=full-icu` depuis 1.6.0 ; de 1.5.1 à 1.5.7, small-icu en anglais uniquement) ; `Intl`, le formatage régional, les échappements de propriétés Unicode dans les expressions régulières et la sortie d'erreur de Node sur stderr correspondent au Node de bureau, sans `NODE_ICU_DATA`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` et `universal`.
- Système de fichiers : module fs natif de Node.js ; tout chemin de l'appareil autorisé par les permissions Android est accessible et le runtime ne conserve aucun refus propre.
- TypeScript : accepte la sortie de l'hôte, applique les instantanés précompilés et peut demander une compilation provider v3 ; les modules TypeScript bruts sur disque se chargent via la suppression de types native de Node, tandis qu'une entrée TypeScript brute envoyée directement renvoie toujours `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- ESM : loader natif de Node.js avec `module.registerHooks` ; liaisons vivantes, dépendances cycliques, `import.meta`, URL `data:` et `require(esm)` comme sur le bureau.
- Capacites: execution synchrone de scripts, bundle transport, runtime natif embarque, agent de capacites hote, host capability live bridge.
- Les flux de fichiers, les flux gzip/deflate/Brotli et l'exécution VM de base sont stables; le Inspector (Debug/Release) explicitement activé est stable dans son périmètre localhost.
- L'exécution intégrée n'affiche plus les avis ExperimentalWarning; les événements warning, avertissements ordinaires, dépréciations et erreurs restent disponibles. La stabilité des API Node et les valeurs par défaut du terminal sont inchangées.
- Les Workers natifs prennent en charge l’imbrication, MessagePort, stdio, argv, execArgv et les préchargements hérités; les limites du nombre de Workers et du délai de démarrage sont supprimées, resourceLimits fournit des valeurs par défaut modifiables et WorkerPool ne limite par défaut ni la file ni la durée des tâches; Inspector suit la requête et le build
- Les scripts principaux et Workers chargent les addons natifs Android avec Node; process.dlopen et .node sont natifs, sans liste propre au plugin de signatures ou de hachages; native-addon fournit trois ABI alignées sur 16 KB et une compilation NDK/CMake hors ligne; les incompatibilités ABI ou fichiers invalides produisent des erreurs Node telles que ERR_DLOPEN_FAILED, sans NDK ni Python embarqués sur l'appareil
- Inspector fonctionne dans les versions Debug et Release après autorisation explicite de l'hôte; inspector et inspector/promises conservent les modules natifs Node, avec Session dans les scripts et Workers, écoute sur 127.0.0.1 uniquement et sans attente bloquante; sans autorisation url() renvoie undefined et l'activation ainsi que la création de Session restent désactivées
- install/update/remove de package_manager acceptent les spécifications npm et exécutent npm fourni dans le projet; npm.cli/exec/runScript/pack utilisent la CLI réelle, avec registry/proxy/cycle de vie selon npm et le .npmrc du projet, sortie en continu et codes d'erreur npm; les fichiers CLI sont préparés au premier appel et les requêtes objet conservent le contrat du stockage local de l'hôte

******

### Historique Des Versions

******

# v1.7.0

###### 2026/10/17

* `Ajout` Les projets et scripts Node.js peuvent être empaquetés en applications signées indépendamment, exécutées par le plugin externe Node.js Runtime, avec une aide à l'installation si le runtime est indisponible
* `Ajout` screen_metrics et autojs6:compat proposent cX/cY/cXy/cYx asynchrones avec les dimensions actuelles et des bases par exécution, avec contrôles de couverture et exemple ESM
* `Ajout` Node.js et Bun prennent en charge OpenCC asynchrone, les codecs, la lecture des pixels, les transformations et la recherche de couleurs; accès aux fichiers image de Bun autorisé séparément
* `Ajout` Les Workers Node.js utilisent OpenCC et les images via le runtime parent, avec CJS, ESM, Workers imbriqués et transfert binaire
* `Correction` Pinyin/Pinyin4j appellent les fournisseurs hôtes de façon asynchrone au lieu du petit dictionnaire local; les commandes de console Android exécutent leurs actions sur l'hôte et renvoient des Promises; déclarations des gestes et de input_observer complétées
* `Dependance` Rétroportage des correctifs de sécurité OpenSSL 3.5.9 vers le moteur Android Node.js 24.21.0

# v1.6.0

###### 2026/10/09

* `Ajout` La page de réglages du plugin permet de choisir 1-8 exécutions simultanées (2 par défaut) et une limite de file (illimitée par défaut); les scripts excédentaires attendent en FIFO, les changements préservent les tâches acceptées et idleExitMs libère les processus inactifs
* `Correction` L’assistant de projet ne rejette plus les dépendances natives, scripts de cycle de vie npm, package exports et Inspector pris en charge; les déclarations de permissions de l’hôte restent vérifiées
* `Amelioration` fs, fs/promises et node:sqlite sont les modules natifs de Node.js : le refus de /proc, /sys, /dev propre au runtime et sa couche de validation des chemins ont disparu, l'accessibilité des fichiers est décidée uniquement par les permissions Android, les API de fichiers asynchrones s'exécutent dans le pool de threads libuv au lieu de bloquer la boucle d'événements, et les erreurs de chemin portent les codes propres à Node.js (ERR_AUTOJS6_FS_NUL_BYTE, ERR_AUTOJS6_FS_PATH_ESCAPE, ERR_AUTOJS6_FS_SCOPED_PATH et ERR_AUTOJS6_SQLITE_FILE_OPERATION_UNSUPPORTED ne sont plus produits par les opérations de fichiers ou de base de données)
* `Amelioration` process.cwd et process.chdir ont le comportement natif de Node.js ; une exécution entre dans son répertoire de travail par un vrai chdir et les chemins relatifs sont résolus par Node.js lui-même
* `Amelioration` ATTACH de node:sqlite (y compris les noms de fichiers par paramètre lié ou expression), VACUUM INTO, les PRAGMA de répertoire et setAuthorizer sont le comportement natif de SQLite ; enableLoadExtension et allowExtension suivent les vérifications d'état propres à Node.js
* `Amelioration` La bibliothèque d'exécution Node.js est construite avec les données ICU complètes (--with-intl=full-icu) : Intl et le formatage régional correspondent au Node.js de bureau et NODE_ICU_DATA n'est plus nécessaire
* `Amelioration` crypto.createPrivateKey et les API de signature acceptent les clés OpenSSL STORE sous forme d'URL file: (le correctif de désactivation propre à Android est retiré)
* `Amelioration` Chaque module intégré de Node.js (cluster, domain, repl, trace_events, wasi, constants, node:sea et les autres) est renvoyé tel que Node l'exporte, directement depuis l'inventaire module.builtinModules de Node ; require, require.resolve, import, process.getBuiltinModule et module.isBuiltin se comportent comme sur le bureau, et seuls worker_threads, child_process, inspector et les modules réseau bruts conservent un verrou au niveau de la requête ; node:wasi s'ouvre avec la suppression de la frontière /proc (sur la libnode actuelle, compilée sans traçage, trace_events lève ERR_TRACE_EVENTS_UNAVAILABLE exactement comme Node)
* `Amelioration` process conserve uniquement la gestion du cycle de vie de exit; dlopen, abort, kill, umask, binding, report, features et les appels d’identité POSIX utilisent Node; os est natif, homedir et tmpdir suivent HOME/TMPDIR et userInfo décrit l’utilisateur Android de l’application
* `Amelioration` supportedAbis dans les informations du plugin (INFO) et les diagnostics d'exécution provient désormais de la meta-data de manifeste org.autojs.plugin.contract.SUPPORTED_ABIS écrite à la compilation (plugin nativeAlignment d'AutoJs6-Gradle-Platform-Versions 1.9.0 ou plus récent), c'est-à-dire toutes les ABI pour lesquelles le plugin est construit et non le split installé ; sans cette meta-data il revient aux ABI empaquetées dans les APK installés, et les constantes ABI écrites à la main sont supprimées
* `Amelioration` Le chargement des modules est le loader CommonJS / ESM natif de Node.js : require, require.resolve, require.cache, require.extensions, Module._resolveFilename, module.register, import(), import.meta et require(esm) sont natifs, et l'entrée s'exécute comme module principal de Node (require.main === module, __filename / process.argv[1] sont des realpaths) ; le runtime n'enregistre que des module.registerHooks servant les modules runtime autojs6:* et les shims de compatibilité (schéma autojs6: ; un paquet npm installé masque toujours le shim du même nom), la barrière de builtins au niveau de la requête, les sources de modules jointes à la requête / matérialisées par le provider / issues de l'instantané TypeScript et la source en ligne de l'entrée ; le linker vm.SourceTextModule, le resolver maison, la réécriture de import() dynamique et les gardes eval / Function disparaissent, et les clés de requête esmEnabled / dynamicImportEnabled n'ont plus d'effet
* `Amelioration` Les Workers natifs prennent en charge l’imbrication, MessagePort, stdio, argv, execArgv et les préchargements hérités; les limites du nombre de Workers et du délai de démarrage sont supprimées, resourceLimits fournit des valeurs par défaut modifiables et WorkerPool ne limite par défaut ni la file ni la durée des tâches; Inspector suit la requête et le build
* `Amelioration` autojs6:bridge.callAutoJs est disponible dans les Workers, même imbriqués, via les MessagePorts parents et conserve les permissions, erreurs, délais et annulations du broker hôte; worker-host-bridge montre la lecture des informations réelles de l’appareil hôte et les déclarations TypeScript couvrent cette API
* `Amelioration` Les points d’entrée et modules importés .ts / .mts / .cts nécessitent toujours le JavaScript produit par le Compiler hôte; la correspondance des instantanés et la compilation à la demande du provider v3 sont conservées. Les erreurs natives de Node remplacent les anciens codes AutoJs6 ESM et d’import dynamique.
* `Amelioration` Les scripts principaux et Workers chargent les addons natifs Android avec Node; process.dlopen et .node sont natifs, sans liste propre au plugin de signatures ou de hachages; native-addon fournit trois ABI alignées sur 16 KB et une compilation NDK/CMake hors ligne; les incompatibilités ABI ou fichiers invalides produisent des erreurs Node telles que ERR_DLOPEN_FAILED, sans NDK ni Python embarqués sur l'appareil
* `Amelioration` Inspector fonctionne dans les versions Debug et Release après autorisation explicite de l'hôte; inspector et inspector/promises conservent les modules natifs Node, avec Session dans les scripts et Workers, écoute sur 127.0.0.1 uniquement et sans attente bloquante; sans autorisation url() renvoie undefined et l'activation ainsi que la création de Session restent désactivées
* `Amelioration` install/update/remove de package_manager acceptent les spécifications npm et exécutent npm fourni dans le projet; npm.cli/exec/runScript/pack utilisent la CLI réelle, avec registry/proxy/cycle de vie selon npm et le .npmrc du projet, sortie en continu et codes d'erreur npm; les fichiers CLI sont préparés au premier appel et les requêtes objet conservent le contrat du stockage local de l'hôte
* `Amelioration` Démarrage des scripts accéléré en évitant les initialisations Web API inutiles et en activant par défaut le cache de compilation portable natif de Node pour les espaces de travail et les Workers; réutilisation entre copies et invalidation des sources modifiées par Node
* `Amelioration` Les métadonnées décrivent les fonctions implémentées sans états provisoires; les exemples UI précisent les services hôtes requis et les champs de marché de plugins et de mises à jour non planifiés sont retirés

# v1.5.7

###### 2026/10/05

* `Amelioration` Les icônes du centre de plugins utilisent les tailles, positions, images claires et sombres et fonds circulaires réglés dans Icon Studio, avec les sources et paramètres permettant de les reproduire
* `Amelioration` Les icônes des informations d'application Android utilisent les illustrations et les fonds clairs et sombres d'Icon Studio, en conservant les images transparentes du centre de plugins et les choix du lanceur

##### Pour plus d'historique des versions

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

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

Les parametres de build viennent de `version.properties`, le SDK minimal actuel est 24 et le SDK cible est 36.

******

### Structure Des Ressources

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/res/values-*/strings.xml
app/src/main/res/raw-*/plugin_instruction.md
app/src/main/assets/doc/CHANGELOG-*.md
```

`strings.xml` contient les descriptions localisees du plugin; `plugin_instruction.md` contient les instructions d'utilisation affichees par l'hote. Les fichiers README et CHANGELOG sont generes depuis des sources JSON par `.python/generate_markdown.py`.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Projet officiel Node.js: https://github.com/nodejs/node
- Plan de build du runtime Node.js: tools/nodejs/runtime-build/README.md
- 16 KB page alignment: [master/docs/16kb.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
