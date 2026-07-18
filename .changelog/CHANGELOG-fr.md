******

### Historique Des Versions

******

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
