package fr.anthonus.utils;

import fr.anthonus.gui.ErrorDialog;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;

import java.io.File;
import java.nio.file.Files;
import java.util.Scanner;

public class SettingsLoader {
    private static final File settingsFile = new File("conf/settings.txt");

    public static boolean enableWebSearch;
    public static boolean enableCustomVoice;

    public static void loadSettings() {
        LOGs.sendLog("Chargement des paramètres...", DefaultLogType.LOADING);
        if (!settingsFile.exists()) {
            throw new RuntimeException("Settings file doesn't exist");
        }

        try (Scanner scanner = new Scanner(settingsFile)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                if (line.startsWith("enableWebSearch=")) {
                    enableWebSearch = Boolean.parseBoolean(line.split("=")[1]);
                } else if (line.startsWith("enableCustomVoice=")) {
                    enableCustomVoice = Boolean.parseBoolean(line.split("=")[1]);
                }
            }
        } catch (Exception e) {
            String errorMessage = "Erreur lors du chargement des paramètres : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
        }
        LOGs.sendLog("Paramètres chargés avec succès", DefaultLogType.LOADING);
    }

    public static void saveSettings() {
        LOGs.sendLog("Enregistrement des paramètres...", DefaultLogType.LOADING);
        StringBuilder content = new StringBuilder();
        content.append("enableWebSearch=").append(enableWebSearch).append("\n");
        content.append("enableCustomVoice=").append(enableCustomVoice).append("\n");

        try {
            Files.write(settingsFile.toPath(), content.toString().getBytes());
        } catch (Exception e) {
            String errorMessage = "Erreur lors de l'enregistrement des paramètres : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
        }
        LOGs.sendLog("Paramètres enregistrés avec succès", DefaultLogType.LOADING);
    }
}
