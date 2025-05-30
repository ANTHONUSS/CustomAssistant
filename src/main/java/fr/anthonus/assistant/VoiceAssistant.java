package fr.anthonus.assistant;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.ChatModel;
import com.openai.models.ResponsesModel;
import com.openai.models.audio.AudioModel;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionCreateParams;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.files.FileCreateParams;
import com.openai.models.files.FilePurpose;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
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

import static java.lang.System.out;

public class VoiceAssistant extends JFrame {
    private JLabel imageLabel;

    private final ImageIcon image = new ImageIcon("data/assistantCustomisation/image.png");
    private final File description = new File("data/assistantCustomisation/description.txt");

    private int imageWidth;
    private int imageHeight;

    public VoiceAssistant() {
        init();
    }

    private void init() {
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
        setLocation(Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70, -imageWidth);

        image.setImage(image.getImage().getScaledInstance(imageWidth, imageHeight, Image.SCALE_DEFAULT));

        imageLabel = new JLabel(image);
        add(imageLabel);

        setFocusableWindowState(false);
        setVisible(true);

        //décalage sur le coté puis écoute du prompt
        slide(-imageWidth, 25, null);
        listenToPrompt();
    }

    private void listenToPrompt() {
        int frameLength = Main.porcupine.getFrameLength();
        int sampleRate = Main.porcupine.getSampleRate();

        int silenceThreshold = 500; // seuil de silence en ms
        int maxSilenceDurationFrames = 2 * (sampleRate / frameLength); // durée maximale de silence en ms
        int silenceFrames = 0;

        byte[] buffer = new byte[frameLength * 2];
        short[] pcm = new short[frameLength];

        List<byte[]> audioChunks = new ArrayList<>(); // liste pour stocker les chunks audio

        while (true) { // on écoute la prompte jusqu'au silence
            int bytesRead = Main.microphone.read(buffer, 0, buffer.length);
            if (bytesRead == buffer.length) {
                audioChunks.add(buffer.clone()); // on ajoute le chunk audio à la liste

                for (int i = 0; i < frameLength; i++) {
                    int LSB = buffer[2 * i] & 0xFF;
                    int MSB = buffer[2 * i + 1];
                    pcm[i] = (short) ((MSB << 8) | LSB);
                }

                boolean isSilence = true;
                for (short sample : pcm) {
                    if (Math.abs(sample) > silenceThreshold) { // seuil de détection de la voix
                        isSilence = false;
                        break;
                    }
                }

                if (isSilence) {
                    silenceFrames++;
                    if (silenceFrames >= maxSilenceDurationFrames) {
                        // Si le silence dure trop longtemps, on arrête l'écoute
                        LOGs.sendLog("Silence détecté, arrêt de l'écoute.", DefaultLogType.DEFAULT);

                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        for (byte[] chunk : audioChunks) {
                            out.write(chunk, 0, chunk.length); // on écrit les chunks dans le ByteArrayOutputStream
                        }
                        byte[] audioData = out.toByteArray(); // on récupère les données audio

                        processPrompt(audioData);

                        break;
                    }
                } else {
                    silenceFrames = 0; // reset du compteur de silence
                }
            }

        }

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
                    .maxCompletionTokens(200)
                    .addSystemMessage("Vous êtes un assistant vocal qui répond aux questions de manière concise et utile. N'utilisez AUCUN caractère spécial à part des ponctuations de base comme .,?!'.")
                    .addUserMessage(transcriptionText)
                    .build();

            ChatCompletion chatCompletion = client.chat().completions().create(chatParams);
            String responseText = chatCompletion.choices().get(0).message().content().get();
            LOGs.sendLog("Message : " + responseText, DefaultLogType.DEFAULT);

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

                    // Lancer l'animation de sortie après la fin de la lecture
                    slide(25, -imageWidth, () -> {
                        Main.assistantInUse = false;
                        dispose();
                    });
                }
            });

            clip.start();

        } catch (Exception e) {
            LOGs.sendLog("Erreur lors de la génération ou de la lecture de la réponse audio : " + e.getMessage(), DefaultLogType.ERROR);

            // En cas d'erreur, on ferme quand même l'assistant
            slide(25, -imageWidth, () -> {
                Main.assistantInUse = false;
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
                    "response_format": "%s"
                }
                """, "gpt-4o-mini-tts", message, "alloy", "wav");

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

    private void slide(int startX, int endX, Runnable onFinish) {
        int y = Toolkit.getDefaultToolkit().getScreenSize().height - imageHeight - 70;
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
            setLocation(x, y);

            if (t >= 1f) {
                timer.stop();
                setLocation(endX, y);
                if (onFinish != null) onFinish.run();
            }
        });
        timer.start();
    }


}
