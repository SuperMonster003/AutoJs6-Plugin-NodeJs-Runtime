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

# v1.2.0

###### 2026/08/29

* `Ajout` Ajout du module-source provider v3 pour compiler à la demande les fichiers `.ts/.mts/.cts` créés pendant l'exécution via des PFD bornés par octets et SHA-256, avec un budget de compilation indépendant de 30 s, le refus stable des sorties de chemin, liens symboliques et ambiguïtés, ainsi que les diagnostics TypeScript et la remise en correspondance Source Map de l'hôte
* `Ajout` Activation d'un accès au système de fichiers comparable au bureau dans les limites des autorisations Android, avec refus permanent de `/proc`, `/sys` et `/dev`
* `Ajout` Ajout de `accessibility.swipe` et `accessibility.gesture` derrière la capacité dédiée `accessibility.gesture`
* `Correction` Échec fermé du TypeScript brut sans sortie de compilation fournie par l'hôte, mappage des imports dynamiques de snapshot et normalisation des piles générées/importées
* `Correction` Remplacement de l'adaptateur ESM partiel basé sur des snapshots par le linker natif V8, corrigeant les exports mutables non mis à jour dans les réexports cycliques
* `Correction` Correction des imports ESM des façades de compatibilité AutoJs6 et de la détection de suffixes TypeScript inexistants, tout en préservant la priorité des paquets npm installés
* `Amelioration` Suppression du fallback legacy d'effacement TypeScript fondé sur des regex et de son commutateur de requête; les `.ts/.mts/.cts` bruts exigent désormais toujours la sortie du compilateur hôte
* `Amelioration` Alignement du contrat v2 hôte/plugin, des manifestes de capacités et de la frontière de responsabilité du runtime plugin-only
* `Amelioration` Centralisation dans le dépôt du plugin des exemples Node.js, des déclarations TypeScript, de l'assistant de projet, des valeurs par défaut du runtime et des contrôles d'alignement avec l'hôte, avec suppression des options Gradle et des ressources de développement dupliquées côté hôte

# v1.1.0

###### 2026/08/18

* `Ajout` Ajout du flux stdout/stderr en direct et de l'annulation coopérative via `node::Stop`
* `Ajout` Remplacement du rejet BUSY par une file série bornée à trois attentes et ajout du cycle de vie des scripts résidents de longue durée
* `Ajout` Activation par défaut des modules réseau Node natifs, de `worker_threads` et de `child_process`, avec validation de dix paquets npm JavaScript purs courants
* `Amelioration` Ajout des espaces de travail direct-run et de la négociation tolérante v1..v2 du provider de sources, avec codes d'erreur concis et piles JavaScript

# v1.0.0

###### 2026/07/18

* `Ajout` Ajout du service de plugin runtime Node.js avec ID de plugin `nodejs`, moteur `nodejs` et slot de runtime `node24_5`
* `Ajout` Fourniture du runtime natif Node.js 24.5.0 via `libnode.so` et `libautojs6-node.so`
* `Ajout` Execution du runtime Node.js dans un processus persistant independant qui reutilise l'etat Node/V8 global au processus tout en creant un isolate et un Environment neufs pour chaque execution
* `Ajout` Ajout de la decouverte des informations du plugin via `org.autojs.plugin.INFO` et de l'appel du runtime via `org.autojs.plugin.nodejs.RUNTIME`
* `Ajout` Prise en charge du code CommonJS/ESM, des sources de modules, du repertoire de travail, de la racine de bac a sable, des variables d'environnement, des resultats stdout/stderr et du prechauffage du runtime
* `Ajout` Prise en charge du transport d'archive d'espace de travail v2 limite a la requete, avec mappage explicite des entrees, execution dans un espace prive du plugin, reecriture des sorties et manifestes tombstone de suppression, sans analyser le sandbox hote
* `Ajout` Admission d'une seule execution active sans file, avec contre-pression `ERR_AUTOJS6_NODE_PLUGIN_BUSY` et annulation par redemarrage du processus
* `Ajout` Ajout de l'agent de capacites hote et du live bridge avec des modules de runtime comme `autojs6:host-app-info`, `autojs6:device-info`, `autojs6:engine-info`, `autojs6:lifecycle-config` et `autojs6:bridge-permissions`
* `Ajout` Ajout de builds APK separes par ABI pour `arm64-v8a`, `armeabi-v7a`, `x86_64` et un APK `universal`
* `Ajout` `libc++_shared.so` empaquete avec les bibliotheques du runtime Node.js dans les sorties APK separees et `universal`
* `Ajout` Ajout des projets `sample/nodejs`, d'un outil de diagnostic du resolveur Node et d'outils de verification du plan de build du runtime
* `Ajout` Ajout des metadonnees du plugin, des instructions d'utilisation, du README et des ressources CHANGELOG localisees en espagnol, francais, russe, arabe, japonais, coreen, anglais, chinois simplifie, chinois traditionnel de Hong Kong et chinois traditionnel de Taiwan
* `Correction` L'annulation par redemarrage du processus pouvait valider un instantane partiel de l'espace de travail pendant le recyclage du processus d'execution
* `Correction` Les descripteurs de fichier du transport d'archive d'espace de travail pouvaient fuir lors d'un echec de validation du contrat de requete ou de materialisation de l'espace de travail, car leur propriete n'etait pas finalisee sur tous les chemins de sortie
* `Amelioration` Metadonnees et diagnostics du contrat R5 pour ABI/capacites, etat du runtime persistant, admission, annulation et attribution au processus independant
* `Amelioration` Ajout de diagnostics monotones par phase pour la construction de la source d'execution, le bootstrap, l'execution du script, la creation et la recuperation du resultat et le nettoyage par execution, avec distinction des etats ignore et non applicable

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
