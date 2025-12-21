package prsa.egosoft.netmapper;

import prsa.egosoft.netmapper.gui.MainWindow;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new MainWindow().setVisible(true);
        });
    }

    public static boolean isAdmin() {
        String os = System.getProperty("os.name").toLowerCase();

        try {
            if (os.contains("win")) {
                // En Windows, 'net session' devuelve error (código 1) si no eres admin
                Process process = Runtime.getRuntime().exec("net session");
                return process.waitFor() == 0;
            } else {
                // En Unix (Linux/macOS), el ID de usuario 0 siempre es root
                Process process = Runtime.getRuntime().exec("id -u");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String uid = reader.readLine();
                return "0".equals(uid);
            }
        } catch (Exception e) {
            return false;
        }
    }
}
