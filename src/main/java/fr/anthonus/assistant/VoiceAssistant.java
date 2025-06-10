package fr.anthonus.assistant;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.AudioPlayer;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.io.jvm.WaveformWriter;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.chat.completions.*;
import fr.anthonus.Main;
import fr.anthonus.customAudioProcessors.RNNoiseProcessor;
import fr.anthonus.gui.ErrorDialog;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;
import fr.anthonus.utils.SettingsLoader;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.ArrayList;

public class VoiceAssistant extends JFrame {

    // pour savoir si l'assistant est en cours d'utilisation
    public static boolean assistantInUse = false;

    // pour l'historique des prompts
    private static final List<ChatCompletionMessageParam> promptHistory = new ArrayList<>();

    private String prompt;

    // pour la personnalité de l'assistant
    private final ImageIcon image = new ImageIcon("data/assistantCustomisation/image.png");
    private final String textPersonality;
    private final String voicePersonality;

    private int imageWidth;
    private int imageHeight;

    // pour l'animation de la fenêtre
    private int currentY = -1;
    private int targetY = -1;
    private Timer animationTimer;
    private Timer listenAnimationTimer;
    private double angle = 0;
    private final double angleStep = 0.1; // ajuster la vitesse globale

    public VoiceAssistant(String prompt) {
        File textPersonalityFile = new File("data/assistantCustomisation/personality/textPersonality.txt");
        File voicePersonalityFile = new File("data/assistantCustomisation/personality/voicePersonality.txt");
        textPersonality = writeFileToString(textPersonalityFile);
        voicePersonality = writeFileToString(voicePersonalityFile);

        if (prompt != null) this.prompt = prompt;


        // Vérifier si le dossier temp existe, sinon le créer
        File tempDir = new File("temp");
        if (!tempDir.exists()) {
            tempDir.mkdirs();
        }
        // clear le dossier temp
        for (File file : tempDir.listFiles()) {
            if (file.isFile()) {
                file.delete();
            }
        }

        init();
    }

    private void init() {
        setVisible(false);
        setUndecorated(true);
        setAlwaysOnTop(true);
        setType(Window.Type.UTILITY);
        setBackground(new Color(0, 0, 0, 0));

        int trueImageWidth = image.getIconWidth();
        int trueImageHeight = image.getIconHeight();

        float ratio = (float) trueImageHeight / trueImageWidth;

        imageWidth = 200;
        imageHeight = Math.round(imageWidth * ratio);

        setSize(imageWidth, imageHeight);

        image.setImage(image.getImage().getScaledInstance(imageWidth, imageHeight, Image.SCALE_DEFAULT));

        // pour l'image
        JLabel imageLabel = new JLabel(image);
        add(imageLabel);

        setFocusableWindowState(false);

        //décalage sur le côté puis écoute du prompt
        int startX = -imageWidth;
        int startY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
        int endY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
        setLocation(startX, startY);
        setVisible(true);
        slide(30, 30, startY, endY, () -> {
            if (prompt == null) {
                listenToPrompt();
            } else {
                // Si l'assistant est en mode texte, on traite directement le prompt (pas d'écoute puisque pas de micro)
                // on lance le traitement dans un thread pour bien jouer l'animation
                new Thread(() -> {
                    processPrompt(null, prompt);
                }).start();
            }
        });

    }

    private void listenToPrompt() {
        startListenAnimation();

        int sampleRate = Main.porcupine.getSampleRate();
        int frameLength = Main.porcupine.getFrameLength();

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
            throw new RuntimeException(e);
        }

        AudioInputStream stream = new AudioInputStream(line);
        JVMAudioInputStream audioStream = new JVMAudioInputStream(stream);

        AudioDispatcher promptDispatcher = new AudioDispatcher(audioStream, frameLength, 0);

        int maxSilenceDurationFrames = (sampleRate / frameLength);
        int[] silenceFrames = {0};
        List<byte[]> audioBuffers = new ArrayList<>();
        SilenceDetector silenceDetector = new SilenceDetector(-35, false);
        promptDispatcher.addAudioProcessor(silenceDetector);

        AudioProcessor rnnoiseProcessor = new RNNoiseProcessor();
        promptDispatcher.addAudioProcessor(rnnoiseProcessor);

        final boolean[] isStarted = {false};
        AudioProcessor listenProcessor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                byte[] bytesBuffer = audioEvent.getByteBuffer().clone();
                audioBuffers.add(bytesBuffer);

