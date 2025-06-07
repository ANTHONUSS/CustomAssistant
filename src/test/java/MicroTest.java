import be.tarsos.dsp.AudioDispatcher;
import be.tarsos.dsp.io.TarsosDSPAudioFormat;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.io.jvm.JVMAudioInputStream;
import be.tarsos.dsp.io.jvm.WaveformWriter;

import javax.sound.sampled.*;

public class MicroTest {
    public static void main(String[] args) throws LineUnavailableException, InterruptedException {
        int sampleRate = 44100;
        int bufferSize = 2048;

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

        AudioDispatcher dispatcher = new AudioDispatcher(audioStream, bufferSize, 0);

        WaveformWriter writer = new WaveformWriter(format, "output.wav");
        dispatcher.addAudioProcessor(writer);

        Thread recordingThread = new Thread(dispatcher, "Audio Recorder");
        recordingThread.start();

        Thread.sleep(5000);
        dispatcher.stop();
    }
}