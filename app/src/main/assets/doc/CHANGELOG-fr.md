# v1.4.0

###### Non publié

* `Correction` Les chemins MediaInfo et image acceptent les chemins absolus, les dossiers parents et les noms valides; les enregistrements suivent les mêmes droits Android avec un hôte mis à jour
* `Correction` Les erreurs de capacité du pont indiquent les déclarations node.permissions manquantes sans exiger le profil pro_compat_opt_in réservé au diagnostic
* `Correction` Le validateur ne rejette plus les chemins fs absolus ou les dossiers parents avec FS_OUTSIDE_SCOPE; Android détermine l’accès réel aux fichiers
* `Amelioration` Projets autonomes pour les événements Android et un enregistrement audio de trois secondes, avec déclarations et étapes de validation manuelle pour la capture, l'OCR, les touches physiques et MediaInfo
