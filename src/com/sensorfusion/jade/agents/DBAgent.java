package com.sensorfusion.jade.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
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
    private boolean isInitialDataFetched = false;

    @Override
    protected void setup() {
        System.out.println("DBAgent [" + getLocalName() + "] a pornit. Conectat la Firebase.");

        // Fetch existing sensors from Firebase on startup
        fetchInitialSensorData();

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
    
    private void fetchInitialSensorData() {
        if (isInitialDataFetched) return;

        System.out.println("DBAgent >> Se preiau senzorii existenți din Firebase...");
        try {
            URL url = new URL(FIREBASE_URL + TABLE_NAME + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            if (conn.getResponseCode() != 200) {
                System.err.println("DBAgent >> Eroare la preluarea datelor inițiale, cod: " + conn.getResponseCode());
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            JSONObject allData = new JSONObject(response.toString());
            for (String key : allData.keySet()) {
                JSONObject sensorRecord = allData.getJSONObject(key);
                if (sensorRecord.has("id") && sensorRecord.has("type")) {
                    addUniqueSensor(sensorRecord.getString("id"), sensorRecord.getString("type"));
                }
            }
            System.out.println("DBAgent >> Preluarea senzorilor finalizată. Senzori unici cunoscuți: " + sensorTypes.size());
            isInitialDataFetched = true;

        } catch (Exception e) {
            System.err.println("DBAgent >> Eroare la citirea datelor inițiale din Firebase: " + e.getMessage());
        }
    }
    
    // Adaugă un senzor nou în cache dacă nu există deja
    private void addUniqueSensor(String id, String type) {
        sensorTypes.putIfAbsent(id, type);
    }
    
    // Gestionează cererea pentru toți senzorii
    private void handleGetAllSensors(ACLMessage request) {
        // Asigură-te că datele inițiale au fost încărcate
        if (!isInitialDataFetched) {
            fetchInitialSensorData();
        }
        
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
        System.out.println("DBAgent a primit o cerere pentru date istorice: " + request.getContent());

        try {
            // Extrage senzorii și perioada de timp din mesaj
            JSONObject requestContent = new JSONObject(request.getContent());
            JSONArray requestedSensors = requestContent.getJSONArray("sensors");
            String range = requestContent.getString("range");

            // Preia toate datele din Firebase
            URL url = new URL(FIREBASE_URL + TABLE_NAME + ".json");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));
            StringBuilder sb = new StringBuilder();
            String output;
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }
            conn.disconnect();

            // Filtrează datele și trimite răspunsul
            JSONObject allData = new JSONObject(sb.toString());
            JSONObject responseData = parseAndFilterByTime(allData, requestedSensors, range);

            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.INFORM);
            reply.setConversationId("sensor-data-response");
            reply.setContent(responseData.toString());
            send(reply);

        } catch (Exception e) {
            e.printStackTrace();
            ACLMessage reply = request.createReply();
            reply.setPerformative(ACLMessage.FAILURE);
            reply.setContent("Eroare la procesarea cererii de date: " + e.getMessage());
            send(reply);
        }
    }

    private JSONObject parseAndFilterByTime(JSONObject allData, JSONArray requestedSensors, String range) {
        JSONObject filteredData = new JSONObject();
        long now = System.currentTimeMillis();
        long timeLimit = 0;

        switch (range) {
            case "Ultimele 15 minute":
                timeLimit = now - (15 * 60 * 1000);
                break;
            case "Ultima oră":
                timeLimit = now - (60 * 60 * 1000);
                break;
            case "Ultimele 24 de ore":
                timeLimit = now - (24 * 60 * 60 * 1000);
                break;
        }
        System.out.println("DBAgent >> Filtrare pentru perioada: " + range + ". Limita de timp (epoch): " + timeLimit);


        List<String> sensorList = new ArrayList<>();
        for (int i = 0; i < requestedSensors.length(); i++) {
            sensorList.add(requestedSensors.getString(i));
        }

        for (String key : allData.keySet()) {
            JSONObject record = allData.getJSONObject(key);
            String sensorId = record.getString("id");

            if (sensorList.contains(sensorId)) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String timestampStr = record.getString("timestamp");
                    Date recordDate = sdf.parse(timestampStr);
                    
                    boolean isAfter = recordDate.getTime() >= timeLimit;
                    System.out.println("DBAgent >> Procesare: " + sensorId + " la " + timestampStr + " (" + recordDate.getTime() + "). Inclus în rezultat: " + isAfter);

                    if (isAfter) {
                        JSONArray sensorData = filteredData.optJSONArray(sensorId);
                        if (sensorData == null) {
                            sensorData = new JSONArray();
                            filteredData.put(sensorId, sensorData);
                        }
                        JSONObject dataPoint = new JSONObject();
                        dataPoint.put("timestamp", record.getString("timestamp"));
                        dataPoint.put("value", record.get("val")); // Corectat: folosește "val" în loc de "value"
                        sensorData.put(dataPoint);
                    }
                } catch (Exception e) {
                     System.err.println("DBAgent >> Eroare la procesarea înregistrării: " + record + " | Eroare: " + e.getMessage());
                }
            }
        }
        // Sortează datele pentru fiecare senzor înainte de a le returna
        for (String sensorId : filteredData.keySet()) {
            JSONArray sensorData = filteredData.getJSONArray(sensorId);
            List<JSONObject> dataList = new ArrayList<>();
            for (int i = 0; i < sensorData.length(); i++) {
                dataList.add(sensorData.getJSONObject(i));
            }

            Collections.sort(dataList, new Comparator<JSONObject>() {
                private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                @Override
                public int compare(JSONObject o1, JSONObject o2) {
                    try {
                        Date d1 = sdf.parse(o1.getString("timestamp"));
                        Date d2 = sdf.parse(o2.getString("timestamp"));
                        return d1.compareTo(d2);
                    } catch (Exception e) {
                        return 0;
                    }
                }
            });

            // Reconstruiește JSONArray-ul sortat
            JSONArray sortedSensorData = new JSONArray(dataList);
            filteredData.put(sensorId, sortedSensorData);
        }

        System.out.println("DBAgent >> Date filtrate și sortate returnate: " + filteredData);
        return filteredData;
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