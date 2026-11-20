# Ambilight TCL / Eurodomest RGBIC

Application native Android TV pour piloter localement le ruban RGBIC Eurodomest sur trois côtés d'une TCL.

**Version active sur \`main\` : 1.0 MediaProjection**. Aucun ADB, root, Termux, contrôleur additionnel, cloud Tuya permanent ou ordinateur.

- Capture Android officielle avec consentement par session.
- Analyse d'image 160x90 et exclusion stabilisée des bandes noires haut/bas/gauche/droite.
- Mapping physique trois côtés préservé, y compris inversion de l'ordre DP61.
- Quatre couleurs déjà validées physiquement ; paliers 8/12/16/20 **expérimentaux et uniquement après validation visuelle**.
- Lecture locale des DP et rapport diagnostic sans Local Key / Device ID.
- Identifiants du boîtier exclusivement saisis sur la télévision, Local Key chiffrée par AndroidKeystore.

Instructions détaillées : [README_MEDIAPROJECTION_V10.md](README_MEDIAPROJECTION_V10.md).

**Téléchargement direct** (publié automatiquement seulement après compilation et tests réussis) :
[Eurodomest-Ambilight-TCL-MEDIAPROJECTION-v1.0.0.apk](https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-MEDIAPROJECTION-v1.0.0.apk)

### Limites
MediaProjection peut dégrader les performances de certaines vidéos 4K/HDR et n'accède pas aux contenus protégés. Un ACK Tuya ne démontre pas l'existence de segments physiques indépendants. Les builds debug GitHub Actions n'ont pas de certificat de signature durable sans configuration supplémentaire.
