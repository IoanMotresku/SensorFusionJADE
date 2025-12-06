package clase;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;

public class DBAgent extends Agent {

    // --- CONFIGURARE ---
    // TODO: Înlocuiește cu link-ul tău din Firebase Console (păstrează slash-ul de la final)
    private static final String FIREBASE_URL = "https://sensorfusionjade-default-rtdb.europe-west1.firebasedatabase.app/";
    private static final String TABLE_NAME = "istoric_senzori"; // Numele nodului în JSON
    
    // Map to store unique sensor IDs and their types
    private final Map<String, String> sensorTypes = new ConcurrentHashMap<>();

    @Override
    protected void setup() {
        System.out.println("DBAgent [" + getLocalName() + "] a pornit. Conectat la Firebase.");

        // 1. Înregistrare în Pagini Aurii (DF)
        registerService();

        // 2. Comportament pentru a primi mesaje
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String convId = msg.getConversationId();
                    if (convId != null) {
                        switch (convId) {
                            case "save-db":
                                saveToFirebase(msg.getContent());
                                break;
                            case "get-all-sensors":
                                handleGetAllSensors(msg);
                                break;
                            case "get-sensor-data":
                                handleGetSensorData(msg);
                                break;
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) {}
        System.out.println("DBAgent [" + getLocalName() + "] oprit.");
    }
    
    // Salvează datele în Firebase
    private void saveToFirebase(String sensorJson) {
        try {
            URL url = new URL(FIREBASE_URL + TABLE_NAME + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String finalJson = sensorJson.substring(0, sensorJson.lastIndexOf("}")) 
                             + ", \"timestamp\":\"" + time + "\"}";
                             
            // Adaugam senzorul la lista de senzori unici
            JSONObject obj = new JSONObject(finalJson);
            addUniqueSensor(obj.getString("id"), obj.getString("type"));

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = finalJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            if (code == 200) {
                System.out.println("DBAgent >> Salvat în Cloud: " + sensorJson);
            } else {
                System.err.println("DBAgent >> Eroare Firebase: " + code);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("DBAgent >> Eroare conexiune internet!");
        }
    }
    
    // Adaugă un senzor nou în cache dacă nu există deja
    private void addUniqueSensor(String id, String type) {
        sensorTypes.putIfAbsent(id, type);
    }
    
    // Gestionează cererea pentru toți senzorii
    private void handleGetAllSensors(ACLMessage request) {
        JSONArray sensorsArray = new JSONArray();
        for (String id : sensorTypes.keySet()) {
            sensorsArray.put(id);
        }
        
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("sensor-list-response");
        reply.setContent(sensorsArray.toString());
        send(reply);
    }

    // Gestionează cererea de date istorice
    private void handleGetSensorData(ACLMessage request) {
        // Simulăm extragerea datelor din baza de date
        // Într-o implementare reală, aici s-ar face o interogare la Firebase
        System.out.println("DBAgent a primit o cerere pentru date istorice: " + request.getContent());
        
        // Mock data pentru demonstrație
        JSONObject responseData = new JSONObject();
        
        // Exemplu de date pentru SenzorTermic1
        JSONArray dataTermic1 = new JSONArray();
        dataTermic1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:10").put("value", 21));
        dataTermic1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:15").put("value", 22));
        dataTermic1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:20").put("value", 21));
        
        // Exemplu de date pentru SenzorPresiune1
        JSONArray dataPresiune1 = new JSONArray();
        dataPresiune1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:12").put("value", 95));
        dataPresiune1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:17").put("value", 98));
        dataPresiune1.put(new JSONObject().put("timestamp", "2025-12-06 20:56:22").put("value", 94));
        
        responseData.put("SenzorTermic1", dataTermic1);
        responseData.put("SenzorPresiune1", dataPresiune1);
        
        ACLMessage reply = request.createReply();
        reply.setPerformative(ACLMessage.INFORM);
        reply.setConversationId("sensor-data-response");
        reply.setContent(responseData.toString());
        send(reply);
    }

    // Înregistrarea serviciului în Pagini Aurii
    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("database-service");
        sd.setName("Firebase-Logger");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}