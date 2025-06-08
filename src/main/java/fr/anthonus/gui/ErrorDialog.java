package fr.anthonus.gui;

import javax.swing.*;

public class ErrorDialog extends JDialog {

    public ErrorDialog(JFrame owner, String message) {
        super(owner, "Erreur", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(400, 200);
        setLocationRelativeTo(owner);

        JLabel label = new JLabel("<html><body style='width: 300px;'>" + message + "</body></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);
        add(label);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> dispose());
        add(okButton, "South");

        pack();
    }

    public static boolean showError(JFrame owner, String message) {
        ErrorDialog dialog = new ErrorDialog(owner, message);
        dialog.setVisible(true);
        return true; // Retourne true pour indiquer que l'erreur a été affichée
    }
}
