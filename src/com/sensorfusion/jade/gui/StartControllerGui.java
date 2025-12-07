package clase;

import jade.core.AID;
import jade.core.Agent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.lang.acl.ACLMessage;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

// Importul lipsă care cauzează eroarea
import clase.ShowGuiAgent;

public class StartControllerGui {

    public static void main(String[] args) {
        // Conectare la platforma JADE existentă
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl(false); // Profil non-main (periferic)
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.MAIN_PORT, "1099");
        
        jade.wrapper.AgentContainer peripheralContainer = rt.createAgentContainer(p);
        
        try {
            // Creăm și pornim agentul reutilizabil care va trimite comanda
            String proxyName = "gui-starter-" + System.currentTimeMillis();
            Object[] agentArgs = new Object[]{"Controller"}; // Argument: numele agentului țintă

            AgentController proxy = peripheralContainer.createNewAgent(
                proxyName, 
                ShowGuiAgent.class.getName(), 
                agentArgs
            );
            proxy.start();
            
            // Oprim containerul temporar după o secundă
            // Agentul se va autodistruge, dar containerul trebuie și el oprit.
             new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    peripheralContainer.kill();
                } catch (Exception e) {
                    // Ignorăm erorile, containerul s-ar putea să fie deja oprit
                }
            }).start();

        } catch (Exception e) {
            System.err.println("Eroare la crearea agentului proxy pentru afișarea GUI.");
            e.printStackTrace();
        }
    }
}
