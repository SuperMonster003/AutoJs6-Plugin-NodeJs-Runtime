# v1.2.0

###### 2026/08/29

* `Ajout` Ajout de `capabilities()` à la façade `mediainfo` et de la sélection explicite des snapshots v1/v2 du plugin dans son entrée appelable et `read()`; l'omission du schéma continue de renvoyer le snapshot Node v1 existant appartenant à l'hôte
* `Ajout` Ajout du module-source provider v3 pour compiler à la demande les fichiers `.ts/.mts/.cts` créés pendant l'exécution via des PFD bornés par octets et SHA-256, avec un budget de compilation indépendant de 30 s, le refus stable des sorties de chemin, liens symboliques et ambiguïtés, ainsi que les diagnostics TypeScript et la remise en correspondance Source Map de l'hôte
* `Ajout` Activation d'un accès au système de fichiers comparable au bureau dans les limites des autorisations Android, avec refus permanent de `/proc`, `/sys` et `/dev`
* `Ajout` Ajout de `accessibility.swipe` et `accessibility.gesture` derrière la capacité dédiée `accessibility.gesture`
* `Ajout` Ajout d’un délai maximal couvrant l’attente et l’exécution des scripts, avec l’erreur `ERR_AUTOJS6_SCRIPT_TIMEOUT`; sans délai défini, les scripts peuvent toujours s’exécuter indéfiniment
* `Ajout` Activation par défaut de `dgram` (UDP) et `http2`, avec une erreur explicite de désactivation pour `trace_events`
* `Ajout` Ajout du débogage local `inspector`, activé explicitement dans les versions Debug, avec écoute uniquement sur localhost et connexion via `adb forward`
* `Correction` Échec fermé du TypeScript brut sans sortie de compilation fournie par l'hôte, mappage des imports dynamiques de snapshot et normalisation des piles générées/importées
* `Correction` Remplacement de l'adaptateur ESM partiel basé sur des snapshots par le linker natif V8, corrigeant les exports mutables non mis à jour dans les réexports cycliques
* `Correction` Correction des imports ESM des façades de compatibilité AutoJs6 et de la détection de suffixes TypeScript inexistants, tout en préservant la priorité des paquets npm installés
* `Amelioration` Suppression du fallback legacy d'effacement TypeScript fondé sur des regex et de son commutateur de requête; les `.ts/.mts/.cts` bruts exigent désormais toujours la sortie du compilateur hôte
* `Amelioration` Alignement du contrat v2 hôte/plugin, des manifestes de capacités et de la frontière de responsabilité du runtime plugin-only
* `Amelioration` Centralisation dans le dépôt du plugin des exemples Node.js, des déclarations TypeScript, de l'assistant de projet, des valeurs par défaut du runtime et des contrôles d'alignement avec l'hôte, avec suppression des options Gradle et des ressources de développement dupliquées côté hôte
* `Amelioration` Extension des tests npm à 15 paquets, avec axios, express et les paquets ESM-only nanoid, p-limit et yocto-queue
* `Amelioration` Le champ de requête `executionMode` fait désormais autorité pour le mode de cycle de vie; le champ sans effet `runtimeAdapter` est obsolète
