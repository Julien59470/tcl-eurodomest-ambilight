# Eurodomest Ambilight TCL — application Android TV (prototype v0.1.0)

**État : code source natif Java fourni, tests de protocole Java exécutés, APK non compilée dans cet environnement.** Ce n'est pas encore une application validée sur ta TCL 55Q6C ou ton Eurodomest 6208000100. Ne pas confondre le ZIP source avec une APK installable.

## 1. Architecture

```
TCL Google TV
 ├─ Universal Ambient Light (l'application déjà installée)
 │    └─ MediaProjection → 127.0.0.1 UDP Raw RGB / port 19446
 └─ Eurodomest Ambilight TCL (cette APK)
      ├─ Service Android connecté au premier plan (notification)
      ├─ Réception UDP et calcul de couleur moyenne
      ├─ Envoi limité à 1,5 commande/seconde, couleur la plus récente seulement
      └─ TCP 6668 / protocole Tuya local 3.3, 3.4 ou 3.5
               ↓
        Eurodomest HDMI Sync Box sur Wi-Fi privé
               ↓
         Ruban LED existant (couleur globale expérimentale)
```

L'application ne capture **pas** l'écran. Elle ne promet pas Netflix ou Disney+ (DRM/FLAG_SECURE). Universal Ambient Light prend en charge la capture quand le contenu n'est pas protégé et si le firmware de ta TV l'autorise.

## 2. Ce qui est implémenté

- Application Java Android TV (API 26+) avec entrée `LEANBACK_LAUNCHER`, bannière TV et navigation par télécommande.
- Service foreground `connectedDevice` avec notification, écoute sur le loopback uniquement.
- Réception de paquets UDP Raw sans en-tête (3 octets par LED, 1–490 pixels).
- Couleur moyenne RGB + transformation HSV hex `HHHHSSSSVVVV`, DPs hypothétiques 20/21/24.
- TCP local Tuya avec AES/ECB 3.3, négociation de clé AES/HMAC 3.4, négociation AES/GCM 3.5.
- Lecture seule des DP avant toute commande et confirmation manuelle du test rouge puis bleu.
- Tentative de restauration des DP 20/21/24 après le test (sans garantie).
- Aucune écriture automatique possible tant que le test physique n'est pas confirmé dans l'interface.
- Démarrage automatique du service sur `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED` **après** validation et activation explicite.
- Local Key chiffrée par Android Keystore AES-GCM ; paramètres exclus des sauvegardes Android.
- Découverte passive d'IP ayant envoyé une annonce Tuya UDP sur les ports 6666/6667 pendant 10 s (sans pouvoir certifier quel appareil est l'Eurodomest).
- Réinitialisation des secrets depuis l'application.
- Tests pure Java de format UDP, HSV, AES, HMAC, GCM et intégrité.

**Ne sont pas implémentés :** récupération automatique de la Local Key via le cloud Tuya, contrôle RGBIC indépendant des zones, contournement DRM, méthode sans consentement MediaProjection, contrôle direct depuis un iPhone en réseau, pilotage de sources HDMI, démarrage garanti sur tous les firmwares TCL.

**Limitations volontaires :** version Tuya 3.1 et 3.2 non intégrées ; si ton appareil utilise ces versions, une adaptation sera nécessaire. Certaines variantes `device22` peuvent demander une découverte de DP plus complète que les 20/21/24. Les commandes Tuya n'ont PAS été confrontées à ton firmware exact.

## 3. Compilation de l'APK

Le projet est prêt pour **Android Studio** (JDK 17, Gradle 8.9, Android SDK 35, Android Gradle Plugin 8.7.3) ou **GitHub Actions**. Le ZIP contient `.github/workflows/build-apk.yml` qui lance une compilation à chaque push sur `main` ainsi qu'à la demande, sans que tu aies besoin d'un PC local pour exécuter l'application. La compilation distante ne contrevient pas à la contrainte « aucune machine permanente dans le salon ».

