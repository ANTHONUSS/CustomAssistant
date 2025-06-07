package fr.anthonus;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.filters.HighPass;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.AudioPlayer;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import fr.anthonus.assistant.VoiceAssistant;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;
import io.github.cdimascio.dotenv.Dotenv;

import javax.sound.sampled.*;
import java.awt.*;
import java.io.File;
import java.util.Scanner;

public class Main {
    public static Porcupine porcupine;
    private static String picovoiceAccessKey;

    private static AudioDispatcher wakeWordDispatcher;

    public static String openAIKey;

    public static boolean assistantInUse = false;

    public static void main(String[] args) throws AWTException {
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
            throw new RuntimeException("Fichiers de modèle introuvables. Assurez-vous que les fichiers sont présents dans le répertoire 'data/wakeWordsModels'.");
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

            // Lance aussi l'écoute du prompt en texte
            Scanner sc = new Scanner(System.in);
            while (true) {
                String prompt = sc.nextLine();
                wakeWordDispatcher.stop();
                Main.assistantInUse = true;
                new VoiceAssistant(prompt);
            }

        } catch (Exception e) {
            e.printStackTrace();
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
            throw new RuntimeException(e);
        }

        final AudioInputStream stream = new AudioInputStream(line);
        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);

        wakeWordDispatcher = new AudioDispatcher(audioStream, frameLength, 0);

        HighPass highPassFilter = new HighPass(200, sampleRate);
        wakeWordDispatcher.addAudioProcessor(highPassFilter);

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
                    if (keyWordIndex >= 0 && !Main.assistantInUse) {
                        wakeWordDispatcher.stop();
                        Main.assistantInUse = true;

                        LOGs.sendLog("Mot-clé détecté ! Lancement en mode vocal...", DefaultLogType.DEFAULT);

                        new VoiceAssistant(null); // Lancement de l'assistant vocal

                    } else if (keyWordIndex >= 0) { // Si l'assistant est déjà en cours d'utilisation
                        LOGs.sendLog("Assistant déjà en cours d'utilisation, mot-clé détecté mais ignoré.", DefaultLogType.DEFAULT);
                    }

                } catch (PorcupineException e) {
                    throw new RuntimeException(e);
                }

                return true;
            }

            @Override
            public void processingFinished() {
            }

        };
        wakeWordDispatcher.addAudioProcessor(wakeWordProcessor);


        LOGs.sendLog("Démarrage de l'écoute...", DefaultLogType.DEFAULT);
        // Démarre le dispatcher dans un thread séparé
        new Thread(wakeWordDispatcher, "Audio Dispatcher").start();
    }

    private static void addTrayItems(PopupMenu popup) {
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
            LOGs.sendLog("Clé API OpenAI non trouvé dans le fichier .env", DefaultLogType.LOADING);
            return;
        } else {
            LOGs.sendLog("Token OpenAI chargé", DefaultLogType.LOADING);
        }

        //Load picovoice access key
        LOGs.sendLog("Chargement du token picovoice...", DefaultLogType.LOADING);
        picovoiceAccessKey = dotenv.get("PICOVOICE_ACCESS_KEY");
        if (picovoiceAccessKey == null || picovoiceAccessKey.isEmpty()) {
            LOGs.sendLog("Clé picovoice non trouvé dans le fichier .env", DefaultLogType.LOADING);
            return;
        } else {
            LOGs.sendLog("Token picovoice chargé", DefaultLogType.LOADING);
        }
    }
}