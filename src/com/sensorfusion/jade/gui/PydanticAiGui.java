package com.sensorfusion.jade.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import jade.core.Agent;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;

public class PydanticAiGui extends JFrame {

    private Agent myAgent; // Reference to the backing agent (e.g., PydanticAgent)

    // Components
    private JList<String> sensorList;
    private DefaultListModel<String> sensorListModel;
    private JButton btnStart;
    private JButton btnEnd;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton btnSend;

    public PydanticAiGui(Agent agent) {
        super("Pydantic AI Interface");
        this.myAgent = agent;

        setSize(900, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
    }

    private void initComponents() {
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(250);

        // --- Left Panel (Sensors & Control) ---
        JPanel leftPanel = new JPanel(new BorderLayout(10, 10));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Sensor List
        sensorListModel = new DefaultListModel<>();
        sensorList = new JList<>(sensorListModel);
        JScrollPane listScrollPane = new JScrollPane(sensorList);
        listScrollPane.setBorder(new TitledBorder("Listă Senzori"));

        // Control Buttons (Start/End)
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        btnStart = new JButton("Început");
        btnEnd = new JButton("Sfârșit");
        controlPanel.add(btnStart);
        controlPanel.add(btnEnd);

        leftPanel.add(listScrollPane, BorderLayout.CENTER);
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

        // --- Listeners ---
        // Basic enter key listener for input field
        inputField.addActionListener(e -> sendMessage());
        btnSend.addActionListener(e -> sendMessage());
        
        // Listeners for Start/End with Date Time Picker
        btnStart.addActionListener(e -> handleDateSelection("Selectează Ora de Început", "Început"));
        btnEnd.addActionListener(e -> handleDateSelection("Selectează Ora de Sfârșit", "Sfârșit"));
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
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm");
            String formattedDate = sdf.format(date);
            appendToChat("[System]: Pentru senzorul '" + selectedSensor + "', ai setat " + type + ": " + formattedDate);
            // Here you could store the date in a variable to send to the agent later
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
        if (!text.isEmpty()) {
            appendToChat("Tu: " + text);
            inputField.setText("");
            
            // Here you would normally send the message to the agent
            if (myAgent != null) {
                // Example: ((PydanticAgent)myAgent).sendRequest(text);
            } else {
                // Simulate response for testing
                SwingUtilities.invokeLater(() -> {
                    try { Thread.sleep(500); } catch (Exception ignored) {}
                    appendToChat("AI (Mock): Am primit mesajul tău: \"" + text + "\"");
                });
            }
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

        SwingUtilities.invokeLater(() -> {
            PydanticAiGui gui = new PydanticAiGui(null);
            
            // Add some mock sensors
            gui.sensorListModel.addElement("Senzor_Temperatura_01");
            gui.sensorListModel.addElement("Senzor_Umiditate_02");
            gui.sensorListModel.addElement("Senzor_Presiune_03");

            gui.setVisible(true);
        });
    }
}
