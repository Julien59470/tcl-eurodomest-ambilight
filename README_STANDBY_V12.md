
# v1.2 — extinction complète à la veille de la télévision

## Fonctionnement
Une session Ambilight active enregistre un receiver dynamique pour Android ACTION_SCREEN_OFF et ACTION_SHUTDOWN, plus un repli via PowerManager.isInteractive() et le callback MediaProjection.onStop().

À l'entrée en veille, la boucle de synchronisation s'interrompt. Elle essaie d'envoyer la commande locale Tuya DP20=false avec une connexion déjà active, puis une deuxième tentative sur une nouvelle connexion si nécessaire. Un verrou CPU partiel est gardé jusqu'à quinze secondes au plus durant la tentative pour éviter que la mise en veille immédiate empêche l'envoi réseau. Le service se termine ensuite, sans relancer MediaProjection automatiquement.

DP20=false est l'arrêt général switch_led documenté par Tuya. Il ne doit pas être confondu avec une commande de couleur noire DP61.

### Test
1. Installer la v1.2.0 et enregistrer les paramètres habituels, sans secrets codés dans le dépôt.
2. Vérifier dans « Réglages luminosité / couleurs » que « Éteindre COMPLÈTEMENT le ruban à la veille » est coché.
3. Tester « TESTER L'EXTINCTION TOTALE DES LED (DP20) », puis « RALLUMER LES LED (DP20) ». Confirmer sur le ruban réel que ces commandes fonctionnent.
4. Démarrer MediaProjection avec l'autorisation « écran entier », ouvrir un contenu normal et appuyer sur le bouton de veille de la TCL.
5. Vérifier que le ruban s'éteint ; réveiller la TV puis consulter « COPIER / PARTAGER LE DIAGNOSTIC » pour lire la dernière tentative et les éventuelles erreurs.
6. Après réveil, redémarrer la session MediaProjection et autoriser la nouvelle capture. Android 14 n'autorise pas la réutilisation d'un ancien jeton de capture.

### Limites
- ACTION_SCREEN_OFF est un événement d'état interactif Android, pas nécessairement une preuve de coupure matérielle de l'écran. Les TV peuvent implémenter des veilles personnalisées.
- Un receiver ACTION_SCREEN_OFF ne peut pas être déclaré dans le manifest : il doit être enregistré dynamiquement. Il n'est donc actif que tant que le service Ambilight existe.
- Si l'application n'est plus active, si le Wi-Fi est coupé trop tôt ou si Android suspend le processus avant l'envoi, la commande peut échouer. Le rapport indique « acquitté » et non « LED physiquement éteinte ».
- L'extinction à la veille ne dépend pas du test précédent de « noir DP61 ». DP20 est un mécanisme distinct.
- Le rapport et l'APK ne contiennent aucun secret.
