package fr.anthonus.gui;

import org.jetbrains.annotations.Contract;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class RVCModelSettings extends JFrame {
    public static final String basePath = "data/assistantCustomisation/rvcModels/";
    private final SettingsWindow parent;

    private String modelName;
    private File modelFile;
    private File indexFile;

    private JLabel modelNameLabel;
    private JTextField modelNameTextField;
    private JButton addDeleteIndexButton;
    private JButton saveModelButton;
    private JButton deleteModelButton;

    public RVCModelSettings(SettingsWindow parent, File modelFile) {
        this.parent = parent;

        this.modelFile = modelFile;
        this.modelName = modelFile.getName().substring(0, modelFile.getName().lastIndexOf("."));

        File indexFile = new File(basePath + modelName + ".index");
        if (indexFile.exists()) {
            this.indexFile = indexFile;
        } else {
            this.indexFile = null;
        }

        initPanel();
        setVisible(true);
    }

    private void initPanel() {
        setTitle("Paramètres du modèle RVC");
        setSize(300, 200);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);

        JPanel panel = new JPanel();
        panel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5, 5, 5, 5);


        // initialisation des composants
        modelNameLabel = new JLabel("Nom :");
        modelNameTextField = new JTextField(20);
        modelNameTextField.setText(modelName);

        JButton addIndexButton = new JButton("Ajouter un Index");
        JButton deleteIndexButton = new JButton("Supprimer l'Index");
        addIndexButton.addActionListener(e -> {
            FileDialog fd = new FileDialog(this, "Ajouter un Index", FileDialog.LOAD);
            fd.setMultipleMode(false);
            fd.setVisible(true);

            if(fd.getFile()==null) return;
            File selectedFile = new File(fd.getDirectory(), fd.getFile());

            if (selectedFile.getName().endsWith(".index")) {
                File newIndexFile = new File(basePath + modelName + ".index");
                try {
                    Files.copy(selectedFile.toPath(), newIndexFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    indexFile = newIndexFile;
                    JOptionPane.showMessageDialog(this, "Index ajouté avec succès !");

                    panel.remove(addDeleteIndexButton);
                    addDeleteIndexButton = deleteIndexButton;
                    gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0.0;
                    panel.add(addDeleteIndexButton, gbc);
                    panel.revalidate();
                    panel.repaint();
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(this, "Erreur lors de la copie de l'index : " + ex.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
                    throw new RuntimeException(ex);
                }

            } else {
                JOptionPane.showMessageDialog(this, "Le fichier sélectionné n'est pas un fichier d'index valide.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });
        deleteIndexButton.addActionListener(e -> {
            if (indexFile != null && indexFile.exists()) {
                int response = JOptionPane.showConfirmDialog(this, "Êtes-vous sûr de vouloir supprimer l'index ?", "Confirmation", JOptionPane.YES_NO_OPTION);
                if (response == JOptionPane.YES_OPTION) {
                    if (indexFile.delete()) {
                        indexFile = null;
                        JOptionPane.showMessageDialog(this, "Index supprimé avec succès !");

                        panel.remove(addDeleteIndexButton);
                        addDeleteIndexButton = addIndexButton;
                        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 0.0;
                        panel.add(addDeleteIndexButton, gbc);
                        panel.revalidate();
                        panel.repaint();
                    } else {
                        JOptionPane.showMessageDialog(this, "Erreur lors de la suppression de l'index.", "Erreur", JOptionPane.ERROR_MESSAGE);
                    }
                }
            } else {
                JOptionPane.showMessageDialog(this, "Aucun index à supprimer.", "Information", JOptionPane.INFORMATION_MESSAGE);
            }
        });

        if (indexFile == null) {
            addDeleteIndexButton = addIndexButton;
        } else {
            addDeleteIndexButton = deleteIndexButton;
        }


        saveModelButton = new JButton("Sauvegarder");
        saveModelButton.setBackground(Color.green);
        saveModelButton.addActionListener(e ->{
            String newModelName = modelNameTextField.getText().trim();
            if (!newModelName.isEmpty()) {
                File newModelFile = new File(basePath + newModelName + ".pth");
                if (modelFile.renameTo(newModelFile)) {
                    modelFile = newModelFile;
                    modelName = newModelName;
                    JOptionPane.showMessageDialog(this, "Modèle sauvegardé avec succès !");
                    if (indexFile != null) {
                        File newIndexFile = new File(basePath + newModelName + ".index");
                        if (indexFile.renameTo(newIndexFile)) {
                            indexFile = newIndexFile;

                            JOptionPane.showMessageDialog(this, "Index sauvegardé avec succès !");
                        } else {
                            JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde de l'index.", "Erreur", JOptionPane.ERROR_MESSAGE);
                        }
                    }

                    parent.updateModelsList();
                } else {
                    JOptionPane.showMessageDialog(this, "Erreur lors de la sauvegarde du modèle.", "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            } else {
                JOptionPane.showMessageDialog(this, "Le nom du modèle ne peut pas être vide.", "Erreur", JOptionPane.ERROR_MESSAGE);
            }
        });

        deleteModelButton = new JButton("Supprimer le modèle");
        deleteModelButton.setBackground(Color.red);
        deleteModelButton.addActionListener(e ->{
            int response = JOptionPane.showConfirmDialog(this, "Êtes-vous sûr de vouloir supprimer ce modèle ?", "Confirmation", JOptionPane.YES_NO_OPTION);
            if (response == JOptionPane.YES_OPTION) {
                if (modelFile.delete()) {
                    JOptionPane.showMessageDialog(this, "Modèle supprimé avec succès !");
                    if (indexFile != null && indexFile.delete()) {
                        JOptionPane.showMessageDialog(this, "Index supprimé avec succès !");
                    } else {
                        JOptionPane.showMessageDialog(this, "Erreur lors de la suppression de l'index", "Avertissement", JOptionPane.WARNING_MESSAGE);
                    }

                    parent.updateModelsList();
                    dispose();
                } else {
                    JOptionPane.showMessageDialog(this, "Erreur lors de la suppression du modèle.", "Erreur", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // placement des composants dans le panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(modelNameLabel, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        panel.add(modelNameTextField, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 0.0;
        panel.add(addDeleteIndexButton, gbc);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        panel.add(saveModelButton, gbc);
        gbc.gridx = 1;
        gbc.gridy = 2;
        panel.add(deleteModelButton, gbc);

        add(panel);

        pack();
    }
}
