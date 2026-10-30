<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de runtime natif Node.js 26.13.0 pour AutoJs6</p>

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

Le plugin AutoJs6 Node.js Runtime fournit a AutoJs6 un runtime natif Node.js 26.13.0 embarque pour les scripts Node.js et les taches de runtime de plugins. Sous Android 17 ou version ultérieure, autorisez les appareils à proximité avant de permettre ce plugin dans le centre de plugins AutoJs6. Cette autorisation se gère aussi dans les paramètres du plugin. Sans autorisation, le plugin reste désactivé et le démarrage automatique est ignoré sans message. Cette autorisation appartient au plugin et est indépendante de celle de AutoJs6.

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
- API hôtes database/sqlite pour Bun et les Workers Node.js, avec requêtes paramétrées, transactions et persistance.
- Mesures et abonnements aux capteurs pour Bun et les Workers Node.js, avec annulation et libération automatique des écouteurs.
- Shell hôte avec flux, autorisation Shizuku et nettoyage des processus pour Bun, Node.js et les Workers.

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

- Slot de runtime: `node26_13`.
- ID de plugin: `nodejs`, moteur: `nodejs`.
- Action du service runtime: `org.autojs.plugin.nodejs.RUNTIME`.
- Bibliotheques natives de runtime: `libnode.so`, `libautojs6-node.so` et `libnodexe.so`.
- Intl : ICU 78 avec les données régionales complètes (`--with-intl=full-icu` depuis 1.6.0 ; de 1.5.1 à 1.5.7, small-icu en anglais uniquement) ; `Intl`, le formatage régional, les échappements de propriétés Unicode dans les expressions régulières et la sortie d'erreur de Node sur stderr correspondent au Node de bureau, sans `NODE_ICU_DATA`.
- Temporal : depuis 2.0.0, l'objet global `Temporal` intégré à Node.js 26 gère dates, heures, fuseaux horaires et calendriers sans bibliothèque supplémentaire.
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
- Branche Node.js 24 : le plugin 1.x (Node.js 24.21.0) est maintenu sur la branche `release/1.x`, uniquement avec des mises à jour de sécurité, jusqu'au 2028-04-30 (fin de vie de Node.js 24).

******

### Historique Des Versions

******

# v2.0.0

###### 2026/10/30

* `Note` À partir du plugin 2.0.0, les scripts s'exécutent sous Node.js 26, qui supprime certaines API obsolètes dans Node.js 24 (par exemple SlowBuffer, l'option recursive de fs.rmdir, les constantes comme fs.F_OK, assert.CallTracker et node:_stream_wrap); les scripts qui les utilisent doivent être adaptés. Pour rester sur Node.js 24, désinstallez la version 2.x puis installez une version 1.x (mises à jour de sécurité uniquement, maintenue jusqu'au 2028-04-30)
* `Note` Les addons natifs (.node) compilés directement contre V8 doivent être recompilés pour Node.js 26; les addons Node-API ne sont pas concernés
* `Ajout` L'objet global Temporal intégré à Node.js 26 gère les dates, les heures, les fuseaux horaires et les calendriers sans bibliothèque supplémentaire
* `Ajout` Modules app, shell et shizuku pour Node.js et Workers conformes aux scripts AutoJs6, y compris le lancement, la désinstallation et l'arrêt des applications dupliquées (clones); les commandes passent par Shizuku, root ou le shell ordinaire, selon le premier disponible, et renvoient des Promises
* `Ajout` Modules d'informations système pour Node.js et Workers conformes aux scripts AutoJs6 : autojs, installer, memory, settings, sysprops, power_manager et autojs6:system (setScreenMetrics met aussi à l'échelle les clics et gestes d'accessibilité), ainsi que les interrupteurs et canaux de notification, dialogs.build, engines pour lister et arrêter les scripts de l'hôte, plugins.extend et plugins.tools
* `Ajout` Modules device, sensors et media pour Node.js et Workers conformes aux scripts AutoJs6 : modèle de l'appareil, vérifications d'écran et de fabricant, toutes les options de vibration et d'écran allumé, événements des capteurs, lecture de musique et analyse des médias; la lecture des identifiants de l'appareil exige la nouvelle capacité device.identifiers
* `Ajout` colors/Color, files, zip, mime et l'enregistreur de durées pour Node.js et Workers conformes aux scripts AutoJs6; dayjs, pangu.js, cheerio et banana-i18n sont disponibles via autojs6:global, et les requêtes axios des Workers sont traitées par le thread principal
* `Correction` Les proxys Packages / java / android installés par rhino.install() échouaient à la lecture des champs statiques, à l'appel de describe(), à l'appel de méthodes statiques ou à la création d'objets avec new
* `Correction` shell.exec échouait parfois avec ERR_STREAM_DESTROYED lorsque la commande se terminait rapidement
* `Amelioration` Node.js 26 ne fournit plus corepack; le plugin continue d'embarquer corepack 0.36.0 et son usage dans le terminal ne change pas
* `Amelioration` Les déclarations TypeScript de worker_threads couvrent les options, l'annulation, les délais et les diagnostics de WorkerPool, typées sans @types/node
* `Amelioration` La bibliothèque Node.js est compilée avec le NDK r29 et garde l'alignement de page 16 KB sur les trois ABI; le script de recette des versions exécute la régression complète sur appareil, écrit un rapport JSON et mesure le démarrage à froid
* `Dependance` Mise à jour de Node.js 24.21.0 -> 26.13.0 (V8 14.6, OpenSSL 3.5.9, ICU 78.3) avec des bibliothèques Android compilées depuis les sources pour les trois ABI
* `Dependance` Mise à jour de npm 11.19.0 -> 11.20.0

# v1.32.0

###### 2026/10/29

* `Ajout` Contrôle des tâches ai.agent, événements et contexte/résultats des scripts enregistrés pour Node.js, Bun et Workers, avec autorisation explicite des applications empaquetées
* `Ajout` Modules d'outils util, species, s13n, fmt/cvt et jsox et fonctions globales de données pour Node.js et Workers, conformes aux scripts AutoJs6 et importables via des noms comme autojs6:util
* `Correction` web.cancelFileChooser() appelé juste après chooseFiles() pouvait laisser le sélecteur de fichiers ouvert
* `Amelioration` Les résultats et erreurs de jsox et de la conversion d'unités d'octets correspondent désormais aux scripts AutoJs6, et jsox.extend/extendAll étendent directement Math, Array et Number du thread courant

# v1.31.0

###### 2026/10/22

* `Ajout` Génération de texte IA, flux, catalogue de modèles et sessions persistantes pour Node.js, Bun et Workers, avec annulation et consentement par application dans 3-Stone AI

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

Les parametres de build viennent de `version.properties`, le SDK minimal actuel est 24 et le SDK cible est 37.

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
