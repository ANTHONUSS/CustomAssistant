package fr.anthonus.gui;

import fr.anthonus.utils.DotEnvWriter;
import fr.anthonus.utils.SettingsLoader;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class SettingsWindow extends JFrame {
    private JPanel assistantCustomisationPanel;
    private JCheckBox webSearchCheckBox;
    private JCheckBox customVoiceCheckBox;
    private JLabel mangioRVCLabel;
    private JTextField mangioRVCTextField;
    private JButton saveSettingsButton;

    private JPanel customVoiceSelectorPanel;
    private JScrollPane rvcModelsScrollPane;
    private DefaultListModel<String> rvcModelsListModel;
    private JList<String> rvcModelsList;
    private JButton importRVCModelButton;
    private JButton modifyRVCModelButton;
    private JLabel selectedRVCModelLabel;

    private JPanel envPanel;
    private JLabel picovoiceAccessKeyLabel;
    private JTextField picovoiceAccessKeyTextField;
    private JLabel openAIKeyLabel;
    private JTextField openAIKeyTextField;
    private JButton saveEnvButton;

    public SettingsWindow() {
        setTitle("Paramètres de l'assistant");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 10, 10, 10);

        gbc.gridx = 0;
        gbc.gridy = 0;
        assistantCustomisationPanel = initSettingsFrame();
        panel.add(assistantCustomisationPanel, gbc);

        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        customVoiceSelectorPanel = initCustomVoiceSelectorFrame();
        panel.add(customVoiceSelectorPanel, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        envPanel = initEnvFrame();
        panel.add(envPanel, gbc);

        add(panel);
        pack();
    }

    private JPanel initSettingsFrame() {
        // Initialisation des composants
        webSearchCheckBox = new JCheckBox("Activer la recherche web");
        webSearchCheckBox.setSelected(SettingsLoader.enableWebSearch);
        customVoiceCheckBox = new JCheckBox("Activer la voix personnalisée");
        customVoiceCheckBox.setSelected(SettingsLoader.enableCustomVoice);
        mangioRVCLabel = new JLabel("Chemin de MangioRVC :");
        mangioRVCTextField = new JTextField(20);
        mangioRVCTextField.setText(SettingsLoader.mangioRVCPath != null ? SettingsLoader.mangioRVCPath.getAbsolutePath() : "");
        saveSettingsButton = new JButton("Sauvegarder les paramètres");
        saveSettingsButton.addActionListener(e -> {
            String mangioRVCPath = mangioRVCTextField.getText().trim();

            SettingsLoader.mangioRVCPath = new File(mangioRVCPath);
            SettingsLoader.enableWebSearch = webSearchCheckBox.isSelected();
            SettingsLoader.enableCustomVoice = customVoiceCheckBox.isSelected();

            SettingsLoader.saveSettings();

            JOptionPane.showMessageDialog(this, "Paramètres sauvegardés avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
        });

        // Ajout des composants à la fenêtre
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(webSearchCheckBox, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        panel.add(customVoiceCheckBox, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(mangioRVCLabel, gbc);
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        panel.add(mangioRVCTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(15, 0, 0, 0);
        panel.add(saveSettingsButton, gbc);

        return panel;
    }

    private JPanel initCustomVoiceSelectorFrame() {
        // Initialisation des composants
        rvcModelsListModel = new DefaultListModel<>();
        File rvcModelsDir = new File(RVCModelSettings.basePath);
        File[] rvcModelFiles = rvcModelsDir.listFiles((dir, name) -> name.endsWith(".pth"));
        for (File rvcModelFile : rvcModelFiles) {
            String name = rvcModelFile.getName().substring(0, rvcModelFile.getName().lastIndexOf("."));
            rvcModelsListModel.addElement(name);
        }
        rvcModelsList = new JList<>(rvcModelsListModel);
        rvcModelsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selectedModel = rvcModelsList.getSelectedValue();
                if (selectedModel != null) {
                    SettingsLoader.selectedRVCModelFile = new File(RVCModelSettings.basePath + selectedModel + ".pth");
                    SettingsLoader.selectedRVCIndexFile = new File(RVCModelSettings.basePath + selectedModel + ".index");
                    selectedRVCModelLabel.setText("Modèle sélectionné : " + selectedModel);
                } else {
                    SettingsLoader.selectedRVCModelFile = null;
                }
            }
        });

        rvcModelsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rvcModelsScrollPane = new JScrollPane(rvcModelsList);
        rvcModelsScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        importRVCModelButton = new JButton("Importer un modèle RVC");
        importRVCModelButton.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Ajouter un Modèle", FileDialog.LOAD);
            fd.setMultipleMode(false);
            fd.setVisible(true);

            if(fd.getFile()==null) return;
            File selectedFile = new File(fd.getDirectory(), fd.getFile());
            String modelName = selectedFile.getName().substring(0, selectedFile.getName().lastIndexOf("."));

            if (selectedFile.getName().endsWith(".pth")) {
                File newModelFile = new File(RVCModelSettings.basePath + modelName + ".pth");
                try {
                    Files.copy(selectedFile.toPath(), newModelFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                    JOptionPane.showMessageDialog(this, "Modèle ajouté avec succès !");

                    // Mettre à jour la liste des modèles RVC
                    rvcModelsListModel.addElement(modelName);
                    rvcModelsList.setSelectedValue(modelName, true);

                    new RVCModelSettings(this, newModelFile);

                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Erreur lors de la copie du modèle : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                }

            } else {
                JOptionPane.showMessageDialog(this, "Le fichier sélectionné n'est pas un fichier de modèle valide.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });
        modifyRVCModelButton = new JButton("Modifier le modèle sélectionné");
        modifyRVCModelButton.addActionListener(e -> {
            String selectedModel = rvcModelsList.getSelectedValue();
            if (selectedModel == null) {
                JOptionPane.showMessageDialog(this, "Veuillez sélectionner un modèle RVC à modifier.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            File modelFile = new File(RVCModelSettings.basePath + selectedModel + ".pth");
            if (!modelFile.exists()) {
                JOptionPane.showMessageDialog(this, "Le modèle sélectionné n'existe pas.", "Erreur", JOptionPane.ERROR_MESSAGE);
                return;
            }
            new RVCModelSettings(this, modelFile);
        });

        String text = SettingsLoader.selectedRVCModelFile != null ? "Modèle séléctionné : " + SettingsLoader.selectedRVCModelFile.getName().substring(0, SettingsLoader.selectedRVCModelFile.getName().lastIndexOf(".")) : "Aucun modèle sélectionné";
        selectedRVCModelLabel = new JLabel(text);

        // Ajout des composants à la fenêtre
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        panel.add(rvcModelsScrollPane, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(importRVCModelButton, gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        panel.add(modifyRVCModelButton, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        panel.add(selectedRVCModelLabel, gbc);

        return panel;
    }

    private JPanel initEnvFrame() {
        // Initialisation des composants
        picovoiceAccessKeyLabel = new JLabel("Clé d'accès Picovoice :");
        picovoiceAccessKeyTextField = new JTextField(20);
        openAIKeyLabel = new JLabel("Clé d'accès OpenAI :");
        openAIKeyTextField = new JTextField(20);
        saveEnvButton = new JButton("Sauvegarder les variables d'environnement");
        saveEnvButton.addActionListener(e -> {
            String picovoiceValue = picovoiceAccessKeyTextField.getText();
            boolean isPicovoiceKeyValid = false;
            boolean isOpenAIKeyValid = false;
            if (picovoiceValue.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Veuillez entrer une valeur pour la clé d'accès Picovoice", "Erreur", JOptionPane.ERROR_MESSAGE);
            } else {
                DotEnvWriter.writeEnv("PICOVOICE_ACCESS_KEY", picovoiceValue);
                isPicovoiceKeyValid = true;
            }

            String openAIValue = openAIKeyTextField.getText();
            if (openAIValue.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Veuillez entrer une valeur pour la clé d'accès OpenAI", "Erreur", JOptionPane.ERROR_MESSAGE);
            } else {
                DotEnvWriter.writeEnv("OPENAI_KEY", openAIValue);
                isOpenAIKeyValid = true;
            }

            if (isPicovoiceKeyValid && isOpenAIKeyValid) {
                JOptionPane.showMessageDialog(this, "Variables d'environnement chargées avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } else if (isPicovoiceKeyValid) {
                JOptionPane.showMessageDialog(this, "Clé d'accès Picovoice chargée avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
            } else if (isOpenAIKeyValid) {
                JOptionPane.showMessageDialog(this, "Clé d'accès OpenAI chargée avec succès.", "Succès", JOptionPane.INFORMATION_MESSAGE);
            }

        });

        // Ajout des composants à la fenêtre
        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(picovoiceAccessKeyLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        panel.add(picovoiceAccessKeyTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0;
        panel.add(openAIKeyLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 1.0;
        panel.add(openAIKeyTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.weightx = 0;
        panel.add(saveEnvButton, gbc);

        return panel;
    }

    public void updateModelsList(){
        rvcModelsListModel.removeAllElements();
        File rvcModelsDir = new File(RVCModelSettings.basePath);
        File[] rvcModelFiles = rvcModelsDir.listFiles((dir, name) -> name.endsWith(".pth"));
        for (File rvcModelFile : rvcModelFiles) {
            String name = rvcModelFile.getName().substring(0, rvcModelFile.getName().lastIndexOf("."));
            rvcModelsListModel.addElement(name);
        }
    }

    public static void main(String[] args) {
        SettingsLoader.loadSettings();

        SwingUtilities.invokeLater(() -> {
            SettingsWindow settingsWindow = new SettingsWindow();
            settingsWindow.setVisible(true);
        });
    }
}
