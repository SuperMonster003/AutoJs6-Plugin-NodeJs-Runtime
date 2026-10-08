# AutoJs6 Node.js Runtime

Ce plugin fournit le runtime natif Node.js 24.21.0 pour AutoJs6.

Installez et activez le plugin dans le centre de plugins AutoJs6, puis demarrez un script avec:

```js
"nodejs";

console.log(process.version);
```

Le slot de runtime est `node24_21`. Il prend en charge les builds `arm64-v8a`, `armeabi-v7a` et `x86_64`, ainsi qu'un APK `universal`.

Les exemples de projets se trouvent dans `sample/nodejs`.

La page de réglages du plugin permet de choisir 1-8 exécutions simultanées (2 par défaut) et une limite de file (illimitée par défaut); les scripts excédentaires attendent en FIFO, les changements préservent les tâches acceptées et idleExitMs libère les processus inactifs
