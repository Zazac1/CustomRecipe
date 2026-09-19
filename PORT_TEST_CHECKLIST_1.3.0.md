# Custom Recipes 1.3.0 - Checklist de tests 26.2

Statut : release 1.3.0 autorisée après build, démarrage client/serveur et vérification locale. Ne coche un élément qu’après l’avoir validé dans le jeu.

## Build et démarrage

- [x] `gradlew compileJava --offline` réussit avec Java 25.
- [x] `gradlew build --offline` réussit.
- [x] Le serveur dédié démarre avec une configuration vierge, sans erreur de mixin.
- [ ] Le serveur dédié démarre avec une configuration 1.2.1 existante.

## Migration de configuration et isolation des mondes

- [ ] Démarrer un monde 1.2.1 avec des recettes personnalisées, un état de recettes intégrées, des recettes Vanilla désactivées et des règles de variantes.
- [ ] Confirmer que les anciennes recettes fonctionnent toujours dans le premier monde chargé après migration.
- [ ] Confirmer qu’une copie réutilisable existe dans la Bibliothèque globale.
- [ ] Créer un second monde : il ne doit contenir aucune recette du premier, sauf si elles ont été copiées volontairement.
- [ ] Modifier le monde A, redémarrer Minecraft et vérifier que le monde B reste inchangé.
- [ ] Supprimer puis recréer une sauvegarde avec le même nom d’affichage : elle doit être traitée comme un nouveau monde physique.

## Sélection de cible et navigation

- [ ] Mod Menu ouvre RecipesCreator sans modifier de cible avant une action explicite.
- [ ] Sélectionner un monde affiche les sauvegardes valides avec leur nom, miniature, recherche, état de survol et défilement.
- [ ] La Bibliothèque globale expose Bibliothèque/Créer, mais pas Recettes Vanilla.
- [ ] Une cible monde expose Bibliothèque/Créer/Recettes Vanilla et affiche le bon badge de cible.
- [ ] Échap et Retour reviennent à l’écran précédent immédiat, pas directement à l’accueil.
- [ ] Le bouton du menu pause ouvre le monde chargé et ne permet pas de changer de cible.

## Ajout rapide

- [ ] Les six raccourcis intégrés tiennent dans la barre latérale Ajout rapide sans dépasser sa bordure.
- [ ] Les recettes intégrées sont absentes d’un nouveau monde tant que Ajouter la recette n’a pas été utilisé.
- [ ] Retirer un raccourci intégré ou personnalisé d’Ajout rapide ne retire que ce raccourci.
- [ ] Ajouter un raccourci personnalisé crée une recette indépendante et modifiable dans le monde actuel.
- [ ] Le mode de sélection Ajout rapide se termine après un ajout réussi ou un clic droit.
- [ ] Le bouton plus permanent du haut n’est jamais mis en surbrillance comme candidat de recette.

## Sauvegarde et autorité serveur

- [ ] Sauvegarder depuis l’accueil local écrit dans la cible sélectionnée et recharge le monde intégré actif.
- [ ] Sauvegarder depuis Bibliothèque ou Recettes Vanilla revient à RecipesCreator.
- [ ] Sur serveur dédié, Sauvegarder depuis Bibliothèque/Vanilla prépare seulement les modifications ; aucune donnée n’est envoyée.
- [ ] Sur serveur dédié, Sauvegarder depuis l’accueil valide, persiste, recharge les recettes et revient à l’écran appelant.
- [ ] Un non-OP distant ne peut pas utiliser `/customrecipe`, obtenir les données de recettes serveur ni sauvegarder la configuration.
- [ ] Un OP distant ne peut modifier que par l’éditeur serveur et reçoit les résultats de validation.
- [ ] L’assistant de chat local ne fait rien en multijoueur distant.

## Régressions 26.2 existantes

- [ ] Les recettes Vanilla désactivées restent visibles et peuvent être réactivées.
- [ ] Les variantes de matériaux désactivées restent bloquées, tandis que les variantes autorisées se fabriquent normalement.
- [ ] Les recettes Vanilla gardent la priorité pour des entrées personnalisées identiques.
- [ ] Sélectionner un résultat personnalisé dans le livre de recettes reste sélectionné pendant le craft avec Maj.
- [ ] Les recettes nécessitant un mod manquant restent récupérables et affichent leurs objets manquants.
