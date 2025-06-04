package fr.anthonus.assistant;

import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
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
    private JLabel imageLabel;

    private final ImageIcon image = new ImageIcon("data/assistantCustomisation/image.png");
    private String textPersonality;
    private String voicePersonality;

    private int imageWidth;
    private int imageHeight;

    public VoiceAssistant() {
        File textPersonalityFile = new File("data/assistantCustomisation/textPersonality.txt");
        File voicePersonalityFile = new File("data/assistantCustomisation/voicePersonality.txt");
        textPersonality = writeFileToString(textPersonalityFile);
        voicePersonality = writeFileToString(voicePersonalityFile);

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

        //décalage sur le coté puis écoute du prompt
        int startY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
        int endY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
        setVisible(true);
        slide(70, 70, startY, endY , null);
        listenToPrompt();
    }

    private void listenToPrompt() {
        int sampleRate = Main.porcupine.getSampleRate();
        int frameLength = Main.porcupine.getFrameLength();

        int silenceThreshold = 1000; // seuil de silence en ms
        int maxSilenceDurationFrames = 2 * (sampleRate / frameLength); // durée maximale de silence en ms

        List<byte[]> audioChunks = new ArrayList<>(); // liste pour stocker les chunks audio
        int[] silenceFrames = {0};

        AudioDispatcher dispatcher;
        try {
            dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, frameLength, 0);
        } catch (LineUnavailableException e) {
            throw new RuntimeException(e);
        }

        AudioDispatcher finalDispatcher = dispatcher;
        dispatcher.addAudioProcessor(new AudioProcessor() {
            @Override
            public boolean process(AudioEvent audioEvent) {
                float[] floatBuffer = audioEvent.getFloatBuffer();
                byte[] buffer = new byte[floatBuffer.length * 2];
                boolean isSilence = true;

                for (int i= 0; i < floatBuffer.length; i++) {
                    short sample = (short) (floatBuffer[i] * Short.MAX_VALUE);
                    buffer[i * 2] = (byte) (sample & 0xFF);
                    buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
                    if (Math.abs(sample) > silenceThreshold) { // Adjust threshold as needed
                        isSilence = false;
                    }
                }
                audioChunks.add(buffer);

                if(isSilence) {
                    silenceFrames[0]++;
                    if (silenceFrames[0] >= maxSilenceDurationFrames) {
                        finalDispatcher.stop();
                        SwingUtilities.invokeLater(() -> {
                            ByteArrayOutputStream out = new ByteArrayOutputStream();
                            try {
                                for (byte[] chunk : audioChunks) out.write(chunk);
                                processPrompt(out.toByteArray());
                            }catch (IOException e) {
                                LOGs.sendLog("Erreur lors de la lecture des chunks audio : " + e.getMessage(), DefaultLogType.ERROR);
                            }
                        });
                    }
                } else {
                    silenceFrames[0] = 0; // reset du compteur de silence
                }
                return true;
            }

            @Override
            public void processingFinished() {}
        });

        new Thread(dispatcher, "Prompt Dispacher").start();

    }

    private void processPrompt(byte[] audioData) {
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .apiKey(Main.openAIKey)
                .build();

        File audioFile;
        try {
            LOGs.sendLog("Traitement de la prompt...", DefaultLogType.DEFAULT);
            audioFile = writeAudioToTempFile(audioData);
        } catch (Exception e) {
            LOGs.sendLog("Erreur lors de l'écriture du fichier audio temporaire : " + e.getMessage(), DefaultLogType.ERROR);
            return;
        }

            LOGs.sendLog("Envoi de la requête de transcription...", DefaultLogType.DEFAULT);
            TranscriptionCreateParams transcriptionParams = TranscriptionCreateParams.builder()
                    .file(audioFile.toPath())
                    .model(AudioModel.WHISPER_1)
                    .build();

            Transcription transcription = client.audio().transcriptions().create(transcriptionParams).asTranscription();
            String transcriptionText = transcription.text();
            LOGs.sendLog("Transcription : " + transcriptionText, DefaultLogType.DEFAULT);

            LOGs.sendLog("Envoi de la requête chatGPT...", DefaultLogType.DEFAULT);
            ChatCompletionCreateParams chatParams = ChatCompletionCreateParams.builder()
                    .model(ChatModel.GPT_4_1_NANO)
                    .maxCompletionTokens(100)
                    .addSystemMessage("Vous êtes un assistant vocal qui répond aux questions de manière concise et utile. N'utilisez AUCUN caractère spécial à part des ponctuations de base comme .,?!'.")
                    .addSystemMessage(textPersonality)
                    .addUserMessage(transcriptionText)
                    .build();

            ChatCompletion chatCompletion = client.chat().completions().create(chatParams);
            String responseText = chatCompletion.choices().get(0).message().content().get();
            LOGs.sendLog("Message : " + responseText, DefaultLogType.DEFAULT);

        //enlever les caractères spéciaux
        responseText = responseText.replaceAll("[^a-zA-Z0-9 .,?!']", " ");

        try {
            LOGs.sendLog("Génération de la réponse audio...", DefaultLogType.DEFAULT);
            byte[] audioResponse = getOpenaiSpeechBytes(responseText);

            if (audioResponse == null || audioResponse.length == 0) {
                LOGs.sendLog("La réponse audio est vide", DefaultLogType.ERROR);
                return;
            }

            LOGs.sendLog("Lecture de l'audio...", DefaultLogType.DEFAULT);
            AudioFormat format = new AudioFormat(24000, 16, 1, true, false);
            ByteArrayInputStream bais = new ByteArrayInputStream(audioResponse);
            AudioInputStream ais = new AudioInputStream(bais, format, audioResponse.length / format.getFrameSize());

            Clip clip = AudioSystem.getClip();
            clip.open(ais);

            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    // Fermer les ressources audio
                    clip.close();
                    try {
                        ais.close();
                    } catch (IOException e) {
                        LOGs.sendLog("Erreur lors de la fermeture du flux audio : " + e.getMessage(), DefaultLogType.ERROR);
                    }
                }
            });

            clip.start();

        } catch (Exception e) {
            LOGs.sendLog("Erreur lors de la génération ou de la lecture de la réponse audio : " + e.getMessage(), DefaultLogType.ERROR);
        } finally {
            int startY = Toolkit.getDefaultToolkit().getScreenSize().height + imageHeight;
            int endY = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
            slide(70, 70, startY, endY, () -> {
                Main.assistantInUse = false;
                Main.launchDispatcher();
                dispose();
            });
        }
    }

    private byte[] getOpenaiSpeechBytes(String message) throws IOException {
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
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }

        if (response.statusCode() == 200) {
            LOGs.sendLog("Réponse audio reçue (taille: " + response.body().length + " bytes)", DefaultLogType.DEFAULT);
            return response.body();
        } else {
            LOGs.sendLog("Erreur lors de la génération de la réponse audio : " + response.statusCode(), DefaultLogType.ERROR);
            throw new IOException("Erreur API: " + response.statusCode());
        }

    }

    private String writeFileToString(File fileName){
        try(BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
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
