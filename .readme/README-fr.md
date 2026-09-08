<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-nodejs-runtime-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin de runtime natif Node.js 24.5.0 pour AutoJs6</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/commit/c30d958730a23614c4e430a4bcfe64623a87d4ed"><img alt="Created" src="https://img.shields.io/date/1783047178?color=2e7d32&label=Created"/></a>
    <br>
    <a href="https://developer.android.com/studio/archive"><img alt="Android Studio" src="https://img.shields.io/badge/Android%20Studio-2023.3+-B64FC8"/></a>
    <a href="https://www.jetbrains.com/idea/download/other.html"><img alt="IntelliJ IDEA" src="https://img.shields.io/badge/IntelliJ%20IDEA-2023.3+-EE4677"/></a>
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

Le plugin AutoJs6 Node.js Runtime fournit a AutoJs6 un runtime natif Node.js 24.5.0 embarque pour les scripts Node.js et les taches de runtime de plugins.

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

- Slot de runtime: `node24_5`.
- ID de plugin: `nodejs`, moteur: `nodejs`.
- Action du service runtime: `org.autojs.plugin.nodejs.RUNTIME`.
- Bibliotheques natives de runtime: `libnode.so` et `libautojs6-node.so`.
- ABI: `arm64-v8a`, `armeabi-v7a`, `x86_64` et `universal`.
- Système de fichiers: les chemins autorisés par Android sont accessibles; `/proc`, `/sys` et `/dev` sont des limites strictes.
- TypeScript: accepte la sortie de l'hôte et peut demander la compilation provider-v3 des fichiers créés pendant l'exécution; le TypeScript brut direct renvoie `ERR_AUTOJS6_TYPESCRIPT_COMPILER_REQUIRED`.
- Linker ESM: `vm.SourceTextModule` / `vm.SyntheticModule`, avec live bindings natifs et dépendances cycliques.
- Capacites: execution synchrone de scripts, bundle transport, runtime natif embarque, agent de capacites hote, host capability live bridge.

******

### Historique Des Versions

******

# v1.3.0

###### Non publié

* `Ajout` Les abonnements du pont Node.js transmettent les événements des capteurs, WebSocket, interfaces, fenêtres flottantes et entrées via les callbacks existants, avec on/once/off, files bornées et compatibilité drainEvents
* `Ajout` Option idleExitMs pour arrêter le processus Node.js inactif et reconnecter le script suivant, avec diagnostic idleForMs et maintien en mémoire par défaut
* `Ajout` Les objets globaux fetch, Request, Response, Headers, FormData et WebSocket utilisent les API Web natives de Node; autojs6:fetch et autojs6:websocket conservent la pile réseau hôte
* `Ajout` Ajout de node:sqlite natif avec contrôle des chemins, CRUD, transactions et sauvegardes; reporters natifs node:test et exemple exécutable, avec zod, cheerio, date-fns, mqtt et ws dans le corpus npm hors ligne
* `Ajout` Entrée console native avec process.stdin / readline et messages JSON par exécution via autojs6:host; transaction postMessage v3 compatible avec les anciens hôtes
* `Ajout` Les sessions de capture Node.js utilisent le consentement Android et le service de premier plan existant, avec des handles d’image, la sauvegarde PNG/JPEG/WebP et la libération à l’arrêt ou à la fin du script
* `Ajout` Les handles d’image Node.js prennent en charge le découpage, le redimensionnement, les niveaux de gris, le seuillage et la recherche de modèles ou de couleurs via le moteur hôte, avec des images indépendantes et leur libération en fin d’exécution
* `Correction` Correction de l’accumulation des requêtes et réponses du pont dans les scripts résidents : suppression des requêtes terminées et conservation des 32 dernières réponses avec une taille de diagnostic limitée
* `Correction` Correction des résultats de succès prématurés perdant les erreurs asynchrones natives et les codes de sortie tardifs; fin alignée sur la sortie finale de la boucle Node
* `Amelioration` Réduction de la latence du pont grâce au transport JNI/Binder par défaut et aux réponses via la boucle événementielle Node, avec repli sur fichiers et limites des appels en attente
* `Amelioration` Restauration des exports natifs Node.js pour stream, crypto, timers, util, node:test et les modules associés, avec maintien des limites du système de fichiers et des répertoires hôtes
* `Amelioration` Les workers natifs suivent le parallélisme CPU (huit maximum), les réglages réseau et fichiers de chaque exécution et les plafonds de ressources par requête; aucun délai de tâche de pool par défaut, avec de vrais exemples CPU et WASM
* `Amelioration` WASI natif reste désactivé pour préserver les limites du système de fichiers; suppression des deux exemples WASI désactivés, avec WebAssembly et les workers WASM toujours disponibles

