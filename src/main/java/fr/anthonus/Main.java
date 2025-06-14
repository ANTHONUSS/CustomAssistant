package fr.anthonus;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import fr.anthonus.assistant.VoiceAssistant;
import fr.anthonus.customAudioProcessors.RNNoiseProcessor;
import fr.anthonus.gui.ErrorDialog;
import fr.anthonus.gui.LoadEnvDialog;
import fr.anthonus.gui.SettingsWindow;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;
import fr.anthonus.utils.SettingsLoader;
import io.github.cdimascio.dotenv.Dotenv;

import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.NoSuchElementException;
import java.util.Scanner;

public class Main {
    public static Porcupine porcupine;
    private static String picovoiceAccessKey;

    private static AudioDispatcher wakeWordDispatcher;

    public static String openAIKey;

    public static void main(String[] args) throws AWTException {
        // Charge les paramètres
        SettingsLoader.loadSettings();

        // Créé le tray icon et l'initialise
        PopupMenu popup = new PopupMenu();
        addTrayItems(popup);

        Image icon = Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/ChatGPT_logo.png"));
        TrayIcon trayIcon = new TrayIcon(icon, "CustomAssistant", popup);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();
        tray.add(trayIcon);

        // Charge les variables d'environnement
        loadEnv();

        File keywordFile = new File("data/wakeWordsModels/ok-assistant_fr_windows_v3_0_0.ppn");
        File modelFile = new File("data/wakeWordsModels/porcupine_params_fr.pv");
        if (!keywordFile.exists() || !modelFile.exists()) {
            LOGs.sendLog("Fichiers de modèle introuvables. Assurez-vous que les fichiers sont présents dans le répertoire 'data/wakeWordsModels'.", DefaultLogType.ERROR);

            System.exit(1);
        }

        // écoute du prompt
        try {
            LOGs.sendLog("Chargement de l'écoute du mot clé", DefaultLogType.LOADING);
            // Init Porcupine avec le modèle
            porcupine = new Porcupine.Builder()
                    .setAccessKey(picovoiceAccessKey)
                    .setKeywordPath(keywordFile.getAbsolutePath())
                    .setModelPath(modelFile.getAbsolutePath())
                    .build();

            // Lance l'écoute du mot clé
            launchDispatcher();


            // Lance aussi l'écoute du prompt en texte (seulement si console disponible)
            try {
                Scanner sc = new Scanner(System.in);
                while (true) {
                    String prompt = sc.nextLine();
                    wakeWordDispatcher.stop();
                    VoiceAssistant.assistantInUse = true;
                    new VoiceAssistant(prompt);
                }
            } catch (NoSuchElementException e) {
                // Si une erreur ici, c'est qu'on l'a lancé en javaw, donc on ne fait rien
            }


        } catch (Exception e) {
            String errorMessage = "Erreur lors de l'initialisation de l'écoute du mot clé : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
        }
    }

    public static void launchDispatcher() {
        int frameLength = porcupine.getFrameLength();
        int sampleRate = porcupine.getSampleRate();

        // Initialisation du dispatcher pour écouter le mot clé
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine line;
        try {
            line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            line.start();
        } catch (LineUnavailableException e) {
            String errorMessage = "Erreur lors de l'ouverture du microphone : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
            return;
        }

        final AudioInputStream stream = new AudioInputStream(line);
        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);

        wakeWordDispatcher = new AudioDispatcher(audioStream, frameLength, 0);

        AudioProcessor rnnoiseProcessor = new RNNoiseProcessor();
        wakeWordDispatcher.addAudioProcessor(rnnoiseProcessor);

        AudioProcessor wakeWordProcessor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                // Convertit le buffer audio en short pour Porcupine
                float[] floatBuffer = audioEvent.getFloatBuffer();
                short[] pcm = new short[floatBuffer.length];
                for (int i = 0; i < floatBuffer.length; i++) {
                    pcm[i] = (short) (floatBuffer[i] * Short.MAX_VALUE);
                }

                try {
                    // Traite le buffer PCM avec Porcupine
                    int keyWordIndex = Main.porcupine.process(pcm);

                    // Si le mot clé est détecté, on lance l'assistant vocal
                    if (keyWordIndex >= 0 && !VoiceAssistant.assistantInUse) {
                        wakeWordDispatcher.stop();
                        VoiceAssistant.assistantInUse = true;

                        LOGs.sendLog("Mot-clé détecté ! Lancement en mode vocal...", DefaultLogType.AUDIO);

                        new VoiceAssistant(null); // Lancement de l'assistant vocal

                    } else if (keyWordIndex >= 0) { // Si l'assistant est déjà en cours d'utilisation
                        LOGs.sendLog("Assistant déjà en cours d'utilisation, mot-clé détecté mais ignoré.", DefaultLogType.AUDIO);
                    }

                } catch (PorcupineException e) {
                    String errorMessage = "Erreur lors du traitement du mot clé : " + e.getMessage();
                    LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
                    ErrorDialog.showError(null, errorMessage);
                    System.exit(1);
                }

