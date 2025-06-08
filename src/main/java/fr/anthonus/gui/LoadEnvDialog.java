package fr.anthonus.gui;

import fr.anthonus.utils.DotEnvWriter;

import javax.swing.*;
import java.awt.*;

public class LoadEnvDialog extends JDialog {
    private final String keyToLoad;
    private final String getHelpLink = "https://github.com/antohnuss/CustomAssistant";

    private JLabel keyLabel;
    private JTextField valueTextField;

    private JButton loadButton;
    private JButton helpButton;

    public LoadEnvDialog(Frame owner, String title, String keyToLoad) {
        super(owner, title, true);
        this.keyToLoad = keyToLoad;

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        keyLabel = new JLabel(keyToLoad + "=");
        valueTextField = new JTextField(20);

        loadButton = new JButton("Valider");
        loadButton.addActionListener(e -> {
            String value = valueTextField.getText();
            if (value.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Veuillez entrer une valeur pour " + keyToLoad, "Erreur", JOptionPane.ERROR_MESSAGE);
            } else {
                DotEnvWriter.writeEnv(keyToLoad, value);

                JOptionPane.showMessageDialog(this, keyToLoad + " a été chargé avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
                dispose();
            }
        });

        helpButton = new JButton("Aide");
        helpButton.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Pour plus d'informations sur la configuration de " + keyToLoad + ", veuillez consulter :\n" + getHelpLink, "Aide", JOptionPane.INFORMATION_MESSAGE);
        });

        JPanel panel = new JPanel();
        panel.add(keyLabel);
        panel.add(valueTextField);
        panel.add(loadButton);
        panel.add(helpButton);

        add(panel);

        pack();
        setLocationRelativeTo(owner); // Centre la fenêtre par rapport au parent
    }

    public static String showDialog(Frame owner, String title, String keyToLoad) {
        LoadEnvDialog dialog = new LoadEnvDialog(owner, title, keyToLoad);
        dialog.setVisible(true);
        return dialog.valueTextField.getText(); // Retourne la valeur entrée par l'utilisateur
    }
}
