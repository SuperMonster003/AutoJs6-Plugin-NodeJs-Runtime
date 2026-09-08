# v1.3.0

###### Non publié

* `Ajout` Les abonnements du pont Node.js transmettent les événements des capteurs, WebSocket, interfaces, fenêtres flottantes et entrées via les callbacks existants, avec on/once/off, files bornées et compatibilité drainEvents
* `Ajout` Option idleExitMs pour arrêter le processus Node.js inactif et reconnecter le script suivant, avec diagnostic idleForMs et maintien en mémoire par défaut
* `Ajout` Les objets globaux fetch, Request, Response, Headers, FormData et WebSocket utilisent les API Web natives de Node; autojs6:fetch et autojs6:websocket conservent la pile réseau hôte
* `Ajout` Ajout de node:sqlite natif avec contrôle des chemins, CRUD, transactions et sauvegardes; reporters natifs node:test et exemple exécutable, avec zod, cheerio, date-fns, mqtt et ws dans le corpus npm hors ligne
* `Ajout` Entrée console native avec process.stdin / readline et messages JSON par exécution via autojs6:host; transaction postMessage v3 compatible avec les anciens hôtes
* `Correction` Correction de l’accumulation des requêtes et réponses du pont dans les scripts résidents : suppression des requêtes terminées et conservation des 32 dernières réponses avec une taille de diagnostic limitée
* `Correction` Correction des résultats de succès prématurés perdant les erreurs asynchrones natives et les codes de sortie tardifs; fin alignée sur la sortie finale de la boucle Node
* `Amelioration` Réduction de la latence du pont grâce au transport JNI/Binder par défaut et aux réponses via la boucle événementielle Node, avec repli sur fichiers et limites des appels en attente
* `Amelioration` Restauration des exports natifs Node.js pour stream, crypto, timers, util, node:test et les modules associés, avec maintien des limites du système de fichiers et des répertoires hôtes
* `Amelioration` Les workers natifs suivent le parallélisme CPU (huit maximum), les réglages réseau et fichiers de chaque exécution et les plafonds de ressources par requête; aucun délai de tâche de pool par défaut, avec de vrais exemples CPU et WASM
