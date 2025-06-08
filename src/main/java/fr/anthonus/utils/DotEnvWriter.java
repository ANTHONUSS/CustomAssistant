package fr.anthonus.utils;

import fr.anthonus.gui.ErrorDialog;
import fr.anthonus.logs.LOGs;
import fr.anthonus.logs.logTypes.DefaultLogType;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class DotEnvWriter {
    private static final File envFile = new File("conf/.env");

    public static void writeEnv(String key, String value) {
        if (!envFile.exists()) {
            throw new RuntimeException("Le fichier .env n'existe pas. Veuillez le créer dans le répertoire 'conf' depuis l'exemple du github.");
        }

        try (Scanner scanner = new Scanner(envFile)) {
            StringBuilder sb = new StringBuilder();
            boolean keyExists = false;
            key = key.trim().toUpperCase();

            while(scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();

                String actualKey = line.split("=")[0].trim();
                if(key.equalsIgnoreCase(actualKey)) {
                    // Si la clé existe déjà, on écrit la nouvelle valeur
                    sb.append(key).append("=").append(value).append("\n");
                    keyExists = true;
                } else {
                    // Sinon, on garde la ligne telle quelle
                    sb.append(line).append("\n");
                }
            }

            String content = sb.toString();

            // Si la clé n'existe pas, on throw une exception
            if (!keyExists) {
                String errorMessage = "La clé '" + key + "' n'existe pas dans le fichier .env. Veuillez la créer manuellement ou utiliser le bouton d'aide si vous ne savez pas comment faire.";
                LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
                ErrorDialog.showError(null, errorMessage);
                System.exit(1);
            }

            // Écrit le contenu dans le fichier .env
            java.nio.file.Files.write(envFile.toPath(), content.getBytes());


        } catch (IOException e) {
            String errorMessage = "Erreur lors de l'écriture dans le fichier .env : " + e.getMessage();
            LOGs.sendLog(errorMessage, DefaultLogType.ERROR);
            ErrorDialog.showError(null, errorMessage);
            System.exit(1);
        }

    }
}
