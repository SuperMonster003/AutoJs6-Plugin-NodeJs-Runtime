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
- Activation par défaut de `dgram` (UDP) et `http2`, avec une erreur explicite de désactivation pour `trace_events`
- Ajout du débogage local `inspector`, activé explicitement dans les versions Debug, avec écoute uniquement sur localhost et connexion via `adb forward`
- Prend en charge le code CommonJS/ESM, les sources de modules, le repertoire de travail, la racine de bac a sable, les variables d'environnement et les resultats stdout/stderr.
- Utilise le linker natif V8 pour les entrées ESM et dynamic `import()`, en préservant les dépendances cycliques, les exports mutables et les live bindings réexportés; CommonJS `require(esm)` conserve sa frontière d'interop synchrone.
- Fournit un accès au système de fichiers comparable à celui du bureau, limité par les autorisations Android de l'application du plugin; `/proc`, `/sys` et `/dev` sont toujours refusés.
- Exécute la sortie TypeScript fournie par l'hôte et peut demander une compilation provider-v3 pour les `.ts`/`.mts`/`.cts` de projet créés pendant l'exécution; l'envoi brut direct échoue toujours de façon fermée, sans fallback d'effacement ni commutateur de compatibilité.
- Fournit un agent de capacites hote et un live bridge avec des modules de runtime comme `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` et `autojs6:bridge-permissions`.
- Inclut les projets `sample/nodejs` et l'inventaire des capacités API hôte `docs/HOST-API.md`.
- Les metadonnees du plugin, les instructions d'utilisation, le README et le CHANGELOG sont localises en espagnol, francais, russe, arabe, japonais, coreen, anglais, chinois simplifie, chinois traditionnel de Hong Kong et chinois traditionnel de Taiwan.
- Fournit le lanceur de terminal multi-appel `libnodexe.so` et une archive npm / corepack déclarés via les meta-data manifest `NODE_CLI_*`, afin que le terminal AutoJs6 (hôte 6.8.0+) puisse exécuter `node`, `npm`, `npx`, `corepack`, `yarn` et `pnpm` dans un shell sous son propre uid.

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
- Intl : ICU 78 avec les seules données de locale anglaises (`--with-intl=small-icu`) ; `Intl`, les échappements de propriétés Unicode dans les expressions régulières et la sortie d'erreur propre de Node sur stderr fonctionnent dans le terminal AutoJs6, et `NODE_ICU_DATA` peut pointer vers un fichier de données ICU complet.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` et `universal`.
- Système de fichiers: les chemins autorisés par Android sont accessibles; `/proc`, `/sys` et `/dev` sont des limites strictes.
- TypeScript: accepte la sortie de l'hôte et peut demander la compilation provider-v3 des fichiers créés pendant l'exécution; le TypeScript brut direct renvoie `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- Linker ESM: `vm.SourceTextModule` / `vm.SyntheticModule`, avec live bindings natifs et dépendances cycliques.
- Capacites: execution synchrone de scripts, bundle transport, runtime natif embarque, agent de capacites hote, host capability live bridge.
- Les flux de fichiers, les flux gzip/deflate/Brotli et l'exécution VM de base sont stables; le Debug Inspector explicitement activé est stable dans son périmètre localhost.
- L'exécution intégrée n'affiche plus les avis ExperimentalWarning; les événements warning, avertissements ordinaires, dépréciations et erreurs restent disponibles. La stabilité des API Node et les valeurs par défaut du terminal sont inchangées.

******

### Historique Des Versions

******

# v1.5.5

###### 2026/09/17

* `Correction` La version et l'empreinte du catalogue de capacités rapportées par runtimeInfo dérivent désormais du runtime kit, corrigeant les anciennes valeurs 1.5.0 encore rapportées par 1.5.4
* `Correction` worker_threads accepte new Worker(code, { eval: true }) comme Node au lieu de traiter le code comme un chemin de script
* `Amelioration` La route de compilation TypeScript de l'hôte devient stable dans le catalogue de capacités et les métadonnées retirées de legacy stripping sont supprimées; le catalogue de cycle de vie ne liste que les modes d'exécution exécutables, en retirant la surface packaged_long_running et les noms réservés node_sandboxed / worker_computation; les métadonnées de transport, d'admission et d'annulation correspondent au runtime réel
* `Amelioration` Les diagnostics autojs6:profile décrivent le runtime réel au lieu des marqueurs historiques partial / reserved / deferred : accès aux fichiers borné par Android, sous-ensemble process, canaux worker, pool de processus et décisions WASI / native addon
* `Amelioration` Audit des exemples : packaged-esm, packaged-dynamic-import, require-esm, wasm-basic, wasm-plugin et desktop-parity-suite passent en stable ; les deux suites de parité exécutent réellement leurs extraits au lieu d'imprimer le texte du catalogue ; compile-cache est marqué non applicable et l'exemple package-install, purement métadonnées, est supprimé
* `Amelioration` Le chargement des modules suit l'accès aux fichiers Android comme Node : require, import et Worker acceptent les chemins absolus, les cibles dans les répertoires parents et les URL file: (/proc, /sys et /dev restent refusés) ; la recherche node_modules reste ancrée à l'espace de travail
* `Amelioration` Les messages Worker et les observateurs fs suivent les limites natives de Node : les plafonds de 64 Ko par message et 32 en file, ainsi que les quotas de 16 observateurs / 64 événements par seconde, sont supprimés ; seule la mémoire Android les borne
* `Amelioration` Dans les workers, process.exit() termine uniquement le thread du worker comme dans Node et process.getBuiltinModule() suit la liste des builtins autorisés du worker au lieu d'être désactivé
* `Amelioration` Les workers résolvent les spécificateurs de paquet nus via le node_modules de l'espace de travail comme dans Node (conditions exports, main, index, auto-référence) au lieu de les refuser
* `Amelioration` L'import() dynamique dans les workers passe par le chargeur ESM partiel du worker (chemins locaux, URL file:, paquets de l'espace de travail, builtins autorisés, with { type: "json" }) au lieu d'être refusé
* `Amelioration` L'exemple host-events passe en stable après la validation manuelle de la touche physique et de l'accès aux notifications
* `Amelioration` L'exemple scheduled-node-task affiche sa politique de cycle de vie et peut conserver la tâche planifiée pour une exécution réelle de WorkManager ; le mode scheduled obtient une régression côté plugin
* `Amelioration` Le mode d'exécution scheduled passe à available dans le catalogue de capacités après une exécution réelle du runner planifié WorkManager de l'hôte via le plugin
* `Amelioration` media.play() renvoie une session de lecture (pause/resume/seekTo/stop/status) via le service musical de scripts de l'hôte, et le nouveau module media_store expose un accès délimité à MediaStore (capabilities/query/get/insert/update/delete/scanFile/exportFile) protégé par les capacités media.playback / media.library / media.library.mutate ; autojs6:compat.media gagne les alias de style Rhino playMusic ; les deux nécessitent la version d'hôte correspondante
* `Amelioration` Les exemples media-playback / media-library passent en stable après l'acceptation manuelle avec les fournisseurs média de l'hôte fusionnés ; l'instantané 1.5.5 du catalogue de capacités est publié sous releases/nodejs-capability-catalog et la tâche d'alignement avec l'hôte le référence désormais
* `Amelioration` L'enveloppe fs abandonne ses propres restrictions d'options : les options fs / fd hérité / flags des flux, watch({ recursive: true }), les filtres cp asynchrones (cpSync conserve ERR_INVALID_RETURN_VALUE de Node), les motifs glob absolus / de répertoire parent / '!' littéral avec tableaux exclude, type / encoding de readableWebStream et les constructeurs Stats / Dirent / Dir suivent désormais Node 24 natif ; filesystemProfile.advancedApis.recursiveWatch signale native
* `Amelioration` readdir / opendir récursifs deviennent natifs (fin du plafond de 4096 entrées et de la vérification realpath par entrée ; readdir('/') liste les noms proc/sys/dev comme Node), readlink / chmod / chown / utimes acceptent les chemins absolus et chmod suit les liens symboliques, et les erreurs de politique fs se réduisent à ERR_AUTOJS6_FS_NUL_BYTE / ERR_AUTOJS6_FS_PATH_ESCAPE (limite dure, partagée avec le chargeur) / ERR_AUTOJS6_FS_SCOPED_PATH tandis que les échecs fs ordinaires ne gardent que leur code Node
* `Amelioration` le runtime abandonne ses propres budgets de sources de modules (16 MiB par module, 64 MiB au total, 8192 modules, nombre de requêtes au provider), une entrée CommonJS reçoit des __filename / require.main.filename / process.argv[1] absolus comme dans Node avec require.main.id '.', et fs.mkdtemp* devient natif : il renvoie le préfixe tel qu'écrit par l'appelant plus le suffixe dans l'encodage demandé et ne rejette plus un répertoire parent qui est un lien symbolique
* `Amelioration` node:sqlite confie les opérations de fichiers du SQL à SQLite natif : ATTACH avec un nom de fichier littéral (y compris les URI file:) est vérifié par l'autorisateur SQLite contre la limite /proc, /sys, /dev au lieu d'analyser le texte SQL, les cibles de VACUUM INTO passent la même vérification via l'ATTACH interne de SQLite, les PRAGMA de répertoire ne sont plus interceptés, les chaînes URI file: et la base temporaire vide s'ouvrent, et setAuthorizer() se compose avec cette vérification (seul ATTACH avec un nom de fichier lié ou calculé reste refusé)
* `Amelioration` Les quotas du pont sont confiés à l'hôte : les appels au-delà de la fenêtre maxPendingBridgeCalls de autojs6:bridge-limits attendent désormais dans l'ordre d'arrivée au lieu d'échouer avec ERR_AUTOJS6_BRIDGE_RESOURCE_LIMIT, le runtime ne plafonne plus lui-même le nombre de handles d'image, de requêtes fetch contrôlées simultanées ni de connexions WebSocket contrôlées (le broker de l'hôte continue d'appliquer sa politique), et require('fetch').policy.maxConcurrentRequests / require('websocket').policy.maxConnections sont retirés
* `Amelioration` Les façades pontées fetch / WebSocket / axios confient leurs plafonds à l'hôte : délais, taille de réponse, nombre de redirections, tailles de message et de file et méthode HTTP sont transmis au fournisseur de l'hôte sans écrêtage (sa propre politique s'applique), le runtime suit jusqu'à 20 redirections comme Node, et les champs hard*/tailles par défaut retirés de policy sont remplacés par limitsEnforcedBy
* `Amelioration` opendir renvoie le fs.Dir paresseux natif de Node (bufferSize, encoding et recursive vont à l'opendir natif, ENOENT / ENOTDIR / ERR_DIR_CLOSED sont les erreurs de Node ; seuls dir.path et parentPath gardent l'écriture de l'appelant), et le garde-fou du runtime contre la suppression de la racine d'accès au système de fichiers est retiré : rm / rmdir de la racine relèvent de Node et d'Android comme tout autre chemin (les codes de politique fs se limitent à FS_NUL_BYTE et FS_PATH_ESCAPE)
* `Amelioration` Le transport du provider hôte adopte les tailles par source / totale / nombre de requêtes que l'hôte annonce via getNativeDiagnostics() (les valeurs intégrées 16 MiB / 64 MiB / 139264 ne servent que de repli), les corps de réponse du fetch relayé arrivent par descripteur de fichier (bodyTransport "pfd") et ne sont donc bornés que par la politique maxResponseBytes de l'hôte et non par la taille de transaction Binder, et une réponse de l'hôte dépassant la taille de transaction Binder est signalée aussitôt par ERR_AUTOJS6_BRIDGE_PROVIDER_FAILED (branche hôte node-m20-2-binder-body-pfd) au lieu d'un délai d'attente du pont
* `Amelioration` Les corps de requête du fetch relayé et les messages WebSocket de plus de 256 Kio parviennent désormais à l'hôte par un descripteur de fichier en lecture seule (bodyTransport / messageTransport "pfd") au lieu d'un JSON base64 en ligne, sur les hôtes annonçant bridgeRequestBinaryTransport=pfd (branche hôte node-m20-2-binder-body-pfd), de sorte que les envois ne sont bornés que par la politique de requête de l'hôte (64 Mio) et maxMessageBytes et non par la taille de transaction Binder
* `Amelioration` Les réponses du fetch contrôlé exposent désormais l'URL finale du fournisseur via Response.url (ResponseInit ne porte pas d'url, la valeur était donc toujours une chaîne vide)
* `Amelioration` L'interopérabilité Java transmet désormais chaque appel à la liste blanche déclarative de l'hôte (classe → constructeurs, méthodes statiques et d'instance, champs ; l'hôte les exécute par réflexion avec des primitives JSON et des handles d'objets), le runtime abandonne sa propre table de classes et relit celle publiée par l'hôte comme java.policy, getStatic() / describe() sont ajoutés et les exceptions levées par le membre lui-même sont signalées par ERR_AUTOJS6_JAVA_CALL_FAILED
* `Amelioration` La priorité npm sur les modules du runtime se limite désormais aux modules qui remplacent de vrais paquets npm (axios, colors, mime, nanoid, opencc, undici) : un paquet homonyme dans node_modules ne remplace plus les façades java, fetch, websocket, device ni les autres façades AutoJs6, require.resolve suit la même règle que require, tout nom de module du runtime peut être importé depuis ESM, autojs6:profile publie la règle sous moduleResolutionProfile et le proxy Packages de Rhino gagne getStatic() / describe()

# v1.5.4

###### 2026/09/17

* `Correction` L'exécution intégrée n'affiche plus les avis ExperimentalWarning; les événements warning, avertissements ordinaires, dépréciations et erreurs restent disponibles. La stabilité des API Node et les valeurs par défaut du terminal sont inchangées
* `Amelioration` Les flux de fichiers, les flux gzip/deflate/Brotli et l'exécution VM de base sont stables; le Debug Inspector explicitement activé est stable dans son périmètre localhost

# v1.5.3

###### 2026/09/16

* `Amelioration` Autorisation du réseau local sous Android 17 intégrée au parcours activation et aux paramètres du plugin, sans page du lanceur; sans autorisation, le plugin reste désactivé et le démarrage automatique est silencieux
* `Amelioration` Cibler Android 17 (SDK 37) avec des autorisations réseau local propres au plugin et une aide à la récupération

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
