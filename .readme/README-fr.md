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

Le plugin AutoJs6 Node.js Runtime fournit a AutoJs6 un runtime natif Node.js 24.21.0 embarque pour les scripts Node.js et les taches de runtime de plugins.

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

- Slot de runtime: `node24_21`.
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

# v1.4.2

###### 2026/09/13

* `Correction` Indiquer uniquement les ABI natives présentes dans l’APK installé
* `Correction` Utiliser des dates de compilation en anglais indépendamment de la langue de la machine

# v1.4.1

###### 2026/09/13

* `Amelioration` Vérification à la compilation de l'alignement des pages de 16 KB des bibliothèques natives 64 bits, avec contrôle du contrat manifest et rapports JSON
* `Amelioration` Harmonisation de l'activation, des métadonnées, de la documentation traduite et de la collecte des APK signés

# v1.4.0

###### 2026/09/10

* `Ajout` Les requêtes MediaInfo prennent en charge streamNumber à partir de 0, countGet et infoKind pour les unités, descriptions et noms lisibles; Rhino et Node conservent TEXT sur le premier flux par défaut et négocient les capacités du plugin
* `Correction` Les chemins MediaInfo et image acceptent les chemins absolus, les dossiers parents et les noms valides; les enregistrements suivent les mêmes droits Android avec un hôte mis à jour
* `Correction` Les erreurs de capacité du pont indiquent les déclarations node.permissions manquantes sans exiger le profil pro_compat_opt_in réservé au diagnostic
* `Correction` Le validateur ne rejette plus les chemins fs absolus ou les dossiers parents avec FS_OUTSIDE_SCOPE; Android détermine l’accès réel aux fichiers
* `Amelioration` Projets autonomes pour les événements Android et un enregistrement audio de trois secondes, avec déclarations et étapes de validation manuelle pour la capture, l'OCR, les touches physiques et MediaInfo
* `Amelioration` La recherche dans les captures, l'OCR et l'enregistrement AAC de trois secondes ont passé la validation manuelle sur appareil; les exemples et extraits de parité Pro utilisant les mêmes appels sont stables
* `Amelioration` L’exemple de validation des événements indique de désactiver temporairement le raccourci d’arrêt par volume haut; l’hôte mis à jour lit node.timeoutMs dans project.json pour permettre les attentes supérieures à cinq secondes

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-NodeJs-Runtime/blob/master/docs/16kb.md)