### Méthode GitHub Actions (sans PC local)

1. Créer un nouveau dépôt GitHub privé, vide, avec branche `main`.
2. Y envoyer le contenu de cette archive **à la racine** du dépôt, y compris le répertoire caché `.github/workflows` (par interface GitHub, Codespaces, ou l'app GitHub connectée).
3. Ouvrir GitHub > dépôt > Actions > **Build Android TV debug APK**. Si nécessaire, choisir **Run workflow**.
4. Lorsque le job devient vert, télécharger l'artefact `Eurodomest-Ambilight-TCL-debug` depuis la page du run. À l'intérieur du ZIP d'artefact se trouve **`app-debug.apk`**.
5. Transférer cette APK sur ta TCL (par exemple via LocalSend) puis l'installer avec le gestionnaire de fichiers, après avoir autorisé l'installation depuis cette source.

L'APK debug est signée automatiquement avec une clé de développement. Elle convient à un test privé. Pour une distribution publique, prévoir une signature de production pérenne et un audit complémentaire.

### Méthode Android Studio

Ouvrir le dossier du projet, attendre la synchronisation Gradle, **Build > Build APK(s)**. Le résultat se trouve sous `app/build/outputs/apk/debug/app-debug.apk`. Ne pas divulguer de Local Key ni d'identifiants dans le dépôt GitHub (aucun de ces secrets n'est nécessaire au build).

## 4. Démarrage sur la TV — procédure de recette

### Étape A : vérifier les couleurs UDP sans Tuya

1. Installer la nouvelle APK `Eurodomest Ambilight TCL`.
2. Ouvrir l'application sur la TCL, sélectionner **Démarrer l’écoute UDP**. Pour le moment le boîtier n'est **pas** commandé (test physique non autorisé).
3. Sélectionner **Envoyer 3 couleurs de démonstration** et vérifier que le compteur `Paquets UDP` augmente. Si le port 19446 est occupé, fermer d'autres récepteurs WLED/UDP.
4. Dans Universal Ambient Light 2.x, configurer : **UDP raw**, host `127.0.0.1`, port `19446`, ordre `RGB`, envoyer 24 LED logiques pour commencer. Si l'ancienne version n'a que WLED, utiliser **WLED > UDP Raw > 19446**.
5. Activer la capture de Universal Ambient Light ; tester YouTube / interface Google TV. Le compteur UDP doit augmenter et la moyenne RGB doit varier. En absence de paquets, ne pas renseigner encore de secrets et diagnostiquer UAL / autorisations Android.

### Étape B : vérifier l'Eurodomest

