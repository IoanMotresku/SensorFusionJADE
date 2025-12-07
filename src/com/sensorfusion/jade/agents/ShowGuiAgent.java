package com.sensorfusion.jade.agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.WakerBehaviour;
import jade.lang.acl.ACLMessage;

/**
 * Un agent simplu, reutilizabil, al cărui singur rol este să trimită comanda
 * "show-gui" către un agent specificat în argumente, apoi să se autodistrugă.
 */
public class ShowGuiAgent extends Agent {

    @Override
    protected void setup() {
        Object[] args = getArguments();
        if (args != null && args.length > 0 && args[0] instanceof String) {
            String targetAgentName = (String) args[0];
            
            System.out.println(getLocalName() + ": Trimit comanda 'show-gui' către agentul '" + targetAgentName + "'...");
            
            // Creăm și trimitem mesajul
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.setContent("show-gui");
            msg.addReceiver(new AID(targetAgentName, AID.ISLOCALNAME));
            send(msg);
            
            // Adăugăm un comportament de așteptare pentru a asigura livrarea
            addBehaviour(new WakerBehaviour(this, 1000) {
                @Override
                protected void onWake() {
                    // După ce a trecut 1 secundă, agentul se poate șterge în siguranță.
                    myAgent.doDelete();
                }
            });
        } else {
            System.err.println("Agentul " + getLocalName() + " nu a primit numele agentului țintă în argumente. Se autodistruge.");
            doDelete(); // Se șterge dacă nu are ce face
        }
    }
}
