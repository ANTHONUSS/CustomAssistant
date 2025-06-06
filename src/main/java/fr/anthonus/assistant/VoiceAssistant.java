package fr.anthonus.assistant;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.SilenceDetector;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.AudioPlayer;
import be.tarsos.dsp.writer.WriterProcessor;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import fr.anthonus.Main;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;

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

    private String prompt;

    // pour la personnalité de l'assistant
    private final ImageIcon image = new ImageIcon("data/assistantCustomisation/image.png");
    private String textPersonality;
    private String voicePersonality;

    // pour l'image
    private JLabel imageLabel;
    private int imageWidth;
    private int imageHeight;

    // pour l'animation de la fenêtre
    private int currentY = -1;
    private int targetY = -1;
    private Timer animationTimer;

    public VoiceAssistant(String prompt) {
        File textPersonalityFile = new File("data/assistantCustomisation/textPersonality.txt");
        File voicePersonalityFile = new File("data/assistantCustomisation/voicePersonality.txt");
        textPersonality = writeFileToString(textPersonalityFile);
        voicePersonality = writeFileToString(voicePersonalityFile);

        if (prompt != null) this.prompt = prompt;

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

        imageLabel = new JLabel(image);
        add(imageLabel);

        setFocusableWindowState(false);

        //décalage sur le côté puis écoute du prompt
        int startX = -imageWidth;
        int startY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
        int endY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
        setLocation(startX, startY);
        setVisible(true);
        slide(30, 30, startY, endY, null);
        if (prompt == null) {
            listenToPrompt();
        } else {
            // Si l'assistant est en mode texte, on traite directement le prompt (pas d'écoute puisque pas de micro)
            processPrompt(null, prompt);
        }
    }

    private void listenToPrompt() {
        int sampleRate = Main.porcupine.getSampleRate();
        int frameLength = Main.porcupine.getFrameLength();

        int maxSilenceDurationFrames = (sampleRate / frameLength);

        int[] silenceFrames = {0};

        List<byte[]> audioChunks = new ArrayList<>();

        AudioDispatcher promptDispatcher;
        try {
            promptDispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, frameLength, 0);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        SilenceDetector silenceDetector = new SilenceDetector(-70, false);
        promptDispatcher.addAudioProcessor(silenceDetector);

        AudioDispatcher finalPromptDispatcher = promptDispatcher;
        promptDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] buffer = audioEvent.getFloatBuffer();
                byte[] bytesBuffer = audioEvent.getByteBuffer();
                audioChunks.add(bytesBuffer);

                if (silenceDetector.isSilence(buffer)){
                    silenceFrames[0]++;

                    if (silenceFrames[0] >= maxSilenceDurationFrames) {
                        finalPromptDispatcher.stop();
                        SwingUtilities.invokeLater(() -> {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try {
                                for (byte[] chunk : audioChunks) {
                                    out.write(chunk);
                                }
                                processPrompt(out.toByteArray(), null);
                            } catch (IOException e) {
                                LOGs.sendLog("Erreur lors de la lecture des chunks audio : " + e.getMessage(), DefaultLogType.ERROR);
                            }
                        });
                    }
                } else {
                    silenceFrames[0] = 0;
                }
                return true;

            }

            @Override
            public void processingFinished() {}
        });

        new Thread(promptDispatcher, "Prompt Dispacher").start();

    }

    private void processPrompt(byte[] audioData, String prompt) {
        //builder le client OpenAI
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(Main.openAIKey)
                .build();
        String transcriptionText = null;

        if (audioData != null && prompt == null) {
            // transcritpion de l'audio en texte
            transcriptionText = audioToText(client, audioData);
        }

        //envoi de la requête chatGPT
        String responseText = getOpenaiResponse(client, (audioData != null && prompt == null? transcriptionText : prompt));

        //génération et lecture de la réponse audio avec animation
        try {
            byte[] audioResponse = getOpenaiSpeechBytes(responseText); // get de la réponse en audio
            AudioFormat format = new AudioFormat(24000, 16, 1, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(audioResponse);
            AudioInputStream ais = new AudioInputStream(bais, format, audioResponse.length / format.getFrameSize());

            playAudioWithAnimation(audioResponse, format);

        } catch (IOException | LineUnavailableException | InterruptedException | UnsupportedAudioFileException e) {
            throw new RuntimeException(e);

        } finally {
            int startY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
            int endY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
            slide(30, 30, startY, endY, () -> {
                Main.assistantInUse = false;
                Main.launchDispatcher();
                dispose();
            });
        }
    }

    private String audioToText(OpenAIClient client, byte[] audioData) {
        File audioFile;
        try {
            LOGs.sendLog("Traitement de la prompt...", DefaultLogType.DEFAULT);
            audioFile = writeAudioToTempFile(audioData);
        } catch (Exception e) {
            LOGs.sendLog("Erreur lors de l'écriture du fichier audio temporaire : " + e.getMessage(), DefaultLogType.ERROR);
            return null;
        }

        LOGs.sendLog("Envoi de la requête de transcription...", DefaultLogType.DEFAULT);
        TranscriptionCreateParams transcriptionParams = TranscriptionCreateParams.builder()
                .file(audioFile.toPath())
                .model(AudioModel.WHISPER_1)
                .build();

        Transcription transcription = client.audio().transcriptions().create(transcriptionParams).asTranscription();
        String transcriptionText = transcription.text();
        LOGs.sendLog("Transcription : " + transcriptionText, DefaultLogType.DEFAULT);

        return transcriptionText;
    }

    private String getOpenaiResponse(OpenAIClient client, String prompt) {
        LOGs.sendLog("Envoi de la requête chatGPT...", DefaultLogType.DEFAULT);
        ChatCompletionCreateParams chatParams = ChatCompletionCreateParams.builder()
                .model(ChatModel.GPT_4_1_NANO)
                .maxCompletionTokens(100)
                .addSystemMessage("Vous êtes un assistant vocal qui répond aux questions de manière concise et utile. N'utilisez AUCUN caractère spécial à part des ponctuations de base comme .,?!'.")
                .addSystemMessage(textPersonality)
                .addUserMessage(prompt)
                .build();

        ChatCompletion chatCompletion = client.chat().completions().create(chatParams);
        String responseText = chatCompletion.choices().get(0).message().content().get();
        LOGs.sendLog("Message : " + responseText, DefaultLogType.DEFAULT);

        //enlever les caractères spéciaux
        responseText = responseText.replaceAll("[^a-zA-Z0-9 .,?!']", " ");

        return responseText;
    }

    private void playAudioWithAnimation(byte[] byteArray, AudioFormat format) throws IOException, LineUnavailableException, UnsupportedAudioFileException {
        LOGs.sendLog("Lecture de l'audio avec animation...", DefaultLogType.DEFAULT);

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
        AudioPlayer audioPlayer = new AudioPlayer(format);

        playerDispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                double rms = AudioEvent.calculateRMS(audioEvent.getFloatBuffer())*3;
                double norm = Math.min(1.0, rms);

                int newOffset = (int)(imageHeight*0.2*norm);

                targetY = baseY - newOffset;

                return true;
            }

            @Override
            public void processingFinished() {}
        });


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
            LOGs.sendLog("Réponse audio reçue (taille: " + response.body().length + " bytes)", DefaultLogType.DEFAULT);
            return response.body();
        } else {
            LOGs.sendLog("Erreur lors de la génération de la réponse audio : " + response.statusCode(), DefaultLogType.ERROR);
            throw new IOException("Erreur API: " + response.statusCode());
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
            LOGs.sendLog("Erreur lors de la lecture du fichier de description : " + e.getMessage(), DefaultLogType.ERROR);
            return "";
        }
    }

    private File writeAudioToTempFile(byte[] audioData) throws Exception {
        // Crée le dossier temp s'il n'existe pas
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
        // Crée un fichier temporaire dans le dossier temp
        File tempFile = File.createTempFile("audio_", ".wav", tempDir);

        AudioFormat format = new AudioFormat(Main.porcupine.getSampleRate(), 16, 1, true, false);
        ByteArrayInputStream bais = new ByteArrayInputStream(audioData);
        AudioInputStream ais = new AudioInputStream(bais, format, audioData.length / format.getFrameSize());
        AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile);
        ais.close();
        return tempFile;
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


}
