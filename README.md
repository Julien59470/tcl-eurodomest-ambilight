# Ambilight TCL / Eurodomest RGBIC

Application native Android TV pour piloter localement le ruban RGBIC Eurodomest sur trois côtés d'une TCL.

**Version active sur `main` : 1.1.0 MediaProjection**. Aucun ADB, root, Termux, contrôleur additionnel, cloud Tuya permanent ou ordinateur.

- Capture Android officielle avec consentement par session.
- Analyse d'image 160x90 et exclusion stabilisée des bandes noires haut/bas/gauche/droite.
- Mapping physique trois côtés préservé, y compris inversion de l'ordre DP61.
- Quatre couleurs déjà validées physiquement ; paliers 8/12/16/20 **expérimentaux et uniquement après validation visuelle**.
- Lecture locale des DP et rapport diagnostic sans Local Key / Device ID.
- Identifiants du boîtier exclusivement saisis sur la télévision, Local Key chiffrée par AndroidKeystore.

Instructions détaillées : [README_MEDIAPROJECTION_V10.md](README_MEDIAPROJECTION_V10.md).

**Téléchargement direct** (publié automatiquement seulement après compilation et tests réussis) :
[Eurodomest-Ambilight-TCL-MEDIAPROJECTION-v1.2.0.apk](https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-MEDIAPROJECTION-v1.2.0.apk)

### Limites
MediaProjection peut dégrader les performances de certaines vidéos 4K/HDR et n'accède pas aux contenus protégés. Un ACK Tuya ne démontre pas l'existence de segments physiques indépendants. Les builds debug GitHub Actions n'ont pas de certificat de signature durable sans configuration supplémentaire.

## Correction navigation Android TV v1.2.0
Le bouton dédié « SAISIR LA LOCAL KEY » ouvre une boîte de dialogue avec focus sur l'éditeur et demande d'ouverture du clavier Android. Une case permet de voir temporairement les caractères, masqués par défaut. La clé n'est persistée qu'après « ENREGISTRER LA CONFIGURATION » et reste chiffrée.

## Version v1.2.0 — étalonnage et couleur
- [Guide de calibrage et fonctionnement](README_MEDIAPROJECTION_V11.md)
- Calibrage zone physique par zone physique, paramétrage écran, permutations enregistrées.
- Pixels sombres pris en compte, gamma/gains et luminosité réglables.
- Commande noire de sécurité uniquement après confirmation physique.
- Diagnostic Tuya : tous les DP retournés (valeurs tronquées), image/commande analysée via bandelettes de couleurs.
- État capturé, FPS, horodatages, erreurs, flux figé et reconnexion Tuya.


## v1.2.0 — Mise en veille de la TCL : extinction complète du ruban

- **Lorsque la session Ambilight MediaProjection est active**, le service de premier plan écoute le broadcast système ACTION_SCREEN_OFF (passage Android en état non interactif), l'arrêt de la projection, et vérifie périodiquement PowerManager.isInteractive().
- Lorsqu'une mise en veille est détectée, le service arrête les mises à jour de couleurs et envoie **DP20 switch_led=false** au contrôleur Eurodomest en Tuya local 3.5. Contrairement à une trame RGBIC noire, c'est une commande d'extinction générale.
- Une courte prise de verrou CPU (PARTIAL_WAKE_LOCK, maximum 15 secondes) peut laisser au réseau le temps de terminer l'envoi ; aucune demande de réveil de l'écran n'est effectuée.
- Deux tentatives au maximum. La dernière tentative/réponse est consultable dans le rapport de diagnostic. Un ACK réseau n'est pas une preuve visuelle d'extinction.
- L'option « Éteindre COMPLÈTEMENT le ruban à la veille » est activée par défaut et modifiable dans « Réglages luminosité / couleurs ».
- Deux boutons permettent de **tester DP20=false** et de **rallumer DP20=true** depuis l'application, sans mise en veille réelle.
- Au prochain démarrage d'une nouvelle session MediaProjection autorisée par Android, la première connexion Tuya réactive DP20 avant l'envoi des couleurs.
- Ce mécanisme n'est pas un surveillant permanent : si la capture est déjà arrêtée, si Android tue l'application, ou si le firmware TCL coupe le réseau avant la livraison de l'événement, aucune extinction ne peut être garantie. Aucun root/ADB/Termux n'est utilisé. Vérifier le comportement sur le firmware TCL réel.
