# Tests de base dans Minecraft

Cette suite de développement vérifie le mod dans un serveur Minecraft NeoForge chargé, avec ses Mixins actifs. Les objets de test utilisent un joueur simulé NeoForge et les véritables menus de craft de Minecraft. Elle ne modifie pas le code du mod distribué.

Les classes de `validation/src/main/java` sont compilées uniquement avec `-PbasicValidation` et sont toujours exclues du JAR distribué.

## Exécution

1. Sauvegarder les configurations de développement de `run/client/config` et `run/server/config`. Les profils de test doivent rester séparés d'une partie habituelle.
2. Démarrer le serveur : `./gradlew.bat runServer -PbasicValidation`.
3. Dans sa console, exécuter `cr_basic_tests prepare`, puis attendre la fin de `/reload`.
4. Pour le scénario d'import, copier `logs/basic/client-fixture.json` dans `run/client/config/customrecipe.json` avant de démarrer le client.
5. Démarrer le client avec `./gradlew.bat runClient -PbasicValidation`, rejoindre le serveur de développement et attendre le chargement du monde.
6. Dans la console du serveur, exécuter `cr_basic_tests run`.
7. Lire `logs/basic/results.json`. Un `FAIL` doit être corrigé ou expliqué comme une erreur de préparation du test, puis le test doit être réexécuté.
8. Effectuer les contrôles visuels et les échanges réseau depuis l'éditeur : la suite serveur ne remplace pas ces manipulations.
9. Fermer les deux jeux, restaurer les configurations sauvegardées, puis exécuter depuis la racine commune `./test-all.ps1 -Mode Full -Version NeoForge_26.3` sans le paramètre de développement.

`prepare` conserve la configuration serveur initiale dans `logs/basic/server-config-before.json`. Ne pas supprimer cette sauvegarde avant la restauration.

## Couverture

- Recettes avec et sans forme, décalage et miroir, quantités minimales et résultat multiple.
- Consommation réelle via les menus de Minecraft, Shift-craft et rejet d'un ancien résultat devenu invalide.
- Ingrédients et résultats nommés, durabilité, composants exacts et remplissage depuis le livre de recettes.
- Priorité Vanilla, remplacement intentionnel, variantes de matériaux et règles globales avec exceptions par monde.
- Recettes désactivées ou corrompues, séparation des mondes et bibliothèque automatique.
- Déblocage automatique, sérialisation, export/import et déduplication.
- Refus de sauvegarde sans permission, JSON invalide, taille excessive et écriture volontairement bloquée.

Les captures et le rapport d'exécution complètent ces assertions ; voir `../VALIDATION.md`.
