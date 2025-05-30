package fr.anthonus;

import ai.picovoice.porcupine.Porcupine;
import ai.picovoice.porcupine.PorcupineException;
import fr.anthonus.assistant.VoiceAssistant;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;
import io.github.cdimascio.dotenv.Dotenv;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.awt.*;
import java.io.File;

public class Main {
    public static Porcupine porcupine;
    public static TargetDataLine microphone;
    private static String picovoiceAccessKey;

    public static String openAIKey;

    public static boolean assistantInUse = false;

    public static void main(String[] args) throws AWTException {
        PopupMenu popup = new PopupMenu();
        addTrayItems(popup);

        Image icon = Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/ChatGPT_logo.png"));
        TrayIcon trayIcon = new TrayIcon(icon, "CustomAssistant", popup);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();
        tray.add(trayIcon);

        loadEnv();

        File keywordFile = new File("data/wakeWordsModels/ok-assistant_fr_windows_v3_0_0.ppn");
        File modelFile = new File("data/wakeWordsModels/porcupine_params_fr.pv");
        if (!keywordFile.exists() || !modelFile.exists()) {
            throw new RuntimeException("Fichiers de modèle introuvables. Assurez-vous que les fichiers sont présents dans le répertoire 'data/wakeWordsModels'.");
        }

        try {

            LOGs.sendLog("Chargement de l'écoute du mot clé", DefaultLogType.LOADING);
            // Init Porcupine avec le modèle
            porcupine = new Porcupine.Builder()
                    .setAccessKey(picovoiceAccessKey)
                    .setKeywordPath(keywordFile.getAbsolutePath())
                    .setModelPath(modelFile.getAbsolutePath())
                    .build();


            int frameLength = porcupine.getFrameLength();
            int sampleRate = porcupine.getSampleRate();

            // Format audio attendu par Porcupine
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false); // set du format audio
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format); // Utilisé pour demander à Java une ligne audio d’entrée compatible avec le format
            microphone = (TargetDataLine) AudioSystem.getLine(info); //accès au micro avec le format audio
            microphone.open(format); // ouverture du micro
            microphone.start(); // démarrage de l'écoute

            byte[] buffer = new byte[frameLength * 2]; // 2 octets par sample
            short[] pcm = new short[frameLength]; // (jsp ça veut dire quoi pcm mais oklm) utile car Porcupine attend un tableau de short

            LOGs.sendLog("Démarrage de l'écoute du micro...", DefaultLogType.LOADING);
            // écoute du micro en boucle
            while (true) {
                int bytesRead = microphone.read(buffer, 0, buffer.length); // lecture des données audio du micro
                boolean detected = voiceDetection(bytesRead, buffer, pcm, porcupine, frameLength);

                if (detected && !assistantInUse) { // si le mot-clé est détecté et que l'assistant est pas déjà utilisé
                    LOGs.sendLog("Mot-clé détecté !", DefaultLogType.DEFAULT);

                    VoiceAssistant assistant = new VoiceAssistant();
                    assistantInUse = true;
                } else if (detected && assistantInUse) {
                    LOGs.sendLog("Assistant déjà en cours d'utilisation, mot-clé ignoré.", DefaultLogType.DEFAULT);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static boolean voiceDetection(int bytesRead, byte[] buffer, short[] pcm, Porcupine porcupine, int frameLength) throws PorcupineException {
        if (bytesRead == buffer.length) {
            for (int i = 0; i < frameLength; i++) {
                int LSB = buffer[2 * i] & 0xFF; // "& 0xFF" pour convertir le byte de poids faible en int non signé
                int MSB = buffer[2 * i + 1]; // le byte de poids fort est déjà signé, donc pas besoin de "& 0xFF"
                pcm[i] = (short) ((MSB << 8) | LSB); // conversion des 2 octets en short
            }

            int keywordIndex = porcupine.process(pcm); // on process les données PCM pour détecter le mot-clé
            if (keywordIndex >= 0) { // si la voix actuelle correspond au mot-clé
                return true; // on retourne true pour indiquer que le mot-clé a été détecté
            }
        }

        return false; // sinon on retourne false
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