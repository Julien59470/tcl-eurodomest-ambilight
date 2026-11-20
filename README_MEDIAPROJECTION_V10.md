# Ambilight TCL v1.0 — MediaProjection, 4 à 20 zones RGBIC

## Contexte
- TCL Google TV Android 14, firmware observé V8-0012T01-LF1V643.003175.
- Eurodomest 6208000100, local Tuya 3.5. Les identifiants et la Local Key ne sont jamais inclus dans ce dépôt.
- Le test historique des **quatre couleurs simultanées a été confirmé visuellement**.
- L'ordre de mapping existant, bas droit → haut droit → haut gauche → bas gauche, est conservé (inversion DP61 par défaut).

## Fonctionnement
1. Installer la nouvelle APK. C'est une application distincte de la v0.9 ADB.
2. Configurer l'IP LAN du boîtier, le Device ID Tuya, le protocole et la Local Key directement sur la TV. La Local Key reste chiffrée dans AndroidKeystore.
3. Enregistrer. Cliquer « Tester 4 couleurs », vérifier **visuellement** puis confirmer.
4. Cliquer « Interroger le boîtier » pour afficher les datapoints connus (lecture seule). L'API locale ne fournit pas nécessairement toutes les définitions produit.
5. Facultatif : « Tester le palier suivant » (8,12,16,20). Ne confirmer que si les segments multicolores s'affichent réellement. Un ACK Tuya n'est pas une preuve de compatibilité. Le dernier palier validé reste enregistré.
6. Cliquer « Démarrer Ambilight ». Accepter la demande Android de capturer **l'écran entier**. Android 14 demande le consentement à chaque nouvelle session.
7. Diagnostics dans l'application : FPS analysés, nombre de commandes, temps des réponses et recadrage haut/bas/gauche/droite.

## Limites
- La lecture de captures vidéos protégées, le HDR et la fluidité 4K dépendent du pipeline TCL. L'application ne les contourne pas.
- Le contrôle DP61 8 à 20 zones reste expérimental tant que le nombre réel de segments du firmware n'est pas confirmé.
- La sélection de zones est volontairement **manuelle**; le logiciel ne peut pas vérifier la fidélité physique des LED à partir d'un ACK réseau.
- Les APK debug GitHub Actions peuvent avoir des signatures différentes. Il peut être nécessaire de désinstaller la version précédente portant le même identifiant d'application pour une mise à jour future. Une signature de distribution stable est recommandée.

## Sécurité
- Aucun secret embarqué. Aucune API Tuya cloud ou service ADB.
- Aucune capture enregistrée ou téléversée; uniquement les couleurs locales.
- L'état DP est interrogé en lecture seule ; les essais RGBIC sont des écritures explicites, demandées par l'utilisateur.
- En cas de problème, arrêter la capture et revenir à Smart Life pour rétablir la scène habituelle.
