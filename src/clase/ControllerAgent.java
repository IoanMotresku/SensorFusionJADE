package clase;

import com.formdev.flatlaf.FlatDarkLaf;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ControllerAgent extends Agent {

    // Cache-ul cu starea curentă a senzorilor (Thread-safe)
    private final Map<String, Object[]> sensorCache = new ConcurrentHashMap<>();
    
    private transient ControllerGui myGui; // transient e bună practică pentru GUI-uri
    private AID dbAgentAID = null;

    @Override
    protected void setup() {
        System.out.println("Controller Agent " + getLocalName() + " pornit.");
        
        // Nu mai creăm GUI-ul aici.
        
        registerService();
        
        // Comportament pentru a căuta periodic agentul de DB
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                if (dbAgentAID == null) {
                    searchForDB();
                }
            }
        });

        // Comportament Unificat pentru a primi TOATE mesajele
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = myAgent.receive();
                if (msg != null) {
                    // Verificăm dacă este o comandă pentru GUI
                    if (msg.getContent() != null && msg.getContent().equals("show-gui")) {
                        System.out.println("Primit comandă de afișare GUI de la " + msg.getSender().getLocalName());
                        showGui();
                    } 
                    // Altfel, presupunem că este un mesaj de la un senzor
                    else {
                        processSensorMessage(msg);
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
        
        // Asigurăm că închiderea GUI-ului se face pe Event Dispatch Thread (EDT)
        if (myGui != null) {
            SwingUtilities.invokeLater(() -> myGui.dispose());
        }
        
        System.out.println("Controller [" + getLocalName() + "] oprit.");
    }

    private void processSensorMessage(ACLMessage msg) {
        String content = msg.getContent();
        
        try {
            String id = extractJsonValue(content, "id");
            String type = extractJsonValue(content, "type");
            String val = extractJsonValue(content, "val");
            String unit = extractJsonValue(content, "unit");
            String status = extractJsonValue(content, "status");

            if (id != null && val != null) {
                // 1. Salvăm starea curentă în cache
                sensorCache.put(id, new Object[]{id, type, val, unit, status});

                // 2. Actualizăm GUI-ul doar dacă este deschis
                if (myGui != null && myGui.isShowing()) {
                    myGui.updateSensor(id, type, val, unit, status);
                    if ("SENSOR_ERROR".equals(status) || "WARNING".equals(status)) {
                        myGui.logToConsole("ALERTA [" + id + "]: " + status + " -> " + val + unit);
                    }
                }
                
                // 3. Trimitem datele către agentul de DB
                if (dbAgentAID != null) {
                    ACLMessage dbMsg = new ACLMessage(ACLMessage.REQUEST);
                    dbMsg.addReceiver(dbAgentAID);
                    dbMsg.setContent(content);
                    dbMsg.setConversationId("save-db");
                    send(dbMsg);
                }
            }
        } catch (Exception e) {
            System.err.println("Eroare la procesarea mesajului de la " + msg.getSender().getLocalName());
            e.printStackTrace();
        }
    }

    public synchronized void guiClosed() {
        if (myGui != null) {
            System.out.println("GUI-ul controller-ului a fost închis. Agentul continuă să ruleze.");
            myGui = null;
        }
    }

    public synchronized void showGui() {
        // Folosim SwingUtilities.invokeLater pentru a manipula GUI-ul în siguranță
        SwingUtilities.invokeLater(() -> {
            if (myGui == null || !myGui.isDisplayable()) {
                System.out.println("Creare instanță nouă de ControllerGui.");
                
                myGui = new ControllerGui(this);
                
                // Populăm GUI-ul cu datele din cache
                myGui.logToConsole("GUI (re)inițializat. Se încarcă starea curentă a senzorilor...");
                for (Object[] sensorData : sensorCache.values()) {
                    myGui.updateSensor(
                        (String) sensorData[0], (String) sensorData[1], 
                        (String) sensorData[2], (String) sensorData[3], 
                        (String) sensorData[4]
                    );
                }
            }
            // Aducem fereastra în față și o facem vizibilă
            myGui.setVisible(true);
            myGui.toFront();
        });
    }

    public void shutdownSystem() {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if(myGui != null) myGui.logToConsole("Se caută senzorii pentru deconectare...");
                
                DFAgentDescription template = new DFAgentDescription();
                template.addServices(new ServiceDescription(){{setType("sensor-service");}});

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    if (result.length > 0) {
                        ACLMessage shutdownMsg = new ACLMessage(ACLMessage.REQUEST);
                        shutdownMsg.setContent("SHUTDOWN");
                        for (DFAgentDescription agent : result) {
                            shutdownMsg.addReceiver(agent.getName());
                        }
                        send(shutdownMsg);
                        if(myGui != null) myGui.logToConsole("Comandă oprire trimisă la " + result.length + " senzori.");
                    }
                    
                } catch (FIPAException e) { e.printStackTrace(); }

                if(myGui != null) myGui.logToConsole("Sistemul se închide în 2 secunde...");
                
                // Oprim agentul curent, dar NU și procesul Java
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    myAgent.doDelete();
                }).start();
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