                if (!isStarted[0] && silenceDetector.isSilence(buffer)) {
                    // Si le silence est détecté, on ne traite pas l'événement
                    audioBuffers.clear();
                    return true;
                } else if (!isStarted[0]) {
                    // Si on n'a pas encore commencé, on initialise le traitement
                    LOGs.sendLog("Début de l'écoute du prompt...", DefaultLogType.AUDIO);
                    isStarted[0] = true;
                }

                if (silenceDetector.isSilence(buffer)) {
                    silenceFrames[0]++;

                    if (silenceFrames[0] >= maxSilenceDurationFrames) {
                        promptDispatcher.stop();
                        stopListenAnimation();

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        for (byte[] audioBuffer : audioBuffers) {
                            out.write(audioBuffer, 0, audioBuffer.length);
                        }
                        processPrompt(out.toByteArray(), null);
                    }
                } else {
                    silenceFrames[0] = 0;
                }

                return true;

            }

            @Override
            public void processingFinished() {
            }
        };
        promptDispatcher.addAudioProcessor(listenProcessor);

        new Thread(promptDispatcher, "Prompt Dispacher").start();

    }

    private void processPrompt(byte[] audioData, String prompt) {
        //builder le client OpenAI
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(Main.openAIKey)
                .build();
        String transcriptionText = null;

        ChatCompletionUserMessageParam userMessage;
        if (audioData != null && prompt == null) {
            // transcritpion de l'audio en texte
            transcriptionText = audioToText(client, audioData);
            // et ajout dans l'historique des prompts
            userMessage = ChatCompletionUserMessageParam.builder()
                    .content(transcriptionText)
                    .build();
        } else {
            // si le prompt est déjà en texte, on l'ajoute directement
            userMessage = ChatCompletionUserMessageParam.builder()
                    .content(prompt)
                    .build();
        }
        promptHistory.add(ChatCompletionMessageParam.ofUser(userMessage));

        // ne garder que les 10 derniers prompts dans l'historique
        while (promptHistory.size() > 10) {
            promptHistory.remove(0);
        }

        //envoi de la requête chatGPT
        String responseText = getOpenaiResponse(client);
        // ajout de la réponse dans l'historique des prompts
        ChatCompletionAssistantMessageParam assistantMessage = ChatCompletionAssistantMessageParam.builder()
                .content(responseText)
                .build();
        promptHistory.add(ChatCompletionMessageParam.ofAssistant(assistantMessage));

        //génération et lecture de la réponse audio avec animation
        try {
            byte[] audioResponse = getOpenaiSpeechBytes(responseText); // get de la réponse en audio
            AudioFormat format = new AudioFormat(24000, 16, 1, true, false);

            if (SettingsLoader.enableCustomVoice) {
                audioResponse = changeAudioRVC(audioResponse, format);
                format = new AudioFormat(40000, 16, 1, true, false);
            }

            playAudioWithAnimation(audioResponse, format);

        } catch (Exception e) {
            String errorMessage = "Erreur lors de la génération ou de la lecture de la réponse audio : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
        } finally {
            int startY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
            int endY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
            slide(30, 30, startY, endY, () -> {
                assistantInUse = false;
                Main.launchDispatcher();
                dispose();
            });
        }
    }

    private byte[] changeAudioRVC(byte[] audioData, AudioFormat format) throws Exception {
        LOGs.sendLog("Changement de la voix avec RVC...", DefaultLogType.RVC);
        writeAudioToTempFile(audioData, "RVC/AssistantResponse", format);
        File tempRVCDir = new File("temp/RVC");

        File mangioRVCPath = new File("D:/Mangio-RVC/Data");
        File pythonRuntimePath = new File(mangioRVCPath.getAbsolutePath() + "/runtime/python.exe");
        File rvcModelFile = SettingsLoader.selectedRVCModelFile;
        File rvcModelIndexFile = new File("data/assistantCustomisation/rvcModels/model.index");

        ProcessBuilder pb = new ProcessBuilder(
                pythonRuntimePath.getAbsolutePath(), "infer_batch_rvc.py",
                Integer.toString(SettingsLoader.pitchRVCVoice),
                tempRVCDir.getAbsolutePath(),
                rvcModelIndexFile.getAbsolutePath(),
                "harvest",
                new File("temp/RVCOutput").getAbsolutePath(),
                rvcModelFile.getAbsolutePath(),
                "0.66",
                "cuda:0",
                "True",
                "3",
                "0",
                "1",
                "0.33"
        );
        pb.directory(mangioRVCPath);
        Process p = pb.start();

        try (BufferedReader stdout = new BufferedReader(new InputStreamReader(p.getInputStream()));
             BufferedReader stderr = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            stdout.lines().forEach(line -> LOGs.sendLog(line, DefaultLogType.RVC));
            stderr.lines().forEach(line -> LOGs.sendLog(line, DefaultLogType.RVC));
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            String errorMessage = "Erreur lors de l'exécution de RVC : code de sortie " + exitCode;
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            return audioData; // retourner l'audio original en cas d'erreur
        }

        File outputFile = new File("temp/RVCOutput/AssistantResponse.wav");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(outputFile)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
        } catch (IOException e) {
            return audioData;
        }

        return byteArrayOutputStream.toByteArray();
    }

    private String audioToText(OpenAIClient client, byte[] audioData) {
        File audioFile;
        try {
            LOGs.sendLog("Traitement de la prompt...", DefaultLogType.API);
            AudioFormat format = new AudioFormat(Main.porcupine.getSampleRate(), 16, 1, true, false);
            audioFile = writeAudioToTempFile(audioData, "userPrompt", format);
        } catch (Exception e) {
            String errorMessage = "Erreur lors de l'écriture du fichier audio temporaire : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            return null;
        }

        LOGs.sendLog("Envoi de la requête de transcription...", DefaultLogType.API);
        TranscriptionCreateParams transcriptionParams = TranscriptionCreateParams.builder()
                .file(audioFile.toPath())
                .model(AudioModel.WHISPER_1)
                .build();

        Transcription transcription = client.audio().transcriptions().create(transcriptionParams).asTranscription();
        String transcriptionText = transcription.text();
        LOGs.sendLog("Transcription : " + transcriptionText, DefaultLogType.API);

        return transcriptionText;
    }

    private String getOpenaiResponse(OpenAIClient client) {
        LOGs.sendLog("Envoi de la requête chatGPT...", DefaultLogType.API);
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .maxCompletionTokens(200)
                .addSystemMessage("""
                        Vous êtes un assistant vocal qui répond aux questions de manière concise et utile.
                        Vous avez un historique des 10 derniers messages envoyés par l'utilisateur et vous.
                        Le message va être envoyé à un modèle TTS, donc tenez en compte que le message sera lu par la suite.
                        N'utilisez aucun caractère spécial à part des ponctuations de base comme .,?!'.
                        Tu doit répondre en français, sauf si l'utilisateur te demande de répondre dans une autre langue.
                        """)
                .addSystemMessage(textPersonality)
                .messages(promptHistory);

        if (SettingsLoader.enableWebSearch) {
            builder.model(ChatModel.GPT_4O_MINI_SEARCH_PREVIEW)
                    .addSystemMessage("""
                            Ne citez pas vos sources.
                            L'utilisation d'internet ne doit pas rajouter de la longeur à la réponse. Uniquement des informations pertinentes.
                            """);

        } else {
            builder.model(ChatModel.GPT_4_1_NANO);
        }

        ChatCompletionCreateParams chatParams = builder.build();

        ChatCompletion chatCompletion = client.chat().completions().create(chatParams);
        String responseText = chatCompletion.choices().get(0).message().content().get();

        if (SettingsLoader.enableWebSearch) {
            // si la web search est activée, on demande à l'assistant de réécrire le résultat plus lisible pour le TTS
            ChatCompletionCreateParams rewriteParams = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4_1_NANO)
                    .maxCompletionTokens(200)
                    .addSystemMessage("""
                            Réécrivez la réponse de manière concise et lisible pour un TTS.
                            N'utilisez aucun caractère spécial à part des ponctuations de base comme .,?!'.
                            """)
                    .addUserMessage(responseText)
                    .build();
            ChatCompletion rewriteCompletion = client.chat().completions().create(rewriteParams);
            responseText = rewriteCompletion.choices().get(0).message().content().get();
        }

        //enlever les caractères spéciaux et remplacer les cédilles en c normaux
        responseText = responseText.replaceAll("[^a-zA-Z0-9 .,?!'çÇàÀâÂäÄéÉèÈêÊëËîÎïÏôÔöÖùÙûÛüÜœŒ\\-+]", " ");

        LOGs.sendLog("Message : " + responseText, DefaultLogType.API);

        return responseText;
    }

    private void playAudioWithAnimation(byte[] byteArray, AudioFormat format) throws IOException, LineUnavailableException, UnsupportedAudioFileException {
        LOGs.sendLog("Lecture de l'audio avec animation...", DefaultLogType.AUDIO);

        int baseY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;

        //Initialisation de l'animation
        if (currentY == -1) currentY = baseY;
        if (animationTimer == null) {
            animationTimer = new Timer(15, e -> {
                if (targetY != -1 && currentY != targetY) {
                    int dy = targetY - currentY;
                    currentY += Math.signum(dy) * Math.max(1, Math.abs(dy) / 4);
                    setLocation(30, currentY);
                    validate();
                }
            });
            animationTimer.start();
        }

        AudioDispatcher playerDispatcher = AudioDispatcherFactory.fromByteArray(byteArray, format, 1024, 0);

        AudioProcessor animationProcessor = new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                double rms = AudioEvent.calculateRMS(audioEvent.getFloatBuffer()) * 3;
                double norm = Math.min(1.0, rms);

                int newOffset = (int) (imageHeight * 0.2 * norm);

                targetY = baseY - newOffset;

                return true;
            }

            @Override
            public void processingFinished() {
            }
        };
        playerDispatcher.addAudioProcessor(animationProcessor);

        AudioPlayer audioPlayer = new AudioPlayer(format);
        playerDispatcher.addAudioProcessor(audioPlayer);

        playerDispatcher.run();
    }

    private byte[] getOpenaiSpeechBytes(String message) throws IOException, InterruptedException {
        String json = String.format("""
                {
                    "model": "%s",
                    "input": "%s",
                    "voice": "%s",
                    "response_format": "%s",
                    "instructions": "%s"
                }
                """, "gpt-4o-mini-tts", message, "alloy", "wav", voicePersonality);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openai.com/v1/audio/speech"))
                .header("Authorization", "Bearer " + Main.openAIKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<byte[]> response;
        response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() == 200) {
            LOGs.sendLog("Réponse audio reçue (taille: " + response.body().length + " bytes)", DefaultLogType.API);
            return response.body();
        } else {
            String errorMessage = "Erreur lors de la génération de la réponse audio : " + response.body().length + " bytes";
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
            return null;
        }

    }

    private String writeFileToString(File fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (IOException e) {
            String errorMessage = "Erreur lors de la lecture du fichier " + fileName.getName() + ": " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            return "";
        }
    }

    private File writeAudioToTempFile(byte[] audioData, String fileName, AudioFormat format) throws Exception {
        fileName = "temp/" + fileName + ".wav";

        AudioDispatcher writerDispatcher = AudioDispatcherFactory.fromByteArray(audioData, format, 1024, 0);

        WaveformWriter waveformWriter = new WaveformWriter(format, fileName);
        writerDispatcher.addAudioProcessor(waveformWriter);

        writerDispatcher.run();

        return new File(fileName);
    }

    private void slide(int startX, int endX, int startY, int endY, Runnable onFinish) {
        int duration = 600; // durée totale en ms
        int fps = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode().getRefreshRate(); // get les fps de l'écran
        if (fps <= 0) fps = 10; // Si le taux de rafraîchissement n'est pas défini, on utilise une valeur par défaut
        Timer timer = new Timer(1000 / fps, null);

        long startTime = System.currentTimeMillis();

        timer.addActionListener(e -> {
            float elapsed = System.currentTimeMillis() - startTime;
            float t = Math.min(elapsed / duration, 1f);
            // Ease-out cubic
            float ease = 1 - (float) Math.pow(1 - t, 3);
            int x = (int) (startX + (endX - startX) * ease);
            int y = (int) (startY + (endY - startY) * ease);
            setLocation(x, y);

            if (t >= 1f) {
                timer.stop();
                setLocation(endX, endY);
                if (onFinish != null) onFinish.run();
            }
        });
        timer.start();
    }

    private void startListenAnimation() {
        int baseX = 30;
        int baseY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
        int offsetX = 20;

        listenAnimationTimer = new Timer(30, e -> {
            angle += angleStep;
            // sin(angle) oscille entre -1 et +1
            int x = baseX + (int) (Math.sin(angle) * offsetX);
            setLocation(x, baseY);
        });
        listenAnimationTimer.start();

    }

    private void stopListenAnimation() {
        if (listenAnimationTimer != null) {
            listenAnimationTimer.stop();
        }
        // remettre à la position de base
        int baseX = 30;
        int baseY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
        slide(getX(), baseX, getY(), baseY, null);
    }


}
