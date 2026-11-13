# Ambilight TCL + Eurodomest v0.4.0 (EXPÉRIMENTAL)

Application Android TV autonome pour TCL 55Q6C / Android 14 et Eurodomest 6208000100 RGBIC. Pas de serveur, de Termux, d'Universal Ambient Light, de PC ou de source HDMI externe.

## Test et statut

- **Prototype compilable, non validé sur la télévision réelle** : les API Android MediaProjection peuvent faire saccader SmartTube 4K/HDR malgré les optimisations.
- Image de capture `160x90`, échantillonnée environ 5 fois par seconde, file d'images sans accumulation via `acquireLatestImage()`.
- Détection des bandes noires haut/bas stable sur quatre images, échantillonnage sur les bords du film plutôt que sur les bandes noires.
- Quatre couleurs RGBIC uniquement (test DP61 confirmé par l'utilisateur sur son boîtier), ordre physique : **bas droit → haut droit → haut gauche → bas gauche**.
- Cadence Tuya 200 ms, repli automatique et lissage léger.
- Local Key uniquement dans Android Keystore de la télévision, aucune clé ni mot de passe dans le dépôt public.

## Installation

URL directe de l'APK :

`https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.4.0.apk`

Cette APK a un identifiant différent de la v0.3 afin de conserver une solution de secours. Il faudra donc saisir de nouveau les paramètres Tuya dans la v0.4, puis vérifier visuellement les quatre couleurs. Ne pas faire fonctionner les deux applications en même temps, afin d'éviter des commandes concurrentes au boîtier.

Ouvrir l'application, configurer le boîtier, valider le test 4 couleurs, puis appuyer sur **DÉMARRER AMBILIGHT**. Choisir/autoriser le partage de **tout l'écran**, ouvrir ensuite SmartTube ou YouTube. Le système garde une notification persistante de capture. Le bouton **Arrêter** met fin à la session.

## Limites connues

- Android 14 exige une nouvelle autorisation de capture à chaque session. Démarrage automatique sans consentement non promis.
- Capture HDR/4K non garantie; DRM protégé non récupérable via MediaProjection ordinaire.
- La détection de bandes noires s'appuie sur la couleur sombre en haut et bas, et peut rater des images très sombres ou des bandes irrégulières.
- Les quatre zones sont une base validée. Le passage à 8–20 couleurs nécessite des tests physiques distincts.
- Fréquence Tuya limitée à cinq commandes par seconde, pas 30 FPS.

## Vérification build

GitHub Actions `Build and publish Ambilight autonome v0.4` exécute les tests Java et compile l'APK, puis la publie sous son propre nom pour Downloader. Les versions v0.1/v0.2/v0.3 demeurent dans le dépôt.
