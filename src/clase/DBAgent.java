package clase;

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

public class DBAgent extends Agent {

    // --- CONFIGURARE ---
    // TODO: Înlocuiește cu link-ul tău din Firebase Console (păstrează slash-ul de la final)
    private static final String FIREBASE_URL = "https://sensorfusionjade-default-rtdb.europe-west1.firebasedatabase.app/";
    private static final String TABLE_NAME = "istoric_senzori"; // Numele nodului în JSON

    @Override
    protected void setup() {
        System.out.println("DBAgent [" + getLocalName() + "] a pornit. Conectat la Firebase.");

        // 1. Înregistrare în Pagini Aurii (DF)
        registerService();

        // 2. Ascultare cereri de salvare
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Procesăm doar mesajele REQUEST care au conversația "save-db"
                    if (msg.getPerformative() == ACLMessage.REQUEST && 
                        "save-db".equals(msg.getConversationId())) {
                        
                        String jsonData = msg.getContent();
                        saveToFirebase(jsonData);
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

    // --- LOGICA DE TRIMITERE HTTP (REST) ---
    private void saveToFirebase(String sensorJson) {
        try {
            // Construim URL-ul complet: URL_BAZA + TABEL + .json
            // Ex: https://.../istoric_senzori.json
            URL url = new URL(FIREBASE_URL + TABLE_NAME + ".json");
            
            // Deschidem conexiunea
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST"); // POST adaugă o intrare nouă cu ID unic
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setDoOutput(true);

            // Adăugăm un timestamp la JSON-ul primit pentru istoric
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            // Truc simplu pentru a insera timestamp-ul în JSON-ul existent (înainte de ultima acoladă)
            String finalJson = sensorJson.substring(0, sensorJson.lastIndexOf("}")) 
                             + ", \"timestamp\":\"" + time + "\"}";

            // Scriem datele
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = finalJson.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // Verificăm răspunsul (Code 200 = OK)
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

    // --- HELPER: Înregistrare DF ---
    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("database-service"); // Controlerul va căuta acest tip
        sd.setName("Firebase-Logger");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
}