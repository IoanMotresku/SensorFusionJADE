package clase;

import org.json.JSONArray;
import org.json.JSONObject;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.core.behaviours.OneShotBehaviour;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import javax.swing.UIManager;

// Importul lipsă care cauzează eroarea "cannot be resolved to a type"
import clase.ShowGuiAgent;

import com.formdev.flatlaf.FlatDarkLaf;

import java.nio.file.Files;
import java.nio.file.Paths;

public class SystemStarter {

    public static void main(String[] args) {
        System.out.println("=============================================");
        System.out.println("== EXECUTING SystemStarter.main() NOW... ==");
        System.out.println("=============================================");
        
        // Setăm tema vizuală o singură dată, la pornirea aplicației.
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF. Exiting.");
            return;
        }

        // Verificăm dacă primim argumentul "autostart"
        if (args.length > 0 && "autostart".equalsIgnoreCase(args[0])) {
            System.out.println("MOD AUTOSTART ACTIVAT.");
            runAutostartSequence();
        } else {
            System.out.println("MOD STANDARD: Se pornește doar serverul JADE.");
            startMainContainer();
        }
    }

    /**
     * Pornește doar containerul principal și agenții de sistem.
     */
    private static void startMainContainer() {
        System.out.println("Lansare container principal JADE...");
        try {
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl();
            p.setParameter(Profile.MAIN_HOST, "localhost");
            // Dezactivăm GUI-ul JADE pentru a preveni conflictele de Look & Feel pe macOS
            p.setParameter(Profile.GUI, "false");
            ContainerController mainContainer = rt.createMainContainer(p);
            
            System.out.println("Lansare agenți de sistem (Controller, DatabaseManager)...");
            mainContainer.createNewAgent("Controller", "clase.ControllerAgent", null).start();
            mainContainer.createNewAgent("DatabaseManager", "clase.DBAgent", null).start();
            System.out.println("Containerul principal și agenții de sistem au pornit cu succes.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Rulează secvența completă: server, și ambele interfețe grafice.
     */
    private static void runAutostartSequence() {
        // 1. Pornim serverul JADE
        System.out.println("AUTOSTART: Se pornește serverul JADE...");
        startMainContainer();

        // Pauză pentru a permite inițializarea completă a serverului
        try { Thread.sleep(2000); } catch (InterruptedException e) {}

        // 2. Lansăm interfața pentru lansatorul de senzori
        System.out.println("AUTOSTART: Se deschide LauncherGui...");
        javax.swing.SwingUtilities.invokeLater(() -> new LauncherGui().setVisible(true));
        
        // 3. După o pauză mai mare, cerem afișarea GUI-ului de monitorizare
        try { Thread.sleep(2500); } catch (InterruptedException e) {}
        System.out.println("AUTOSTART: Se deschide ControllerGui...");
        requestControllerGui();
    }

    /**
     * Trimite mesajul "show-gui" către ControllerAgent folosind noul ShowGuiAgent.
     */
    private static void requestControllerGui() {
        System.out.println("AUTOSTART: Se cere afișarea GUI-ului de monitorizare...");
        try {
            Runtime rt = Runtime.instance();
            Profile p = new ProfileImpl(false); // Profil pentru container periferic
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            AgentContainer peripheralContainer = rt.createAgentContainer(p);

            // Agent temporar pentru a trimite mesajul
            String proxyName = "gui-starter-auto-" + System.currentTimeMillis();
            // Argumentul este numele agentului căruia îi trimitem comanda
            Object[] agentArgs = new Object[]{"Controller"};

            AgentController proxy = peripheralContainer.createNewAgent(proxyName, ShowGuiAgent.class.getName(), agentArgs);
            proxy.start();

        } catch (Exception e) {
            System.err.println("AUTOSTART: Eroare la solicitarea GUI-ului de monitorizare.");
            e.printStackTrace();
        }
    }
}