# v1.2.0

###### 2026/08/29

* `Ajout` Ajout de `capabilities()` à la façade `mediainfo` et de la sélection explicite des snapshots v1/v2 du plugin dans son entrée appelable et `read()`; l'omission du schéma continue de renvoyer le snapshot Node v1 existant appartenant à l'hôte
* `Ajout` Ajout du module-source provider v3 pour compiler à la demande les fichiers `.ts/.mts/.cts` créés pendant l'exécution via des PFD bornés par octets et SHA-256, avec un budget de compilation indépendant de 30 s, le refus stable des sorties de chemin, liens symboliques et ambiguïtés, ainsi que les diagnostics TypeScript et la remise en correspondance Source Map de l'hôte
* `Ajout` Activation d'un accès au système de fichiers comparable au bureau dans les limites des autorisations Android, avec refus permanent de `/proc`, `/sys` et `/dev`
* `Ajout` Ajout de `accessibility.swipe` et `accessibility.gesture` derrière la capacité dédiée `accessibility.gesture`
* `Ajout` Ajout d’un délai maximal couvrant l’attente et l’exécution des scripts, avec l’erreur `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sans délai défini, les scripts peuvent toujours s’exécuter indéfiniment
* `Ajout` Activation par défaut de `dgram` (UDP) et `http2`, avec une erreur explicite de désactivation pour `trace_events`
* `Ajout` Ajout du débogage local `inspector`, activé explicitement dans les versions Debug, avec écoute uniquement sur localhost et connexion via `adb forward`
* `Ajout` Ajout d’une activation sans interface protégée par la permission du plugin hôte, des descriptions du centre de plugins et désactivation de la sauvegarde des données
* `Correction` Échec fermé du TypeScript brut sans sortie de compilation fournie par l'hôte, mappage des imports dynamiques de snapshot et normalisation des piles générées/importées
* `Correction` Remplacement de l'adaptateur ESM partiel basé sur des snapshots par le linker natif V8, corrigeant les exports mutables non mis à jour dans les réexports cycliques
* `Correction` Correction des imports ESM des façades de compatibilité AutoJs6 et de la détection de suffixes TypeScript inexistants, tout en préservant la priorité des paquets npm installés
* `Amelioration` Suppression du fallback legacy d'effacement TypeScript fondé sur des regex et de son commutateur de requête; les `.ts/.mts/.cts` bruts exigent désormais toujours la sortie du compilateur hôte
* `Amelioration` Alignement du contrat v2 hôte/plugin, des manifestes de capacités et de la frontière de responsabilité du runtime plugin-only
* `Amelioration` Centralisation dans le dépôt du plugin des exemples Node.js, des déclarations TypeScript, de l'assistant de projet, des valeurs par défaut du runtime et des contrôles d'alignement avec l'hôte, avec suppression des options Gradle et des ressources de développement dupliquées côté hôte
* `Amelioration` Extension des tests npm à 15 paquets, avec axios, express et les paquets ESM-only nanoid, p-limit et yocto-queue
* `Amelioration` Le champ de requête `executionMode` fait désormais autorité pour le mode de cycle de vie; le champ sans effet `runtimeAdapter` est obsolète
* `Amelioration` Suppression de dix anciens exemples affichant uniquement des états fixes et indication dans les types de l’absence de fournisseurs hôtes pour la capture d’écran, l’analyse d’images et l’enregistrement audio

# v1.1.0

###### 2026/08/18

* `Ajout` Ajout du flux stdout/stderr en direct et de l'annulation coopérative via `node::Stop`
* `Ajout` Remplacement du rejet BUSY par une file série bornée à trois attentes et ajout du cycle de vie des scripts résidents de longue durée
* `Ajout` Activation par défaut des modules réseau Node natifs, de `worker_threads` et de `child_process`, avec validation de dix paquets npm JavaScript purs courants
* `Amelioration` Ajout des espaces de travail direct-run et de la négociation tolérante v1..v2 du provider de sources, avec codes d'erreur concis et piles JavaScript

##### Pour plus d'historique des versions

* [CHANGELOG.md](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/.changelog/CHANGELOG-fr.md)

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
