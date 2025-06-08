package fr.anthonus.customAudioProcessors;

import be.tarsos.dsp.AudioEvent;
import be.tarsos.dsp.AudioProcessor;
import de.maxhenkel.rnnoise4j.Denoiser;
import de.maxhenkel.rnnoise4j.UnknownPlatformException;
import fr.anthonus.gui.ErrorDialog;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;

import java.io.IOException;
import java.util.LinkedList;

public class RNNoiseProcessor implements AudioProcessor {
    private final Denoiser denoiser;

    private static final LinkedList<Float> inputBuffer = new LinkedList<>();
    private static final LinkedList<Float> outputBuffer = new LinkedList<>();

    public RNNoiseProcessor() {
        try {
            denoiser = new Denoiser();
        } catch (IOException | UnknownPlatformException e) {
            String errorMessage = "Erreur lors de l'initialisation du dénoiseur RNNoise : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean process(AudioEvent audioEvent) {
        float[] input = audioEvent.getFloatBuffer();

        // emplilage le buffer glissant avec les échantillons d'entrée
        for (float sample : input) {
            inputBuffer.add(sample);
        }

        // traitement des blocs de 480 échantillons
        while (inputBuffer.size() >= 480) {
            // extrait un bloc de 480 échantillons
            short[] block = new short[480];
            for (int i = 0; i < 480; i++) {
                float sample = inputBuffer.removeFirst();
                block[i] = (short) Math.max(Math.min(sample * 32767, Short.MAX_VALUE), Short.MIN_VALUE);
            }

            // traite le bloc avec RNNoise
            short[] denoisedBlock = denoiser.denoise(block);

            // ajoute le bloc traité au buffer de sortie
            for (short sample : denoisedBlock) {
                outputBuffer.add(sample / 32768f); // normalisation
            }
        }

        // convertit le buffer de sortie en tableau float
        for(int i=0; i < input.length; i++){
            if (!outputBuffer.isEmpty()) {
                input[i] = outputBuffer.removeFirst();
            } else {
                input[i] = 0f; // si le buffer de sortie est vide, on remplit avec 0
            }
        }

        return true;
    }

    @Override
    public void processingFinished() {}
}
