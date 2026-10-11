# NetworkTrafficMonitor-Android

Application Android de démonstration pour la surveillance de trafic réseau en temps réel.

Fonctionnalités
- Sélection d’une application cible
- Affichage des événements de trafic en temps réel
- Blocage d’une requête avec sauvegarde locale
- Modification d’une réponse serveur avec remplacement persistant
- Service foreground pour rester actif en arrière-plan
- Vue JSON / Java / Hex pour chaque réponse

Important
Ce projet est une base de démonstration logicielle. L’interception totale du trafic système Android exige des mécanismes au niveau du système (VPN, proxy système, root, ou un matériel adapté). Cette application montre la logique centrale : règles, blocage, modification et affichage en temps réel dans une interface Android.

Architecture
- MainActivity.kt : écran principal et interface utilisateur
- TrafficMonitorService.kt : service de fond et logique de suivi
- TrafficModels.kt : modèles de règles, requêtes, décisions, événements

Démarrage rapide
1. Ouvrir le projet dans Android Studio.
2. Synchroniser Gradle.
3. Lancer sur un émulateur ou un appareil.
4. Sélectionner une application cible.
5. Configurer une règle de blocage ou de modification.
6. Démarrer le service d’arrière-plan.
