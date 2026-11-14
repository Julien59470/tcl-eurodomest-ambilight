# Ambilight TCL + Eurodomest — v0.5.0 expérimentale

## Corrections
- Ordre LED corrigé : le premier groupe DP61 observé s'affiche sur la partie gauche de ton ruban, tandis que les échantillons d'écran démarrent à droite. L'ordre des quatre groupes est donc inversé par défaut. Bouton de correction gauche/droite si ton ruban ne correspond pas.
- Capture optimisée : 160 × 90, jusqu'à 12 images analysées/s, acquisition de la dernière image sans file retardée. La projection Android elle-même reste une limite possible en 4K/HDR.
- Lissage RGB adapté au contraste et nouvelles couleurs prioritaires. Commandes Tuya : 200 ms au démarrage, essai automatique de 160 ms seulement après 25 commandes avec réponse <115 ms ; retour à 200 ms en cas de ralentissement ou d'erreur.
- Diagnostic enrichi : FPS réellement mesurés, cadence LED, dernier temps de réponse et maximum.

## Installer
https://raw.githubusercontent.com/Julien59470/tcl-eurodomest-ambilight/main/Eurodomest-Ambilight-TCL-AUTONOME-v0.5.0.apk

La v0.5 a un identifiant de package séparé, afin d'éviter le conflit de signature debug de la v0.4 et conserver l'ancienne version installée. Renseigner à nouveau ses paramètres privés dans l'interface. Ne jamais les publier dans GitHub.

## Vérification
Couper l'ancienne application, installer la v0.5, refaire le test statique 4 couleurs et démarrer la capture. Lancer une vidéo divisée rouge à gauche / bleu à droite, vérifier que les LEDs à gauche sont rouges, à droite bleues. Si c'est inversé, appuyer une fois sur Corriger la répartition gauche/droite. Puis tester une vidéo avec bandes noires et SmartTube 1080p puis 4K SDR.

Cette version n'a pas encore été testée physiquement sur le téléviseur. Les performances 4K HDR restent non garanties.
