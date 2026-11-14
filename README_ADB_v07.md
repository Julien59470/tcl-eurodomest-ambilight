# Ambilight TCL v0.7 — capture ADB sans pictogramme (expérimentale)

Cette version capture le bureau Android TV et les applications compatibles en utilisant le service **shell** de l'ADB sans fil via TLS. Pas de MediaProjection pendant la capture ADB, pas d'AccessibilityService, pas de root, pas d'ordinateur permanent ni de boîtier supplémentaire.

**IMPORTANT** : le moteur utilise `exec:screencap -p`, qui doit réaliser une copie globale de l'écran et compresser un PNG à chaque image. Il est plus léger que de décoder deux flux vidéo uniquement sous certaines conditions, et n'est **pas démontré plus fluide** sur la TCL 55Q6C. La cadence des captures sera mesurée automatiquement, puis ralentie si le temps d'acquisition est élevé. Ce prototype n'est pas une solution 4K/HDR garantie.

## Installer

APK directement :
https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.7.0.apk

1. Arrêter les anciennes applications Ambilight. Installer l'APK v0.7 séparément. La v0.5 reste disponible.
2. Configurer le boîtier Eurodomest (IP locale, Device ID, Tuya 3.5, Local Key) et valider visuellement le test des quatre couleurs. Ne jamais publier la clé.
3. Dans l'app, sélectionner **Associer ADB sans PC (Safari sur iPhone)**. Noter l'URL et le PIN temporaires affichés.
4. Sur TV : Options pour développeurs → Débogage sans fil → Associer avec un code ; **laisser cet écran ouvert**.
5. Sur iPhone connecté au même Wi-Fi, ouvrir l'URL locale affichée par la TV, saisir le PIN de l'application, le port d'association et le code à six chiffres visibles sur la TV. La TV reste sur l'écran du code pendant l'envoi. La connexion de configuration s'arrête automatiquement après dix minutes.
6. Revenir dans l'application TV et sélectionner **DÉMARRER SANS PICTOGRAMME (ADB)**. Tester menus, SmartTube puis vidéos HDR/4K. Le débogage sans fil doit être actif sur la TV. Si l'ADB n'est pas disponible, la capture indique un diagnostic et **n'active pas silencieusement MediaProjection**.
7. La méthode « Capture de secours (MediaProjection) » reste disponible, avec consentement Android et indicateur de capture.

## Architecture / sécurité

- LibADB Android (Apache 2.0) : association TLS et shell local uniquement, **sans ouverture du port ADB non chiffré 5555**.
- Clé RSA d'association générée et maintenue dans AndroidKeyStore (non exportée).
- Serveur de configuration HTTP temporaire sur le port local 28914 : PIN à huit chiffres, cinq essais max, aucun stockage des codes, aucune collecte de Local Key. Il doit rester inaccessible depuis Internet : Wi-Fi domestique seulement. Le code d'association ADB transite en clair dans le réseau local ; ne pas utiliser un réseau public.
- Le serveur s'arrête après 10 min ou en quittant explicitement, mais l'ADB sans fil lui-même reste contrôlé par Android.
- Les images de l'écran ne sont ni stockées ni envoyées sur Internet. Seules les couleurs de quatre zones sont transmises directement à Eurodomest via Tuya Local.
- Le boîtier a validé 40 commandes en huit secondes à 200 ms d'intervalle ; le moteur de capture PNG peut limiter fortement la cadence réellement disponible.
- Après reboot, le débogage sans fil peut être désactivé et les ports TLS changent : l'app tente une reconnexion mDNS, mais un démarrage parfaitement automatique n'est pas garanti.

## Retour arrière
Arrêter la v0.7 et réouvrir la v0.5 ; ne jamais exécuter simultanément deux versions qui envoient des commandes Tuya.

## Références
https://developer.android.com/tools/adb
https://github.com/MuntashirAkon/libadb-android
