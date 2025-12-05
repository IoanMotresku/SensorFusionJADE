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

            // 3. Lansare Senzor 1: TEMPERATURĂ
            // Configurație: ID, Tip, Unitate, SliderMin, SliderMax, HwMin, HwMax, SafeMin, SafeMax
            Object[] argsTemp = new Object[] {
                "SENS-TEMP-01", "Temperatură", "°C",
                -50, 150,   // Slider Range
                -30, 100,   // Hardware Range
                18, 25      // Safe Range
            };
            
            AgentController acS1 = mainContainer.createNewAgent(
                "SenzorTermic", 
                "clase.SensorAgent", 
                argsTemp
            );
            acS1.start();

            // 4. Lansare Senzor 2: PRESIUNE
            Object[] argsPres = new Object[] {
                "SENS-PRES-A", "Presiune Hidraulică", "Bar",
                0, 200,     // Slider
                0, 150,     // Hardware
                80, 110     // Safe
            };
            
            AgentController acS2 = mainContainer.createNewAgent(
                "SenzorPresiune", 
                "clase.SensorAgent", 
                argsPres
            );
            acS2.start();

            // 5. Lansare Senzor 3: UMIDITATE
            Object[] argsHum = new Object[] {
                "SENS-HUM-EXT", "Umiditate", "%",
                0, 100,
                0, 100,
                30, 60
            };
            
            AgentController acS3 = mainContainer.createNewAgent(
                "SenzorUmiditate", 
                "clase.SensorAgent", 
                argsHum
            );
            acS3.start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}