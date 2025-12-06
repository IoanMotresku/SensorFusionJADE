package clase;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

public class MainLauncher {

    public static void main(String[] args) {
        // 1. Pornire Platformă JADE (Main Container)
        Runtime rt = Runtime.instance();
        Profile p = new ProfileImpl();
        p.setParameter(Profile.MAIN_HOST, "localhost");
        p.setParameter(Profile.GUI, "false"); // Afișează GUI-ul standard JADE (RMA)
     
        
        try {
            ContainerController mainContainer = rt.createMainContainer(p);
            
            // 2. Lansare Controller
            AgentController acCtrl = mainContainer.createNewAgent(
                "Controller", 
                "clase.ControllerAgent", 
                null
            );
            acCtrl.start();
            
            AgentController acDB = mainContainer.createNewAgent(
            	    "DatabaseManager", 
            	    "clase.DBAgent", 
            	    null
            	);
            	acDB.start();
            
            // Așteptăm puțin să pornească controller-ul
            Thread.sleep(2000);

            // 3. Lansare dinamică a senzorilor din fișierul JSON
            try {
                String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("config/sensors.json")));
                org.json.JSONArray sensorsConfig = new org.json.JSONArray(content);

                for (int i = 0; i < sensorsConfig.length(); i++) {
                    org.json.JSONObject config = sensorsConfig.getJSONObject(i);
                    String baseName = config.getString("baseName");
                    int count = config.getInt("count");

                    for (int j = 1; j <= count; j++) {
                        String agentName = baseName + j;
                        String sensorId = "SENS-" + config.getString("type").substring(0, 4).toUpperCase() + "-" + j;

                        Object[] sensorArgs = new Object[] {
                            sensorId,
                            config.getString("type"),
                            config.getString("unit"),
                            config.getInt("sliderMin"),
                            config.getInt("sliderMax"),
                            config.getInt("hwMin"),
                            config.getInt("hwMax"),
                            config.getInt("safeMin"),
                            config.getInt("safeMax")
                        };

                        AgentController acS = mainContainer.createNewAgent(agentName, "clase.SensorAgent", sensorArgs);
                        acS.start();
                    }
                }
            } catch (java.io.IOException e) {
                System.err.println("Eroare la citirea fișierului sensors.json: " + e.getMessage());
                e.printStackTrace();
            } catch (org.json.JSONException e) {
                System.err.println("Eroare la parsarea fișierului sensors.json: " + e.getMessage());
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}