                return true;
            }

            @Override
            public void processingFinished() {
            }

        };
        wakeWordDispatcher.addAudioProcessor(wakeWordProcessor);


        LOGs.sendLog("Démarrage de l'écoute...", DefaultLogType.AUDIO);
        // Démarre le dispatcher dans un thread séparé
        new Thread(wakeWordDispatcher, "Audio Dispatcher").start();
    }

    private static void addTrayItems(PopupMenu popup) {
        //Ajout de l'item pour autoriser le web search
        CheckboxMenuItem webSearchItem = new CheckboxMenuItem("Recherche web");
        webSearchItem.setState(SettingsLoader.enableWebSearch);
        webSearchItem.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                LOGs.sendLog("Recherche web activée", DefaultLogType.LOADING);
                SettingsLoader.enableWebSearch = true;
            } else {
                LOGs.sendLog("Recherche web désactivée", DefaultLogType.LOADING);
                SettingsLoader.enableWebSearch = false;
            }
            // Sauvegarde les paramètres après modification
            SettingsLoader.saveSettings();
        });
        popup.add(webSearchItem);

        //Ajout de l'item pour autoriser la voix personnalisée
        CheckboxMenuItem customVoiceItem = new CheckboxMenuItem("Voix personnalisée");
        customVoiceItem.setState(SettingsLoader.enableCustomVoice);
        customVoiceItem.addItemListener(e -> {
            if (SettingsLoader.mangioRVCPath == null) {
                // show dialog to inform the user to set the path
                String errorMessage = "Le chemin vers Mangio RVC n'est pas défini. Veuillez le configurer dans les paramètres.";
                LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
                ErrorDialog.showError(null, errorMessage);
            } else {
                if (e.getStateChange() == ItemEvent.SELECTED) {
                    LOGs.sendLog("Voix personnalisée activée", DefaultLogType.LOADING);
                    SettingsLoader.enableCustomVoice = true;
                } else {
                    LOGs.sendLog("Voix personnalisée désactivée", DefaultLogType.LOADING);
                    SettingsLoader.enableCustomVoice = false;
                }
                // Sauvegarde les paramètres après modification
                SettingsLoader.saveSettings();
            }
        });
        popup.add(customVoiceItem);

        popup.addSeparator();

        MenuItem settingsItem = new MenuItem("Paramètres");
        settingsItem.addActionListener(e -> {
            if (!SettingsWindow.isOpen) {
                SettingsWindow settingsWindow = new SettingsWindow();
                settingsWindow.setVisible(true);
                SettingsWindow.isOpen = true;
            }
        });
        popup.add(settingsItem);

        popup.addSeparator();

        // Ajout de l'exit
        MenuItem exitItem = new MenuItem("Quitter");
        exitItem.addActionListener(e -> System.exit(0));
        popup.add(exitItem);
    }

    private static void loadEnv() {
        Dotenv dotenv = Dotenv.configure()
                .directory("conf")
                .load();

        //Load ChatGPT api key
        LOGs.sendLog("Chargement du token ChatGPT...", DefaultLogType.LOADING);
        openAIKey = dotenv.get("OPENAI_KEY");
        if (openAIKey == null || openAIKey.isEmpty()) {
            openAIKey = LoadEnvDialog.showDialog(null, "Chargement du token OpenAI", "OPENAI_KEY");
        } else {
            LOGs.sendLog("Token OpenAI chargé", DefaultLogType.LOADING);
        }

        //Load picovoice access key
        LOGs.sendLog("Chargement du token picovoice...", DefaultLogType.LOADING);
        picovoiceAccessKey = dotenv.get("PICOVOICE_ACCESS_KEY");
        if (picovoiceAccessKey == null || picovoiceAccessKey.isEmpty()) {
            picovoiceAccessKey = LoadEnvDialog.showDialog(null, "Chargement du token Picovoice", "PICOVOICE_ACCESS_KEY");
        } else {
            LOGs.sendLog("Token picovoice chargé", DefaultLogType.LOADING);
        }
    }
}