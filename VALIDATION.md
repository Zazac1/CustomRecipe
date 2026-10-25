# Vérification du port NeoForge 26.3

Date : **25 octobre 2026**.

Version du mod : **1.4.7+26.3**. Environnement : **Minecraft 26.3**, **NeoForge 26.3.0.98**, **JDK 25.0.4.1**, Windows.

## Validation automatique

Commande exécutée depuis la racine commune après la dernière correction des ressources :

```powershell
.\test-all.ps1 -Mode Full -Version NeoForge_26.3
```

Résultat : **14 PASS, 0 FAIL, 0 WARN**. Cette exécution concerne uniquement le nouveau port. Les anciennes variantes n'ont pas été reconstruites.

Le rapport détaillé est conservé dans `validation/results/full-2026-10-25.md`. Les contrôles couvrent la compilation, le contenu et les métadonnées du JAR, les ressources, les traductions et les contrats de non-régression communs.

## Tests de base complémentaires

La suite de développement a ensuite exécuté **24 contrôles dans un serveur Minecraft NeoForge chargé : 24 PASS, 0 FAIL**. Les résultats détaillés sont conservés dans `validation/results/basic-2026-10-25.json` et les instructions dans `validation/README.md`.

Ces contrôles vérifient les recettes avec et sans forme, les quantités, le décalage et le miroir, la consommation réelle et le Shift-craft, le rejet d'un résultat périmé, les composants et la durabilité, le remplissage depuis le livre, la priorité Vanilla, les variantes de matériaux, les exceptions par monde, les recettes corrompues, la bibliothèque automatique, le déblocage des recettes, l'export/import et les refus de sauvegarde.

Les refus de sauvegarde ont été vérifiés directement sur les gestionnaires serveur avec un joueur simulé : absence de permission, JSON invalide, taille excessive et écriture volontairement bloquée. Ils ne constituent pas une preuve d'exécution de tous les échanges réseau ou messages d'erreur de l'interface.

Le 25 octobre 2026, l'utilisateur a également indiqué avoir effectué ses tests manuels et a demandé la publication. Cette validation humaine est distincte des contrôles automatiques et des observations de Codex.

## Contrôles réellement effectués dans Minecraft

Les essais ont utilisé les profils de développement `run/client` et `run/server`. Le serveur dédié de test écoutait uniquement sur `127.0.0.1:25576`.

| Contrôle | Résultat | Observation |
|---|---|---|
| Chargement client et serveur dédié | PASS | Le mod s'initialise sur les deux côtés et le client rejoint le serveur. |
| Métadonnées NeoForge | PASS | La liste des mods affiche Custom Recipe 1.4.7+26.3 et son icône. L'ancien champ `logoFile` a été remplacé par `iconFile` ; le redémarrage n'affiche plus d'avertissement de métadonnées du mod. |
| Éditeur serveur | PASS | `/customrecipe_server` ouvre l'éditeur de l'opérateur sur le monde `port-validation`. |
| Brouillon Quick Add | PASS | La bibliothèque neuve est vide. Le modèle de totem affiche un aperçu, puis ajoute une seule copie indépendante. Le fichier serveur contient encore zéro recette avant la sauvegarde principale. |
| Sauvegarde serveur | PASS | Une confirmation est affichée, l'éditeur se ferme, le fichier contient la recette activée et le serveur recharge ses recettes. |
| Craft réel et livre de recettes | PASS | Après déblocage explicite de la recette avec `/recipe give`, le livre remplit la grille ; une émeraude et quatre blocs d'or sont consommés. Le résultat est placé dans l'inventaire. `data get entity Dev Inventory` confirme un totem et l'absence des ingrédients. |
| Retrait de la copie de test | PASS | Après retrait de cette copie dans le fichier de configuration de test et `/reload`, le serveur répond `Unknown recipe` pour son identifiant. Ce contrôle ne teste pas le bouton de suppression de l'interface. |
| Joueur non opérateur | PASS | Après `deop Dev`, la commande de l'éditeur est inaccessible et le fichier serveur conserve exactement le même hash. Les droits du compte de test sont ensuite restaurés. |
| Configuration dans la liste des mods | PASS | Le bouton Configuration ouvre la bibliothèque globale hors d'un monde. |
| Catalogue local avant chargement d'un monde | PASS | Le navigateur affiche 1 266 recettes avec leurs icônes depuis le menu principal. |
| Menu pause en solo | PASS | Un nouveau monde démarre et reçoit le conseil du premier jour. Le bouton Custom Recipe du menu pause ouvre l'éditeur verrouillé sur ce monde, indépendamment du serveur dédié. |

Preuves conservées : `logs/evidence/craft-totem.png`, `logs/evidence/server-after-save.json`, `logs/evidence/server-runtime.log`, `logs/evidence/client-runtime.log`, `logs/evidence/neoforge-menu-catalogue.png` et `logs/evidence/solo-current-world.png`.

## Limites de cette vérification

Ces contrôles fonctionnels ciblent les points d'intégration du nouveau loader. Ils ne constituent pas l'exécution exhaustive de tous les scénarios complémentaires : Codex a observé la réception d'un objet local en collision et d'une recette locale dans le brouillon serveur, sans modification du fichier serveur avant sauvegarde, mais ce parcours a été interrompu avant sa publication. Toutes les échelles d'interface et la compatibilité en jeu avec REI n'ont pas été vérifiées par Codex.

Les journaux de cette machine comportent des avertissements Windows sur les compteurs de performances et sur l'annonce LAN ; ils n'ont pas empêché les essais sur la connexion locale. Aucun autre mod optionnel n'était installé pendant ces contrôles.

## Fichier produit

- JAR : `build/libs/customrecipe-1.4.7+26.3.jar`.
- SHA-256 : `D7938FA3A699FA8D8F6944535EC6FEB120D2DC4B50289C6C14CCD7ABCBF79842`.

Le JAR a été inspecté automatiquement ; les essais en jeu utilisent les mêmes classes via les lancements de développement NeoForge. Les mentions de Mod Menu dans le conseil de démarrage ont ensuite été adaptées à la liste des mods NeoForge dans les quatre traductions, puis intégrées à la reconstruction finale validée en mode Full.
