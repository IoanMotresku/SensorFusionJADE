package clase;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StatisticsAgent extends Agent {

    private StatisticsGui myGui;
    private AID dbAgentAID = null;

    @Override
    protected void setup() {
        System.out.println("Statistics Agent " + getLocalName() + " started.");
        
        SwingUtilities.invokeLater(() -> {
            myGui = new StatisticsGui(this);
            myGui.setVisible(true);
        });
        
        // Request the list of sensors from the database agent upon startup
        requestSensorList();

        // Behavior to handle incoming messages from the database agent
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

    private void processIncomingMessage(ACLMessage msg) {
        String convId = msg.getConversationId();
        String content = msg.getContent();

        if (convId != null) {
            switch (convId) {
                case "sensor-list-response":
                    List<String> sensors = parseSensorList(content);
                    if (myGui != null) {
                        myGui.setSensorList(sensors);
                    }
                    break;
                case "sensor-data-response":
                    Map<String, List<Point>> chartData = new ConcurrentHashMap<>();
                    List<Object[]> tableData = new ArrayList<>();
                    parseSensorDataResponse(content, chartData, tableData);
                    if (myGui != null) {
                        myGui.displayData(chartData, tableData);
                    }
                    break;
            }
        }
    }
    
    // Parse the JSON array of sensor IDs
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

    // Parse the JSON object containing historical data for multiple sensors
    private void parseSensorDataResponse(String jsonContent, Map<String, List<Point>> chartData, List<Object[]> tableData) {
        try {
            JSONObject jsonResponse = new JSONObject(jsonContent);
            for (String sensorId : jsonResponse.keySet()) {
                List<Point> points = new ArrayList<>();
                JSONArray sensorDataArray = jsonResponse.getJSONArray(sensorId);

                for (int i = 0; i < sensorDataArray.length(); i++) {
                    JSONObject dataPoint = sensorDataArray.getJSONObject(i);
                    String timestamp = dataPoint.getString("timestamp");
                    int value = dataPoint.getInt("value");
                    points.add(new Point(i, value)); // Using 'i' as a simple x-coordinate for now
                    tableData.add(new Object[]{sensorId, timestamp, value});
                }
                chartData.put(sensorId, points);
            }
        } catch (Exception e) {
            System.err.println("Error parsing sensor data response: " + e.getMessage());
        }
    }

    // Method to find the DBAgent using the Directory Facilitator
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
                System.out.println("DBAgent not found yet. Will try again.");
            }
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }
    }

    // Methods to be called by the GUI
    public void requestSensorList() {
        findDBAgent();
        if (dbAgentAID != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(dbAgentAID);
            msg.setConversationId("get-all-sensors");
            send(msg);
        }
    }
    
    public void requestData(List<String> selectedSensors, String timeRange) {
        findDBAgent();
        if (dbAgentAID != null) {
            ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
            msg.addReceiver(dbAgentAID);
            msg.setConversationId("get-sensor-data");
            
            JSONObject requestContent = new JSONObject();
            requestContent.put("sensors", new JSONArray(selectedSensors));
            requestContent.put("range", timeRange);
            
            msg.setContent(requestContent.toString());
            send(msg);
        }
    }
}