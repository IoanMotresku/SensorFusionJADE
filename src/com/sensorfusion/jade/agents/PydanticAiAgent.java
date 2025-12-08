package com.sensorfusion.jade.agents;

import com.sensorfusion.jade.gui.PydanticAiGui;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.nio.charset.StandardCharsets; // Adaugă importul acesta sus

public class PydanticAiAgent extends Agent {

    private PydanticAiGui myGui;
    private AID dbAgentAID = null;
    private String lastQuery; // Reține întrebarea utilizatorului ("ce e?")

 // --- FIX: Forțăm HTTP_1_1 pentru stabilitate cu Uvicorn/FastAPI ---
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)  // <--- ACEASTA ESTE LINIA MAGICĂ
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    @Override
    protected void setup() {
        System.out.println("Pydantic AI Agent " + getLocalName() + " started.");

        SwingUtilities.invokeLater(() -> {
            myGui = new PydanticAiGui(this);
            myGui.setVisible(true);
        });

        requestSensorList();

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    processIncomingMessage(msg);
                } else {
                    block();
                }
            }
        });
    }

    @Override
    protected void takeDown() {
        System.out.println("Pydantic AI Agent " + getLocalName() + " is shutting down.");
        if (myGui != null) {
            SwingUtilities.invokeLater(() -> myGui.dispose());
        }
    }

    private void processIncomingMessage(ACLMessage msg) {
        String convId = msg.getConversationId();
        if (convId == null) return;

        switch (convId) {
            case "sensor-list-response":
                List<String> sensors = parseSensorList(msg.getContent());
                if (myGui != null) {
                    myGui.setSensorList(sensors);
                }
                break;

            case "sensor-data-response":
                // --- NEW: Aici a sosit JSON-ul crud de la Baza de Date ---
                String rawSensorJson = msg.getContent();

                if (myGui != null) {
                    // Feedback vizual că se procesează
                    myGui.appendToChat("[System]: Date primite. Se analizează cu Gemini AI...");
                }

                // Trimitem datele la Python pentru analiză (în background)
                askPythonBrain(lastQuery, rawSensorJson);
                break;
        }
    }

    /**
     * --- NEW: Metoda care trimite datele la serviciul Python FastAPI ---
     */
    private void askPythonBrain(String question, String rawDbResponse) {
        final String finalQuestion = (question == null || question.isEmpty()) 
                                     ? "Analizează datele." 
                                     : question;

        new Thread(() -> {
            try {
                // --- PASUL 1: Procesare și LIMITARE date (Optimized) ---
                JSONArray pythonSensorsPayload = new JSONArray();

                if (rawDbResponse != null && !rawDbResponse.trim().isEmpty()) {
                    try {
                        JSONObject dbData = new JSONObject(rawDbResponse);

                        for (String sensorId : dbData.keySet()) {
                            JSONArray readings = dbData.getJSONArray(sensorId);
                            
                            // Aici este FIX-ul: Luăm doar ultimele 30 de valori sau facem "sampling"
                            // Dacă sunt prea multe date, luăm doar din 10 în 10
                            int totalReadings = readings.length();
                            int step = 1;
                            if (totalReadings > 50) {
                                step = totalReadings / 30; // Reducem la aprox 30 puncte de date per senzor
                            }

                            for (int i = 0; i < totalReadings; i += step) {
                                JSONObject reading = readings.getJSONObject(i);

                                JSONObject s = new JSONObject();
                                s.put("id", sensorId);
                                
                                // Asigurăm tipurile corecte
                                double val = reading.optDouble("value", 0.0);
                                s.put("val", val);
                                
                                s.put("timestamp", reading.optString("timestamp", ""));
                                s.put("status", "OK"); // Default
                                s.put("unit", "?");
                                
                                // Determinare tip senzor
                                String lowerId = sensorId.toLowerCase();
                                if (lowerId.contains("termic") || lowerId.contains("temp")) s.put("type", "Temperatura");
                                else if (lowerId.contains("umidit")) s.put("type", "Umiditate");
                                else if (lowerId.contains("presiune")) s.put("type", "Presiune");
                                else s.put("type", "Senzor");

                                pythonSensorsPayload.put(s);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Eroare transformare JSON: " + e.getMessage());
                    }
                }

                // --- PASUL 2: Construcția Payload ---
                JSONObject payload = new JSONObject();
                payload.put("question", finalQuestion);
                payload.put("sensors", pythonSensorsPayload);

                String jsonToSend = payload.toString();
                System.out.println(">>> TRIMIT SPRE PYTHON (" + jsonToSend.length() + " chars): " + jsonToSend);

                // --- PASUL 3: HTTP Request cu UTF-8 Explicit ---
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8000/agent/analyze"))
                        .header("Content-Type", "application/json") // Simplu, fără charset extra uneori ajută
                        .POST(HttpRequest.BodyPublishers.ofString(jsonToSend, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // --- PASUL 4: Răspuns ---
                if (response.statusCode() == 200) {
                    JSONObject responseJson = new JSONObject(response.body());
                    String aiAnswer = "";
                    
                    if (responseJson.has("answer")) aiAnswer = responseJson.getString("answer");
                    else if (responseJson.has("data")) aiAnswer = responseJson.get("data").toString();
                    else aiAnswer = response.body();

                    final String finalAnswer = aiAnswer;
                    SwingUtilities.invokeLater(() -> {
                        myGui.appendToChat("AI: " + finalAnswer);
                        myGui.appendToChat("-------------------");
                    });
                } else {
                    System.err.println("Eroare Python: " + response.statusCode());
                    System.err.println("Body: " + response.body()); // Vedem exact ce zice serverul
                    SwingUtilities.invokeLater(() -> 
                        myGui.appendToChat("[Error]: Python Error " + response.statusCode())
                    );
                }

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> myGui.appendToChat("[System Error]: " + e.getMessage()));
            }
        }).start();
    }
    

    private List<String> parseSensorList(String jsonContent) {
        List<String> sensors = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(jsonContent);
            for (int i = 0; i < jsonArray.length(); i++) {
                sensors.add(jsonArray.getString(i));
            }
        } catch (Exception e) {
            System.err.println("Error parsing sensor list: " + e.getMessage());
        }
        return sensors;
    }

    private void findDBAgent() {
        if (dbAgentAID != null) return;

        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("database-service");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                dbAgentAID = result[0].getName();
            } else {
                System.out.println("PydanticAiAgent: DBAgent not found yet.");
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    public void requestSensorList() {
        findDBAgent();
        if (dbAgentAID != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(dbAgentAID);
            msg.setConversationId("get-all-sensors");
            send(msg);
        }
    }

    public void requestSensorData(String sensorId, Date startDate, Date endDate, String query) {
        findDBAgent();
        if (dbAgentAID != null) {
            this.lastQuery = query;

            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(dbAgentAID);
            msg.setConversationId("get-sensor-data");

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            
            JSONObject requestContent = new JSONObject();
            requestContent.put("sensors", new JSONArray(Collections.singletonList(sensorId)));
            requestContent.put("startDate", sdf.format(startDate));
            requestContent.put("endDate", sdf.format(endDate));

            msg.setContent(requestContent.toString());
            send(msg);

            // Doar notificăm utilizatorul că procesul a început
            myGui.appendToChat("[System]: Se caută date în DB pentru '" + sensorId + "'...");
            
        } else {
            if (myGui != null) {
                myGui.appendToChat("[System]: Eroare: Nu s-a găsit DBAgent.");
            }
        }
    }
}