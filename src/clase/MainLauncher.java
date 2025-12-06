package clase;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class MainLauncher {

    public static void main(String[] args) {
        // Asigurăm crearea interfeței grafice pe firul de execuție dedicat (EDT)
        javax.swing.SwingUtilities.invokeLater(() -> {
            new LauncherGui().setVisible(true);
        });
    }
}