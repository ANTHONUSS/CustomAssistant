import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.WaveformWriter;

import javax.sound.sampled.LineUnavailableException;

public class MicroTest {
    public static void main(String[] args) throws LineUnavailableException {
        int sampleRate = 16000;
        int bufferSize = 1024;
        int overlap = 0;

        AudioDispatcher dispatcher = AudioDispatcherFactory.fromDefaultMicrophone(sampleRate, bufferSize, overlap);
        TarsosDSPAudioFormat audioFormat = new TarsosDSPAudioFormat(sampleRate, 16, 1, true, false);
        dispatcher.addAudioProcessor(new WaveformWriter(audioFormat, "test_output.wav"));

        new Thread(dispatcher).start();

        System.out.println("Enregistrement... Parlez pendant 5 secondes.");
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        dispatcher.stop();
        System.out.println("Fichier WAV sauvegardé.");
    }
}