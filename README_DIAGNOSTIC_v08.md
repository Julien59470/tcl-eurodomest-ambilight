# Version v0.8.0 — diagnostic ADB local TCL

Cette version conserve les moteurs ADB TLS (screencap) et MediaProjection de secours, sans service d'accessibilité.

Le message générique "ADB : IOException" de la v0.7 ne permettait pas de déterminer si mDNS, l'authentification TLS, la commande ADB ou l'utilitaire screencap échouaient.

## Corrections

- Erreur précise par phase (connexion TLS / commande screencap) avec le message d'origine limité à 170 caractères.
- Après quatre échecs consécutifs, arrêt de la capture ADB plutôt que reconnexions permanentes.
- Port ADB TLS manuel optionnel : le port de connexion **Adresse IP et port** dans les paramètres de débogage sans fil Google TV. Ne pas confondre avec le port temporaire d'association.
- Test utilisateur **Vérifier connexion et capture ADB** : annonce une réussite seulement après avoir réellement reçu un PNG. Pas de commandes shell à taper.
- Aucune modification de l'orientation RGBIC ou du traitement des couleurs.

## Tester

1. Arrêter la v0.7 et toute autre app Ambilight.
2. Installer séparément v0.8.0 (nouvelle identité d'application, pour garder v0.7).
3. Configurer l'Eurodomest et valider les quatre couleurs.
4. Associer ADB depuis Safari, si ce n'est pas déjà fait, en restant sur l'écran du code à six chiffres de la TCL. Attention : chaque version de l'APK possède sa propre identité cryptographique ADB.
5. Cliquer sur Vérifier connexion et capture ADB.
6. Si échec de découverte mDNS : saisir le **port de connexion ADB TLS** indiqué par la TV dans Débogage sans fil (pas le port d'association).
7. Si le message précise "Commande screencap", la connexion ADB peut être fonctionnelle mais le firmware ou la commande refuse la capture. La saisie du port ne résoudra pas un refus du système.
8. Si test concluant : démarrer l'Ambilight ADB. Sinon, utiliser le mode MediaProjection de secours.

## Limites

Le moteur screencap fabrique un PNG global puis le décode. Il peut provoquer une surcharge et des saccades. Les restrictions du firmware ne sont pas contournées. Cette APK est une version de diagnostic et **n'est pas une preuve de fluidité 4K HDR**.

Lien :
https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.8.0.apk
