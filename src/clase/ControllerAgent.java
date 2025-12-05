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

import javax.swing.*;

public class ControllerAgent extends Agent {

    private ControllerGui myGui;
    private AID dbAgentAID = null;

    @Override
    protected void setup() 
    {
        // 1. Setup Look & Feel
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}

        // 2. Inițializare GUI Sincronă (ca să fim siguri că există înainte de a continua)
        try {
            SwingUtilities.invokeAndWait(() -> {
                myGui = new ControllerGui(this);
                myGui.setVisible(true);
                myGui.logToConsole("Sistem pornit. Se inițializează serviciile JADE...");
            });
        } catch (Exception e) {
            e.printStackTrace(); 
            // Dacă eșuează GUI, continuăm fără el sau oprim agentul
        }

        // 3. Înregistrare în DF
        registerService(); // Acum myGui sigur nu e null
        
     // Adaugă asta în setup(), după registerService()
        addBehaviour(new jade.core.behaviours.TickerBehaviour(this, 10000) {
            @Override
            protected void onTick() {
                if (dbAgentAID == null) {
                    searchForDB();
                }
            }
        });

        // 4. Comportament de Ascultare
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    if (msg.getPerformative() == ACLMessage.INFORM || 
                        msg.getPerformative() == ACLMessage.FAILURE) {
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
        // Ștergere din Pagini Aurii
        try { DFService.deregister(this); } catch (FIPAException e) {}
        
        if (myGui != null) {
            myGui.dispose();
        }
        System.out.println("Controller [" + getLocalName() + "] oprit.");
    }

    // --- LOGICA DE PROCESARE MESAJE ---
    private void processSensorMessage(ACLMessage msg) {
        String content = msg.getContent();
        
        try {
            // Exemplu JSON primit: 
            // {"id":"SENS-1", "type":"Temp", "val":25, "unit":"C", "status":"NORMAL"}
            
            // Parsare Manuală (String Manipulation) ca să nu importăm librării JSON externe
            String id = extractJsonValue(content, "id");
            String type = extractJsonValue(content, "type");
            String val = extractJsonValue(content, "val");
            String unit = extractJsonValue(content, "unit");
            String status = extractJsonValue(content, "status");

            // Validare rapidă
            if (id != null && val != null) {
                // Actualizare GUI
                myGui.updateSensor(id, type, val, unit, status);
                
                // Logging special pentru erori
                if ("SENSOR_ERROR".equals(status) || "WARNING".equals(status)) {
                    myGui.logToConsole("ALERTA [" + id + "]: " + status + " -> " + val + unit);
                }
                
             // --- COD NOU: Trimitere spre DB ---
                // Dacă am găsit agentul de bază de date, îi pasăm JSON-ul original
                if (dbAgentAID != null) {
                    ACLMessage dbMsg = new ACLMessage(ACLMessage.REQUEST);
                    dbMsg.addReceiver(dbAgentAID);
                    dbMsg.setContent(content); // Trimitem exact ce am primit de la senzor
                    dbMsg.setConversationId("save-db");
                    send(dbMsg);
                }
                
            }
            
        } catch (Exception e) {
            myGui.logToConsole("Eroare la procesarea mesajului de la " + msg.getSender().getLocalName());
            e.printStackTrace();
        }
    }

    // --- FUNCȚIONALITATE: SHUTDOWN GLOBAL ---
    // Apelată când apeși butonul roșu din GUI
    public void shutdownSystem() {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                myGui.logToConsole("Se caută senzorii pentru deconectare...");
                
                // 1. Căutăm toți senzorii în DF
                DFAgentDescription template = new DFAgentDescription();
                ServiceDescription sd = new ServiceDescription();
                sd.setType("sensor-service");
                template.addServices(sd);

                try {
                    DFAgentDescription[] result = DFService.search(myAgent, template);
                    ACLMessage shutdownMsg = new ACLMessage(ACLMessage.REQUEST);
                    shutdownMsg.setContent("SHUTDOWN"); // Comandă specială

                    for (DFAgentDescription agent : result) {
                        shutdownMsg.addReceiver(agent.getName());
                    }

                    if (result.length > 0) {
                        send(shutdownMsg);
                        myGui.logToConsole("Comandă oprire trimisă la " + result.length + " senzori.");
                    } else {
                        myGui.logToConsole("Nu s-au găsit senzori activi.");
                    }
                    
                } catch (FIPAException e) {
                    e.printStackTrace();
                }

                // 2. Închidem Controller-ul după scurt timp
                myGui.logToConsole("Sistemul se închide în 2 secunde...");
                new Thread(() -> {
                    try { Thread.sleep(2000); } catch (InterruptedException e) {}
                    doDelete();
                    System.exit(0); // Omoară tot procesul Java
                }).start();
            }
        });
    }

    // --- HELPER: Înregistrare DF ---
    private void registerService() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("controller-service"); // CHEIA MAGICĂ: Senzorii caută acest tip
        sd.setName("Central-Controller");
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
            myGui.logToConsole("Controller înregistrat în DF.");
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    // --- HELPER: Parsare JSON Simplă ---
    // Caută "key":"value" sau "key":value
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int startIdx = json.indexOf(searchKey);
        
        if (startIdx == -1) return "???";
        
        startIdx += searchKey.length();
        
        // Verificăm dacă valoarea e string (începe cu ghilimele)
        boolean isString = (json.charAt(startIdx) == '\"');
        if (isString) startIdx++; // Sărim peste prima ghilimeauă
        
        int endIdx;
        if (isString) {
            endIdx = json.indexOf("\"", startIdx);
        } else {
            // Dacă e număr, căutăm virgula sau acolada de final
            int commaIdx = json.indexOf(",", startIdx);
            int braceIdx = json.indexOf("}", startIdx);
            
            if (commaIdx == -1) endIdx = braceIdx;
            else if (braceIdx == -1) endIdx = commaIdx;
            else endIdx = Math.min(commaIdx, braceIdx);
        }
        
        if (endIdx == -1) return "???";
        
        return json.substring(startIdx, endIdx).trim();
    }
    
    private void searchForDB() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("database-service"); // Căutăm tipul înregistrat de DBAgent
        template.addServices(sd);

        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                dbAgentAID = result[0].getName();
                if (myGui != null) myGui.logToConsole("Conectat la baza de date: " + dbAgentAID.getLocalName());
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }
    
}