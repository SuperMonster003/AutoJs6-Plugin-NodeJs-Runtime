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
* `Ajout` Node.js image.toBytes transfère les pixels PNG ou RGBA par descripteurs de fichiers vers des Buffer natifs, via JNI ou le pont fichier, et libère les pièces jointes après usage, expiration ou fin d’exécution
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
