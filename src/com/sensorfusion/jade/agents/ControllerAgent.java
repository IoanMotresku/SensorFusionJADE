package com.sensorfusion.jade.agents;

import com.sensorfusion.jade.gui.ControllerGui;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.content.lang.sl.SLCodec;
import jade.domain.DFService;
import jade.domain.JADEAgentManagement.JADEManagementOntology;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerAgent extends Agent {

    // Clasa internă pentru a menține starea completă a unui senzor
    private static class SensorState {
        String id;
        String type;
        String value;
        String unit;
        String status;
        long lastUpdateTime;
        final int timeoutSeconds;

        SensorState(String id, String type, int timeoutSeconds) {
            this.id = id;
            this.type = type;
            this.timeoutSeconds = timeoutSeconds;
            this.status = "REGISTERED"; // Status inițial
            this.value = "N/A";
            this.unit = "N/A";
            this.lastUpdateTime = System.currentTimeMillis();
        }

        // Metodă pentru a actualiza starea la primirea de date noi
        void update(String value, String unit, String status) {
            this.value = value;
            this.unit = unit;
            this.status = status;
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    // Cache-ul cu starea curentă a senzorilor (Thread-safe)
    private final Map<String, SensorState> sensorCache = new ConcurrentHashMap<>();
    
    private transient ControllerGui myGui;
    private AID dbAgentAID = null;

    @Override
    protected void setup() {
        System.out.println("Controller Agent " + getLocalName() + " pornit.");
        
        // Register language and ontology for AMS communication
        getContentManager().registerLanguage(new SLCodec());
        getContentManager().registerOntology(JADEManagementOntology.getInstance());
        
        registerService();
        
        // Comportament pentru a căuta periodic agentul de DB
        addBehaviour(new TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                if (dbAgentAID == null) searchForDB();
            }
        });

        // Comportament pentru a verifica senzorii inactivi
        addBehaviour(new TickerBehaviour(this, 5000) { // Rulează la fiecare 5 secunde
            @Override
            protected void onTick() {
                final long now = System.currentTimeMillis();
                for (SensorState state : sensorCache.values()) {
                    // Verificăm doar senzorii care nu sunt deja marcați ca inactivi
                    if (!"INACTIVE".equals(state.status)) {
                        long elapsedTime = (now - state.lastUpdateTime) / 1000;
                        if (elapsedTime > state.timeoutSeconds) {
                            System.out.println("Senzorul " + state.id + " a devenit inactiv (timeout: " + state.timeoutSeconds + "s)");
                            state.status = "INACTIVE";
                            if (myGui != null) {
                                myGui.updateSensor(state.id, state.type, state.value, state.unit, state.status);
                            }
                        }
                    }
                }
            }
        });

        // Comportament Unificat pentru a primi TOATE mesajele
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    String content = msg.getContent();
                    String convId = msg.getConversationId();

                    // Prioritizăm comenzile speciale bazate pe conținut
                    if (content != null && "show-gui".equals(content)) {
                        System.out.println("Primit comandă de afișare GUI de la " + msg.getSender().getLocalName());
                        showGui();
                        return; // Am procesat mesajul
                    }

                    // Apoi procesăm mesajele de date bazate pe ConversationID
                    if (convId != null) {
                        switch (convId) {
                            case "sensor-registration":
                                handleRegistration(content, msg);
                                break;
                            case "sensor-data":
                                handleSensorData(content);
                                break;
                        }
                    }
                } else {
                    block();
                }
            }
        });
    }
    
    private void handleRegistration(String jsonContent, ACLMessage originalMsg) {
        try {
            String fullId = extractJsonValue(jsonContent, "id");
            String type = extractJsonValue(jsonContent, "type");
            int timeout = Integer.parseInt(extractJsonValue(jsonContent, "timeout"));
            
            String localId = (new AID(fullId, AID.ISGUID)).getLocalName();

            if (localId != null && type != null) {
                System.out.println("Înregistrare nouă de la " + localId + " (Tip: " + type + ", Timeout: " + timeout + "s)");
                SensorState newState = new SensorState(localId, type, timeout);
                sensorCache.put(localId, newState);

                if (myGui != null && myGui.isShowing()) {
                    myGui.updateSensor(localId, type, newState.value, newState.unit, newState.status);
                }

                // Create and send the confirmation reply
                ACLMessage reply = originalMsg.createReply();
                reply.setPerformative(ACLMessage.CONFIRM);
                reply.setContent("REGISTRATION_CONFIRMED");
                send(reply);
            }
        } catch (Exception e) {
            System.err.println("Eroare la procesarea mesajului de înregistrare: " + jsonContent);
            e.printStackTrace();
        }
    }
    
    private void handleSensorData(String jsonContent) {
        try {
            String fullId = extractJsonValue(jsonContent, "id");
            if (fullId == null) return;

            String localId = (new AID(fullId, AID.ISGUID)).getLocalName();
            SensorState state = sensorCache.get(localId);

            if (state != null) {
                String val = extractJsonValue(jsonContent, "val");
                String unit = extractJsonValue(jsonContent, "unit");
                String status = extractJsonValue(jsonContent, "status");

                state.update(val, unit, status);

                if (myGui != null && myGui.isShowing()) {
                    myGui.updateSensor(localId, state.type, val, unit, status);
                    if ("SENSOR_ERROR".equals(status) || "WARNING".equals(status)) {
                        myGui.logToConsole("ALERTA [" + localId + "]: " + status + " -> " + val + unit);
                    }
                }

                if (dbAgentAID != null) {
                    ACLMessage dbMsg = new ACLMessage(ACLMessage.REQUEST);
                    dbMsg.addReceiver(dbAgentAID);
                    dbMsg.setContent(jsonContent);
                    dbMsg.setConversationId("save-db");
                    send(dbMsg);
                }
            } else {
                 System.out.println("Am primit date de la un senzor neînregistrat: " + localId);
            }
        } catch (Exception e) {
            System.err.println("Eroare la procesarea datelor de la senzor: " + jsonContent);
            e.printStackTrace();
        }
    }

    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) {}
        if (myGui != null) {
            SwingUtilities.invokeLater(() -> myGui.dispose());
        }
        System.out.println("Controller [" + getLocalName() + "] oprit.");
    }

    public synchronized void guiClosed() {
        if (myGui != null) {
            System.out.println("GUI-ul controller-ului a fost închis. Agentul continuă să ruleze.");
            myGui = null;
        }
    }

    public synchronized void showGui() {
        SwingUtilities.invokeLater(() -> {
            if (myGui == null || !myGui.isDisplayable()) {
                myGui = new ControllerGui(this);
                myGui.logToConsole("GUI (re)inițializat. Se încarcă starea curentă a senzorilor...");
                for (SensorState sensorData : sensorCache.values()) {
                    myGui.updateSensor(
                        sensorData.id, sensorData.type, 
                        sensorData.value, sensorData.unit, 
                        sensorData.status
                    );
                }
            }
            myGui.setVisible(true);
            myGui.toFront();
        });
    }

    public void shutdownSystem() {
        if (myGui != null) {
            myGui.logToConsole("SHUTDOWN command received. Shutting down platform...");
        }
        System.out.println(getLocalName() + " is initiating platform shutdown...");

        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                try {
                    // Create a request to the AMS to shut down the platform
                    ACLMessage shutdownMsg = new ACLMessage(ACLMessage.REQUEST);
                    shutdownMsg.addReceiver(getAMS());
                    
                    // Set the language and ontology for the content manager
                    shutdownMsg.setLanguage(new SLCodec().getName());
                    shutdownMsg.setOntology(JADEManagementOntology.getInstance().getName());
                    
                    // Use the JADE ontology to create the shutdown action
                    jade.content.onto.basic.Action shutdownAction = new jade.content.onto.basic.Action(getAMS(), new jade.domain.JADEAgentManagement.ShutdownPlatform());
                    
                    // Fill the message content with the shutdown action
                    myAgent.getContentManager().fillContent(shutdownMsg, shutdownAction);
                    
                    send(shutdownMsg);
                } catch (Exception e) {
                    System.err.println("Error while trying to shut down the platform: " + e);
                    e.printStackTrace();
                }
            }
        });
    }

    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        dfd.addServices(new ServiceDescription(){{setType("controller-service"); setName("Central-Controller");}});
        try {
            DFService.register(this, dfd);
            System.out.println("Controller înregistrat în DF.");
        } catch (FIPAException fe) { fe.printStackTrace(); }
    }
    
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIdx = json.indexOf(searchKey);
        if (startIdx == -1) return null;
        startIdx += searchKey.length();
        boolean isString = (json.charAt(startIdx) == '\"');
        if (isString) startIdx++;
        int endIdx;
        if (isString) {
            endIdx = json.indexOf("\"", startIdx);
        } else {
            int commaIdx = json.indexOf(",", startIdx);
            int braceIdx = json.indexOf("}", startIdx);
            endIdx = (commaIdx == -1) ? braceIdx : Math.min(commaIdx, braceIdx);
        }
        if (endIdx == -1) return null;
        return json.substring(startIdx, endIdx).trim();
    }
    
    private void searchForDB() {
        DFAgentDescription template = new DFAgentDescription();
        template.addServices(new ServiceDescription(){{setType("database-service");}});
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                dbAgentAID = result[0].getName();
                if (myGui != null && myGui.isShowing()) myGui.logToConsole("Conectat la baza de date: " + dbAgentAID.getLocalName());
            }
        } catch (FIPAException fe) { fe.printStackTrace(); }
    }
}