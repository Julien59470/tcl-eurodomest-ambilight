# Ambilight TCL v0.9.0 — correctif libadb `Stream closed.`

## Observation
Sur TCL Android TV 14, association ADB réalisée, test screencap échoue avec `Commande screencap: Stream closed.` et zéro capture. Cela n'établit pas que le firmware bloque screencap : libadb-android 3.1.1 possède des anomalies documentées de gestion du CLSE/EOF. Voir https://github.com/MuntashirAkon/libadb-android/issues/34 et https://github.com/MuntashirAkon/libadb-android/pull/35 (non fusionnée lors de cette modification).

## Correction
- `PngStreamReader` lit les données `exec:screencap -p`, détecte les blocs PNG et arrête la lecture dès que IEND est reçu au lieu d'attendre le EOF incorrect de LibADB.
- Image complète nécessaire : une fermeture prématurée avant IEND reste une erreur. On ne masque jamais un PNG tronqué.
- Watchdog de 8 secondes pour débloquer un flux ADB qui n'envoie plus de données.
- Tests JVM des PNG complets, fragmentés, interrompus et des réponses invalides.
- Reprise des moteurs MediaProjection et RGBIC de la v0.8. Pas de service Accessibility, pas de root.
- APK séparée de la v0.8 car les APK Debug GitHub Actions n'ont pas de signature de distribution persistante.

**Limite :** si aucun octet PNG n'arrive ou si libadb perd le flux avant IEND, il faudra traiter le transport lui-même, ou abandonner screencap en faveur d'un autre moteur. Rien ne garantit que cette capture soit plus fluide en 4K/HDR.

## Lien direct
https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.9.0.apk
