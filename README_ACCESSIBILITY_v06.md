# Ambilight TCL — mode sans pictogramme v0.6.0 (expérimental)

## Objectif
Une seule APK utilisant la fonction publique Android AccessibilityService.takeScreenshot
à la place de MediaProjection. Elle ne nécessite ni ordinateur ni commandes ADB.

## Test sur TCL 55Q6C / Android 14
1. Arrêter Universal Ambient Light et les anciens Ambilight v0.4/v0.5.
2. Installer l'APK v0.6 séparée. Configurer le boîtier, confirmer le test des 4 couleurs.
3. Cliquer **DÉMARRER SANS PICTOGRAMME**.
4. Activer **Ambilight TCL - capture locale** dans les paramètres Accessibilité de la TV.
5. Revenir à SmartTube et vérifier : voyant de capture absent, LED synchronisées, vidéo fluide.
6. Pour arrêter, revenir à Ambilight TCL et cliquer Arrêter. Le service d'accessibilité
   peut également être désactivé dans les paramètres TV à tout moment.

## Sécurité
Le service n'analyse pas les événements d'entrée, ne sauvegarde pas d'image et
ne communique pas avec un serveur. Les quatre couleurs calculées sont envoyées
au contrôleur Eurodomest via le protocole Tuya local. La clé reste chiffrée.

## Limitations
- AccessibilityService peut prendre des captures mais la copie GPU vers CPU
  peut être coûteuse, surtout avec les flux 4K/HDR. Aucune fluidité garantie.
- Android limite la fréquence des screenshots. Le code prend une capture après
  avoir terminé la précédente et ralentit en cas d'erreurs ou de charge.
- Le pictogramme MediaProjection n'est pas créé par le mode Accessibilité ;
  la présence d'un autre indicateur système dépend du firmware TCL.
- Le mode de secours MediaProjection conserve son indicateur.
- La capture de contenu protégé ou sur un plan vidéo matériel séparé est
  soumise aux restrictions Android habituelles.
- Le service doit être explicitement autorisé par l'utilisateur dans Accessibilité.
- Une version différente de la v0.5 est installée pour éviter un conflit de
  signature APK et conserver un retour arrière.

## Lien APK direct
https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.6.0.apk