1. Trouver son IP privée dans l'interface du routeur ou essayer le bouton d'écoute des annonces Tuya. Les annonces détectées ne prouvent pas qu'il s'agit de l'Eurodomest.
2. Trouver le Device ID / Virtual ID dans Smart Life. Récupérer la Local Key par une méthode autorisée (notamment le portail développeur Tuya, sous réserve d'accès). **Ne jamais publier ou envoyer cette clé.**
3. Remplir IP, ID, **version réellement détectée** (3.3 / 3.4 / 3.5) et Local Key puis appuyer sur Enregistrer.
4. Appuyer sur **Interroger les points de données**. C'est en lecture seule. Attendre les DP réels et vérifier types de DP20 (bool), DP21 (texte) et éventuellement DP24 (texte).
5. Si le profil est cohérent, appuyer sur **Tester ROUGE puis BLEU**. Accepter uniquement en regardant le boîtier. La tentative de restauration initiale n'est pas garantie.
6. Appuyer sur **J'ai VU rouge et bleu** uniquement si les LED ont réellement changé. Cela débloque l'envoi de couleurs en continu.
7. Démarrer l'écoute UDP, lancer UAL et regarder `commandes envoyées` et le comportement de la bande LED.
8. Activer l'option de démarrage au boot dans notre application puis la reprise au boot/veille dans Universal Ambient Light. Redémarrer entièrement la TV et tester ensuite la sortie de veille simple.

### Survie à la veille

L'application utilise un service Android de premier plan `connectedDevice` ; `BOOT_COMPLETED` permet son redémarrage après un boot complet dans les limites Android. La TV peut toutefois arrêter l'app pendant une veille profonde, notamment à cause des politiques d'énergie du fabricant. Si c'est le cas, ouvrir les réglages de l'application sur la TV et autoriser son activité en arrière-plan quand le firmware le permet. Les autorisations MediaProjection de Universal Ambient Light demeurent indépendantes.

## 5. Aide au diagnostic

| Symptôme | Cause possible / test utile |
|---|---|
| Démonstration UDP : compteur bloqué à zéro | Démarrer le service ; vérifier si port 19446 déjà pris / restriction de bouclage |
| Démo UDP OK, mais YouTube ne fait pas varier les couleurs | Source de capture UAL, consentement MediaProjection, vidéo rendue en plan non capturable, version UAL, adresse UDP |
| Appareil Tuya introuvable au scan | Scanner passif seulement ; utiliser la liste des clients du routeur ; vérifier que boîtier et TV sont sur le même LAN |
| Timeout 6668 | IP incorrecte, boîtier hors ligne, VLAN/isolement Wi-Fi, port fermé, connexion Tuya déjà occupée |
| Échec AES / HMAC / GCM | Local Key erronée ou changée, version de protocole incorrecte, firmware incompatible |
| DP20/21 absents | Profil Tuya différent ou statut non complet ; NE PAS FORCER les commandes |
| Test rouge/bleu non concluant | Ne pas cliquer sur « J'ai VU » ; inspecter DP, scène, mode et format du firmware |
| Fonctionne puis se fige | TV/Android tue le service ; vérifier notification, veille, batterie, connexion Wi-Fi, délais de réponse Tuya |
| Netflix/Disney+ ne change pas les couleurs | Contenus protégés DRM non disponibles à MediaProjection ordinaire |

## 6. Retour arrière

1. Dans l'APK, sélectionner **Arrêter la synchronisation et l'écoute**.
2. Désactiver **Démarrer au boot** et enregistrer.
3. Si la scène du boîtier est perturbée, revenir à la couleur ou au mode souhaité dans Smart Life.
4. Pour retirer les identifiants stockés localement, utiliser le bouton **Effacer les identifiants et réinitialiser**.
5. Désinstaller la nouvelle APK si désiré ; cela ne modifie ni UAL ni le firmware de l'Eurodomest.

## 7. Validation du code et sécurité

La commande de test pure-Java (aucun SDK Android requis) est :

```sh
mkdir -p tests/bin
javac -d tests/bin app/src/main/java/fr/ambilight/eurodomest/{RawRgb,TuyaWire}.java tests/BridgePureTests.java
java -cp tests/bin BridgePureTests
```

Résultat local actuel : **21 assertions réussies** (RGB, HSV, AES ECB, HMAC SHA-256, GCM, signatures invalides, clés de session). Ne remplace pas les essais physiques. L'environnement utilisé pour créer le projet n'a ni SDK Android installé ni accès aux téléchargements nécessaires : **aucune APK n'a été compilée ni installée sur une télévision**.

Les codes de référence des protocoles proviennent de :
- https://github.com/jasonacox/tinytuya/blob/master/PROTOCOL.md
- https://github.com/jasonacox/tinytuya/blob/master/tinytuya/core/message_helper.py
- https://github.com/make-all/tuya-local/issues/1475
- https://github.com/make-all/tuya-local/issues/2818
- https://github.com/vasmarfas/universal-ambient-light
- https://developer.android.com/develop/background-work/services/fgs/service-types

Application réalisée sur mesure, indépendante et expérimentale. Pas d'abonnement, pas de nouveau contrôleur ni de PC à laisser allumé.
