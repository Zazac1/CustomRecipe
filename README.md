# Custom Recipe — NeoForge 26.3

Port de **Custom Recipe 1.4.7** pour **Minecraft 26.3**, avec **NeoForge 26.3.0.98 ou supérieur** et **Java 25**.

Ce projet reprend les fonctionnalités et les adaptations Minecraft de `Fabric_26.3`, avec une intégration NeoForge fondée sur `NeoForge_1.21.11` : bibliothèque globale, recettes propres à chaque monde, ajout automatique dans tous les mondes, objets personnalisés et composants, quantités, recettes avec ou sans forme, règles sur les recettes existantes et livre de recettes.

## Compilation et installation

Depuis ce dossier, avec un JDK 25 installé :

```powershell
.\gradlew.bat build
```

Le fichier à installer est `build/libs/customrecipe-1.4.7+26.3.jar`. Copier ce fichier dans le dossier `mods` d'une installation **NeoForge pour Minecraft 26.3**. Fabric Loader, Fabric API et Mod Menu ne sont pas nécessaires.

## Utilisation

- Ouvrir la configuration depuis le bouton de configuration de Custom Recipe dans la liste des mods NeoForge.
- En solo, utiliser aussi le bouton Custom Recipe du menu pause pour gérer le monde en cours.
- Sur un serveur dédié, un opérateur utilise `/customrecipe_server`. Installer le mod sur le serveur et sur le client de l'opérateur.
- `/customrecipe_item` permet d'enregistrer, distribuer et gérer les objets personnalisés.
- Les configurations sont stockées dans `config/customrecipe.json`, séparément pour le client et le serveur.

## Développement et vérification

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

Les dossiers de jeu sont `run/client` et `run/server`. `run-local-test.ps1` prépare un serveur et un client de développement avec Java 25, sans réutiliser les mondes des autres variantes.

Depuis la racine commune, la validation automatique de ce port s'exécute avec :

```powershell
.\test-all.ps1 -Mode Full -Version NeoForge_26.3
```

Le rapport commun se trouve dans `validation/results/latest.md`. La compilation et les contrats automatiques ne remplacent pas les scénarios fonctionnels en jeu ; voir `VALIDATION.md` pour les contrôles réellement effectués sur ce port.

## Références

- Code de référence Minecraft : `../Fabric_26.3`.
- Intégration de référence du loader : `../NeoForge_1.21.11`.
- [Kit officiel NeoForge 26.3 avec NeoGradle](https://github.com/NeoForgeMDKs/MDK-26.3-NeoGradle).
- Licence MIT : [LICENSE](LICENSE).
