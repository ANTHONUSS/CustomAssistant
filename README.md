# Prérequis
- Java 24
- Tous les fichiers contenus dans le zip de la release
  - `conf/.env` (fichier de configuration)
  - `conf/settings.txt` (fichier de configuration)
  - `data/assistantCustomisation/image.png` (image de l'assistant)
  - `data/assistantCustomisation/personality/textPersonality.txt` (personnalité de l'assistant)
  - `data/assistantCustomisation/personality/voicePersonality.txt` (customisation de la parole de l'assistant)
  - `data/assistantCustomisation/rvcModels/` (Dossier contenant les modèles RVC)
  - `data/wakeWordsModels/ok-assistant_fr_windows_v3_0_0.ppn` (Le modèle de mot clé)
  - `data/wakeWordsModels/porcupine_params_fr.pv` (Le paramètre fr pour modèle de mod clé)
  - `temp/RVC/` (Dossier temporaire pour les modèles RVC)
  - `temp/RVCOutput/` (Dossier temporaire pour les sorties RVC)
  - `customAssistant.jar` (Le fichier à exécuter pour lancer l'assistant)
- L'application [Mangio RVC](https://github.com/Mangio621/Mangio-RVC-Fork/releases/latest) installée (si vous souhaitez utiliser la customisation de la voix de l'assistant).

# Démarrage du programme
- Télécharger [la dernière release](https://github.com/ANTHONUSS/CustomAssistant/releases/latest) de l'assistant depuis le GitHub.
- Décompresser le fichier zip dans un dossier de votre choix.
 
À ce stade, deux choix s'offrent à vous :

- Exécuter le jar avec la commande suivante :

```bash
java -jar customAssistant.jar
```

- Exécuter le jar avec un double clic (si votre système est configuré pour exécuter les fichiers jar avec Java).

Si vous souhaitez exécuter le programme avec un double clic, il faut configurer votre système pour lancer les .jar avec l'exécutable javaw. Pour cela, vous pouvez suivre ces étapes (Windows) :
1. Faites un clic droit sur le fichier `customAssistant.jar`.
2. Sélectionnez "Ouvrir avec" puis "Choisir une autre application".
3. Cochez la case "Toujours utiliser cette application pour ouvrir les fichiers .jar".
4. Cliquez sur "Parcourir" et naviguez jusqu'à l'emplacement de votre installation Java (par exemple, `C:\Program Files\Java\jdk-24\bin\javaw.exe`).
5. Sélectionnez `javaw.exe` et cliquez sur "Ouvrir".
6. Cliquez sur "OK" pour confirmer.

Maintenant, vous devriez pouvoir exécuter le fichier `customAssistant.jar` en double-cliquant dessus.

# Configuration de l'assistant

## Fichier `.env` :

Le fichier `.env` contient les variables d'environnement nécessaires au bon fonctionnement de l'assistant. Vous aurez besoin de 2 clés API pour le faire fonctionner :
- Une AccessKey Porcupine pour la détection du mot clé (disponible depuis le site de [Picovoice.ai](https://console.picovoice.ai/)).
- Une clé API OpenAI pour la génération de texte (disponible depuis le site de [OpenAI.com](https://platform.openai.com)).

Le contenu du fichier `.env` doit ressembler à ceci :

```dotenv
PICOVOICE_ACCESS_KEY=[Porcupine Access Key]

OPENAI_KEY=[OpenAI API Key]
```

Il faudra bien sûr remplacer les crochets par vos propres clés (sans les crochets).

Si aucune clé n'est présente après le `=`, une fenêtre de configuration s'ouvrira lors du premier lancement de l'assistant pour vous permettre de saisir ces clés. Vous pouvez également modifier ces clés ultérieurement dans l'interface de l'assistant ou dans le fichier directement.

## Fichier `settings.txt` :

Le fichier `settings.txt` contient les paramètres de configuration de l'assistant. Vous pouvez modifier ces paramètres selon vos préférences. Voici le contenu par défaut du fichier `settings.txt` :

```
enableWebSearch=false
enableCustomVoice=false
mangioRVCPath=[path/to/mangio/rvc/app]
```

Vous pouvez modifier les paramètres directement dans ce fichier, ou dans l'interface de l'assistant une fois qu'il est lancé.

## Personnalisation de l'assistant :
L'assistant peut être personnalisé via son interface graphique, ou directement dans les fichiers de configuration. Voici les éléments que vous pouvez personnaliser :
- **Image de l'assistant** : Placez une image nommée `image.png` dans le dossier `data/assistantCustomisation`. Cette image sera utilisée comme avatar de l'assistant.
- **Personnalité de l'assistant** : Modifiez le fichier `textPersonality.txt` dans le dossier `data/assistantCustomisation/personality`. Ce fichier doit contenir la personnalité de l'assistant, par exemple : "Vous êtes un assistant gentil et amical qui adore servir les autres.".
- **Personnalité de la voix de l'assistant** : Modifiez le fichier `voicePersonality.txt` dans le dossier `data/assistantCustomisation/personality`. Ce fichier doit contenir la personnalité de la voix de l'assistant, par exemple : "Vous parlez avec une voix douce et apaisante avec un accent japonais".
- **Modèles RVC** : Placez vos modèles RVC dans le dossier `data/assistantCustomisation/rvcModels/` ou utiliser l'interface de l'assistant pour les ajouter. Vous pouvez utiliser Mangio RVC pour créer vos propres modèles RVC.
- **Recherche web** : Si vous souhaitez que l'assistant effectue des recherches sur le web, vous pouvez activer cette fonctionnalité dans le fichier `settings.txt` en mettant `enableWebSearch=true` ou en activant l'option sur l'interface graphique.

# Utilisation de l'assistant
Pour réveiller l'assistant, il suffit de dire le mot clé 'Ok Assistant'. L'assistant écoutera ensuite votre question et y répondra en utilisant l'API OpenAI. Si vous avez activé la personnalisation de la voix, l'assistant utilisera le modèle RVC sélectionné pour répondre avec une voix personnalisée.
Si l'assistant a été lancé en mode terminal, vous pouvez également écrire dans le terminal pour poser des questions à l'assistant. Il répondra de la même manière qu'en mode vocal.

# Problèmes connus
- Manque de robustesse dans la gestion des erreurs.
- Manque de robustesse dans le chargement des paramètres.

# Contributions et licences
Ce projet est open source et sous licence GPL-3.0. Je suis ouvert aux contributions.

Si vous souhaitez contribuer, n'hésitez pas à ouvrir une issue ou une pull request sur le dépôt GitHub.

Vous avez le droit de modifier le code source et de l'utiliser à des fins personnelles, mais pas commerciales. Vous n'avez pas le droit de distribuer des versions closed-sources (licence GPL-3.0).