# v1.3.0

###### Non publié

* `Ajout` Les abonnements du pont Node.js transmettent les événements des capteurs, WebSocket, interfaces, fenêtres flottantes et entrées via les callbacks existants, avec on/once/off, files bornées et compatibilité drainEvents
* `Correction` Correction de l’accumulation des requêtes et réponses du pont dans les scripts résidents : suppression des requêtes terminées et conservation des 32 dernières réponses avec une taille de diagnostic limitée
* `Amelioration` Réduction de la latence du pont grâce au transport JNI/Binder par défaut et aux réponses via la boucle événementielle Node, avec repli sur fichiers et limites des appels en attente
