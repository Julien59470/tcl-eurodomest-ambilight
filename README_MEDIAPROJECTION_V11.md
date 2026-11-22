# Ambilight TCL v1.1.0 — Calibration et traitement couleur

## Compatibilité cible

TCL Android TV 14, firmware de référence tcl9618 V8-0012T01-LF1V643.003175 ; Eurodomest 6208000100 en Tuya local 3.5. Les couleurs à 4/8/12/16/20 zones ont été observées en essais manuels sur la TV, mais une correspondance indépendante de toutes les zones nécessite la calibration.

**Aucune clé Tuya, IP privée ou Device ID n'est embarqué dans l'APK ni publié dans le dépôt.**

## Installation

APK directe sur [main](https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-MEDIAPROJECTION-v1.1.0.apk) après réussite des tests et de la compilation GitHub Actions.

Les APK debug GitHub Actions changent potentiellement de certificat. Si Android refuse une mise à jour signée différemment, il faut désinstaller l'ancienne version, ce qui efface ses réglages chiffrés. Une clé de signature durable dans GitHub Secrets est nécessaire pour le résoudre sans publier de clé privée.

## Mise en route

1. Installer et démarrer Ambilight TCL.
2. Saisir IP LAN, Device ID Tuya, Local Key et protocole 3.5 dans Configuration. La Local Key est enregistrée dans AndroidKeystore et ne figure jamais dans les diagnostics.
3. Valider le motif quatre couleurs si nouvelle installation.
4. Sélectionner ensuite le palier 8/12/16/20 si chaque motif s'affiche physiquement de manière distincte. Un accusé de réception Tuya seul ne constitue pas une preuve.
5. **Calibrer LED ↔ écran** : la zone physique numérotée s'allume en rouge, les autres en bleu faible. Répondre avec sa position réelle sur le pourtour de l'écran (boutons position écran ←/→). Affecter : le mapping conserve une permutation de tous les segments, même en cas de correction. Le mapping initial est l'ordre inversé testé sur les anciennes versions.
6. **Tester extinction noire** : commande DP61 contenant une valeur HSV nulle pour chaque zone. Ne confirmer la réussite que si les LED s'éteignent entièrement. Sans cette confirmation, le logiciel ne prétend pas pouvoir éteindre les LED de façon fiable lors d'un flux absent.
7. **Réglages luminosité / couleurs** : gamma, niveau de noir, balance RGB, intensité et lissage. Les réglages s'appliquent au prochain démarrage de MediaProjection.
8. Démarrer MediaProjection, autoriser « écran entier » à chaque session (obligatoire sur Android 14), ouvrir une vidéo autorisant la capture.

## Fonctionnement

- 160×90 RGBA, acquireLatestImage; traitement d'au plus ~12.5 images/s; cadence d'envoi limitée à 200ms.
- Découverte géométrique des quatre bandes noires avec confirmation temporelle. Les scènes sombres ne déclenchent pas un recadrage brusque.
- Moyennes RGB sur toutes les données de la zone, y compris les pixels sombres. Pas de minimum lumineux forcé.
- Correction gamma/brightness/gains et filtrage temporel ; noir exact directement transmis sans lissage.
- Mapping DP61 index physique → position écran persisté par profil de zones 4,8,12,16,20; le contrôle physique ne modifie ni les données de découpage ni les firmware.
- Diagnostics locaux affichant les 12 DP (ou tout autre nombre retourné, avec valeurs tronquées) et les deux rangées de couleurs (capturées et transmises).
- Capture absente >2,4s : tente une extinction RGBIC uniquement si l'extinction noire a été validée auparavant.
- À l'arrêt : extinction RGBIC optionnelle seulement si validée. Ces commandes peuvent échouer (déconnexion / firmware) ; pas de garantie d'extinction en cas de perte réseau.

## Limitations importantes

- DRM/FLAG_SECURE, surfaces vidéo matérielles, 4K/HDR et performance GPU relèvent du firmware TCL.
- Deux boîtiers et deux apps pilotant le même Tuya simultanément peuvent entrer en conflit.
- Le calibrage logique par couleur rouge n'est pas une preuve absolue de l'indépendance physique des sections ; cela nécessite la vérification visuelle.
- Le mode Tuya DP61 « combinaison » est expérimental au-delà de quatre couleurs sur ce produit ; l'app contrôle uniquement ce datapoint et son état, jamais les DP physiques de segmentation.
- Les zones noires restent noires dans les valeurs calculées, mais le comportement physique d'extinction doit être testé sur le matériel.
- L'application n'exécute pas automatiquement un redémarrage après boot : MediaProjection nécessite une nouvelle autorisation.

## Tests automatisés

- tests/FrameAnalyzerTests.java — ancienne régression bordures / orientation.
- tests/ColorMappingTests.java — bordures quatre côtés, scène sombre, pixel noir, gamma, mapping bijectif et lissage.
- tests/BridgePureTests.java — chiffrement Tuya 3.3–3.5 et encodage DP61 4–20 couleurs.

Tous les tests doivent réussir sur GitHub Actions avant qu'une APK ne soit publiée.
