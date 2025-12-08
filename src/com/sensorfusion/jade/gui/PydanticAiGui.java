package com.sensorfusion.jade.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sensorfusion.jade.agents.PydanticAiAgent;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

public class PydanticAiGui extends JFrame {

    private PydanticAiAgent myAgent; // Reference to the backing agent

    // Components
    private JList<String> sensorList;
    private DefaultListModel<String> sensorListModel;
    private JButton refreshButton;
    private JButton btnStart;
    private JButton btnEnd;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton btnSend;

    private java.util.Date startDate;
    private java.util.Date endDate;

    public PydanticAiGui(PydanticAiAgent agent) {
        super("Pydantic AI Interface");
        this.myAgent = agent;
		
        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
        addListeners();
    }
	
    private void initComponents() {
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(250);

        // --- Left Panel (Sensors & Control) ---
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Sensor List Panel with Refresh Button
        JPanel sensorListPanel = new JPanel(new BorderLayout());
        
        sensorListModel = new DefaultListModel<>();
        sensorList = new JList<>(sensorListModel);
        JScrollPane listScrollPane = new JScrollPane(sensorList);
        
        refreshButton = new JButton("⟳"); // Refresh icon
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(new JLabel("Listă Senzori"), BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);
        
        sensorListPanel.add(headerPanel, BorderLayout.NORTH);
        sensorListPanel.add(listScrollPane, BorderLayout.CENTER);
        
        // Control Buttons (Start/End)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnStart = new JButton("Început");
        btnEnd = new JButton("Sfârșit");
        controlPanel.add(btnStart);
        controlPanel.add(btnEnd);

        leftPanel.add(sensorListPanel, BorderLayout.CENTER);
        leftPanel.add(controlPanel, BorderLayout.SOUTH);

        // --- Right Panel (Chat) ---
        JPanel rightPanel = new JPanel(new BorderLayout(10, 10));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Chat Area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScrollPane = new JScrollPane(chatArea);
        chatScrollPane.setBorder(new TitledBorder("Răspuns AI"));

        // Input Area
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputField = new JTextField();
        btnSend = new JButton("Trimite");
        
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(btnSend, BorderLayout.EAST);

        rightPanel.add(chatScrollPane, BorderLayout.CENTER);
        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        // --- Assembly ---
        mainSplitPane.setLeftComponent(leftPanel);
        mainSplitPane.setRightComponent(rightPanel);
        add(mainSplitPane);
    }

    private void addListeners() {
        // Basic enter key listener for input field
        inputField.addActionListener(e -> sendMessage());
        btnSend.addActionListener(e -> sendMessage());
        
        // Listeners for Start/End with Date Time Picker
        btnStart.addActionListener(e -> handleDateSelection("Selectează Ora de Început", "Început"));
        btnEnd.addActionListener(e -> handleDateSelection("Selectează Ora de Sfârșit", "Sfârșit"));
        
        refreshButton.addActionListener(e -> {
            if (myAgent != null) {
                myAgent.requestSensorList();
            }
        });
    }

    private void handleDateSelection(String title, String type) {
        String selectedSensor = sensorList.getSelectedValue();
        if (selectedSensor == null) {
            JOptionPane.showMessageDialog(this, 
                "Te rog selectează mai întâi un senzor din listă!", 
                "Niciun senzor selectat", 
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.Date date = showDateTimePicker(title);
        if (date != null) {
            if ("Început".equals(type)) {
                startDate = date;
            } else {
                endDate = date;
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
            String formattedDate = sdf.format(date);
            appendToChat("[System]: Pentru senzorul '" + selectedSensor + "', ai setat " + type + ": " + formattedDate);
        }
    }

    private java.util.Date showDateTimePicker(String title) {
        JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
        JSpinner.DateEditor dateEditor = new JSpinner.DateEditor(dateSpinner, "dd.MM.yyyy HH:mm");
        dateSpinner.setEditor(dateEditor);
        // Current time as default
        dateSpinner.setValue(new java.util.Date()); 

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.add(new JLabel("Alege data și ora:"), BorderLayout.NORTH);
        panel.add(dateSpinner, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(this, panel, title, 
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return (java.util.Date) dateSpinner.getValue();
        }
        return null;
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) {
            return; // Nu trimite mesaje goale
        }

        String selectedSensor = sensorList.getSelectedValue();

        // Validations
        if (selectedSensor == null) {
            JOptionPane.showMessageDialog(this, "Te rog selectează un senzor.", "Validare Eșuată", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (startDate == null) {
            JOptionPane.showMessageDialog(this, "Te rog setează data de început.", "Validare Eșuată", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (endDate == null) {
            JOptionPane.showMessageDialog(this, "Te rog setează data de sfârșit.", "Validare Eșuată", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (startDate.after(endDate)) {
            JOptionPane.showMessageDialog(this, "Data de început trebuie să fie înainte de data de sfârșit.", "Validare Eșuată", JOptionPane.WARNING_MESSAGE);
            return;
        }

        appendToChat("Tu: " + text);
        inputField.setText("");
        
        // Here you would normally send the message to the agent
        if (myAgent != null) {
            myAgent.requestSensorData(selectedSensor, startDate, endDate, text);
        } else {
            // Simulate response for testing
            SwingUtilities.invokeLater(() -> {
                try { Thread.sleep(500); } catch (Exception ignored) {}
                appendToChat("AI (Mock): Am primit cererea pentru '" + selectedSensor + "' cu textul: \"" + text + "\"");
            });
        }
    }

    public void appendToChat(String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    public void setSensorList(List<String> sensors) {
        SwingUtilities.invokeLater(() -> {
            sensorListModel.clear();
            sensors.forEach(sensorListModel::addElement);
        });
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF.");
        }

        try {
            Runtime rt = Runtime.instance();
            rt.setCloseVM(true);
            Profile p = new ProfileImpl(false); // Non-main container
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            AgentContainer ac = rt.createAgentContainer(p);

            String agentName = "pydantic-ai-" + System.currentTimeMillis();
            ac.createNewAgent(agentName, "com.sensorfusion.jade.agents.PydanticAiAgent", new Object[]{}).start();

        } catch (Exception e) {
            System.err.println("JADE platform not found, launching Pydantic AI GUI in offline/mock data mode.");
            // Fallback to offline mode
            SwingUtilities.invokeLater(() -> {
                PydanticAiGui gui = new PydanticAiGui(null);
                
                // Add some mock sensors
                gui.sensorListModel.addElement("Senzor_Temperatura_01 (Mock)");
                gui.sensorListModel.addElement("Senzor_Umiditate_02 (Mock)");
                gui.sensorListModel.addElement("Senzor_Presiune_03 (Mock)");

                gui.setVisible(true);
            });
        }
    }
}
