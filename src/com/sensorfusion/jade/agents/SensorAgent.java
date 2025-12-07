package com.sensorfusion.jade.agents;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sensorfusion.jade.gui.SensorGui;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import javax.swing.*;

public class SensorAgent extends Agent {

    // Variabilele nu mai sunt final/hardcodate
    private String SENSOR_ID;
    private String SENSOR_TYPE;
    private String SENSOR_UNIT;
    
    private int SLIDER_MIN;
    private int SLIDER_MAX;
    private int HW_MIN;
    private int HW_MAX;
    private int SAFE_MIN;
    private int SAFE_MAX;
    private int activityTimeoutSeconds;

    private SensorGui myGui;
    private AID controllerAID = null;
    private boolean registered = false; // Flag to track registration status

    @Override
    protected void setup() {
        // --- 1. PRELUARE ARGUMENTE (De la Launcher) ---
        Object[] args = getArguments();
        
        if (args != null && args.length >= 10) {
            // Ordinea contează: ID, Type, Unit, SliderMin, SliderMax, HwMin, HwMax, SafeMin, SafeMax, Timeout
            try {
                SENSOR_ID = (String) args[0];
                SENSOR_TYPE = (String) args[1];
                SENSOR_UNIT = (String) args[2];
                SLIDER_MIN = Integer.parseInt(args[3].toString());
                SLIDER_MAX = Integer.parseInt(args[4].toString());
                HW_MIN = Integer.parseInt(args[5].toString());
                HW_MAX = Integer.parseInt(args[6].toString());
                SAFE_MIN = Integer.parseInt(args[7].toString());
                SAFE_MAX = Integer.parseInt(args[8].toString());
                activityTimeoutSeconds = Integer.parseInt(args[9].toString());
            } catch (Exception e) {
                System.err.println("Eroare la parsarea argumentelor pentru " + getLocalName());
                e.printStackTrace();
                doDelete(); // Oprim agentul dacă argumentele sunt greșite
                return;
            }
        } else {
            // Valori default de siguranță (fallback)
            System.out.println("⚠️ " + getLocalName() + ": Nu s-au primit argumente. Se folosesc valori default.");
            SENSOR_ID = "DEFAULT-" + getLocalName();
            SENSOR_TYPE = "Generic";
            SENSOR_UNIT = "-";
            SLIDER_MIN = 0; SLIDER_MAX = 100;
            HW_MIN = 0; HW_MAX = 100;
            SAFE_MIN = 40; SAFE_MAX = 60;
            activityTimeoutSeconds = 60; // Default timeout
        }

        // --- 2. RESTUL LOGICII (Identic cu înainte) ---
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}

        SwingUtilities.invokeLater(() -> {
            myGui = new SensorGui(this);
            myGui.setVisible(true);
            myGui.updateConfiguration(
                SENSOR_ID, SENSOR_TYPE, SENSOR_UNIT,
                SLIDER_MIN, SLIDER_MAX,
                HW_MIN, HW_MAX,
                SAFE_MIN, SAFE_MAX
            );
        });

        registerInDF();

        addBehaviour(new TickerBehaviour(this, 5000) {
            @Override
            protected void onTick() {
                if (controllerAID == null) searchForController();
            }
        });
        
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() { searchForController(); }
        });
        
        // Behavior to listen for registration confirmation
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                if (controllerAID == null) {
                    block(1000); // Wait a bit before checking again
                    return;
                }
                
                MessageTemplate mt = MessageTemplate.and(
                    MessageTemplate.MatchPerformative(ACLMessage.CONFIRM),
                    MessageTemplate.MatchSender(controllerAID)
                );
                
                ACLMessage msg = myAgent.receive(mt);
                if (msg != null) {
                    if ("REGISTRATION_CONFIRMED".equals(msg.getContent())) {
                        System.out.println(getLocalName() + ": Înregistrare confirmată de către " + controllerAID.getLocalName());
                        registered = true;
                    }
                } else {
                    block();
                }
            }
        });
    }

    // ... (Restul metodelor takeDown, processSensorData, registerInDF, searchForController rămân identice) ...
    
    @Override
    protected void takeDown() {
        try { DFService.deregister(this); } catch (FIPAException e) {}
        
        // Asigurăm că închiderea GUI-ului se face pe Event Dispatch Thread (EDT)
        // pentru a evita excepții de tip InterruptedException
        if (myGui != null) {
            SwingUtilities.invokeLater(() -> myGui.dispose());
        }
    }

    /**
     * Metodă publică ce poate fi apelată (de ex. de la GUI)
     * pentru a cere agentului să se închidă în mod sigur.
     */
    public void requestToDoDelete() {
        addBehaviour(new OneShotBehaviour(this) {
            @Override
            public void action() {
                myAgent.doDelete();
            }
        });
    }

    public void processSensorData(int val) {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                String status = "NORMAL";
                int performative = ACLMessage.INFORM;

                if (val < HW_MIN || val > HW_MAX) {
                    status = "SENSOR_ERROR";
                    performative = ACLMessage.FAILURE;
                } else if (val < SAFE_MIN || val > SAFE_MAX) {
                    status = "WARNING";
                    performative = ACLMessage.INFORM;
                }

                String contentJson = String.format(
                    "{\"id\":\"%s\", \"type\":\"%s\", \"val\":%d, \"unit\":\"%s\", \"status\":\"%s\"}",
                    getLocalName(), SENSOR_TYPE, val, SENSOR_UNIT, status
                );

                if (controllerAID != null && registered) {
                    ACLMessage msg = new ACLMessage(performative);
                    msg.addReceiver(controllerAID);
                    msg.setContent(contentJson);
                    msg.setConversationId("sensor-data");
                    send(msg);
                } else if (controllerAID == null) {
                    searchForController();
                }
            }
        });
    }

    private void registerInDF() {
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("sensor-service");
        sd.setName(getLocalName());
        dfd.addServices(sd);
        try { DFService.register(this, dfd); } catch (FIPAException fe) { fe.printStackTrace(); }
    }

    private void searchForController() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("controller-service");
        template.addServices(sd);
        try {
            DFAgentDescription[] result = DFService.search(this, template);
            if (result.length > 0) {
                AID newControllerAID = result[0].getName();
                // Daca abia am gasit controller-ul, ne inregistram la el
                if (controllerAID == null) {
                    controllerAID = newControllerAID;
                    sendRegistration();
                }
            }
        } catch (FIPAException fe) { fe.printStackTrace(); }
    }

    private void sendRegistration() {
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                if (controllerAID != null) {
                    System.out.println(getLocalName() + ": Trimit mesaj de înregistrare către " + controllerAID.getLocalName());
                    
                    ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                    msg.addReceiver(controllerAID);
                    msg.setConversationId("sensor-registration");
                    
                    String contentJson = String.format(
                        "{\"id\":\"%s\", \"type\":\"%s\", \"timeout\":%d}",
                        getLocalName(), SENSOR_TYPE, activityTimeoutSeconds
                    );
                    msg.setContent(contentJson);
                    send(msg);
                }
            }
        });
    }
}