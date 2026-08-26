# v1.2.0

###### 2026/08/26

* `Ajout` Activation d'un accès au système de fichiers comparable au bureau dans les limites des autorisations Android, avec refus permanent de `/proc`, `/sys` et `/dev`
* `Ajout` Ajout de `accessibility.swipe` et `accessibility.gesture` derrière la capacité dédiée `accessibility.gesture`
* `Correction` Échec fermé du TypeScript brut sans sortie de compilation fournie par l'hôte, mappage des imports dynamiques de snapshot et normalisation des piles générées/importées
* `Amelioration` Alignement du contrat v2 hôte/plugin, des manifestes de capacités et de la frontière de responsabilité du runtime plugin-only
* `Amelioration` Centralisation dans le dépôt du plugin des exemples Node.js, des déclarations TypeScript, de l'assistant de projet, des valeurs par défaut du runtime et des contrôles d'alignement avec l'hôte, avec suppression des options Gradle et des ressources de développement dupliquées côté hôte
