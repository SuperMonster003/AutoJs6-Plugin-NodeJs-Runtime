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
* `Ajout` Les API Node device exposent les informations actuelles du système, de l’écran, de la batterie et de la mémoire, le réglage de luminosité et le maintien temporaire de l’écran allumé; media règle le volume selon les autorisations Android
* `Ajout` autojs6:events observe les notifications Android, les Toast externes, les touches d’accessibilité et les événements écran ou batterie par callbacks, avec autorisations explicites et nettoyage automatique; events natif de Node reste compatible
* `Ajout` Node app prend en charge les services Android, les broadcasts et la recherche des applications installées; dialogs propose la saisie, les choix et les fenêtres de progression fermées avec le script, et keys expose les actions système d’accessibilité
* `Ajout` Node recorder prend en charge l’enregistrement AAC avec autorisation du microphone, notification, limite de durée et arrêt avec le script; ui.overlay permet la mise à jour des propriétés, le déplacement et les événements transmis
* `Ajout` Les scripts Node peuvent s’exécuter dans deux processus indépendants avec une file FIFO partagée et un routage des annulations et des entrées par exécution
* `Correction` Correction de l’accumulation des requêtes et réponses du pont dans les scripts résidents : suppression des requêtes terminées et conservation des 32 dernières réponses avec une taille de diagnostic limitée
* `Correction` Correction des résultats de succès prématurés perdant les erreurs asynchrones natives et les codes de sortie tardifs; fin alignée sur la sortie finale de la boucle Node
* `Amelioration` Réduction de la latence du pont grâce au transport JNI/Binder par défaut et aux réponses via la boucle événementielle Node, avec repli sur fichiers et limites des appels en attente
* `Amelioration` Restauration des exports natifs Node.js pour stream, crypto, timers, util, node:test et les modules associés, avec maintien des limites du système de fichiers et des répertoires hôtes
* `Amelioration` Les workers natifs suivent le parallélisme CPU (huit maximum), les réglages réseau et fichiers de chaque exécution et les plafonds de ressources par requête; aucun délai de tâche de pool par défaut, avec de vrais exemples CPU et WASM
* `Amelioration` WASI natif reste désactivé pour préserver les limites du système de fichiers; suppression des deux exemples WASI désactivés, avec WebAssembly et les workers WASM toujours disponibles
* `Amelioration` Les exemples OCR demandent le consentement Android et reconnaissent de vraies images; les plugins OCR ou codes-barres indisponibles renvoient une erreur unavailable lisible, et les échecs de reconnaissance restent des erreurs
