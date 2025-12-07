package com.sensorfusion.jade.gui;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jade.wrapper.StaleProxyException;

public class LauncherGui extends JFrame {

    private final Map<String, JSpinner> sensorSpinners = new LinkedHashMap<>();
    private final Map<String, Integer> launchedAgentCounts = new ConcurrentHashMap<>();
    private JSONArray sensorConfigs;

    private JButton launchButton;
    private JTextArea logArea;
    private JPanel sensorsPanel;

    private jade.wrapper.AgentContainer peripheralContainer;

    public LauncherGui() {
        super("Lansator Platformă JADE");

        setSize(600, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
        loadSensorConfiguration();
        initializeJadeContainer();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel titleLabel = new JLabel("Configurare Lansare Agenți", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        sensorsPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(sensorsPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Selecție Senzori"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        JPanel southPanel = new JPanel(new BorderLayout(10, 10));

        logArea = new JTextArea(8, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Jurnal Lansare"));

        launchButton = new JButton("LANSARE AGENȚI");
        launchButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        launchButton.setBackground(new Color(0, 150, 0));
        launchButton.setForeground(Color.WHITE);
        launchButton.addActionListener(e -> launchAgents());

        southPanel.add(logScrollPane, BorderLayout.CENTER);
        southPanel.add(launchButton, BorderLayout.SOUTH);
        mainPanel.add(southPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }
    
    private void initializeJadeContainer() {
        try {
            jade.core.Runtime rt = jade.core.Runtime.instance();
            jade.core.Profile p = new jade.core.ProfileImpl();
            p.setParameter(jade.core.Profile.MAIN_HOST, "localhost");
            p.setParameter(jade.core.Profile.MAIN_PORT, "1099");
            p.setParameter(jade.core.Profile.CONTAINER_NAME, "SenzoriContainer-" + System.currentTimeMillis());
            peripheralContainer = rt.createAgentContainer(p);
            logToConsole("Container JADE 'Senzori' pregătit.");
        } catch (Exception e) {
            logToConsole("EROARE CRITICĂ: Nu s-a putut crea container-ul JADE.");
            e.printStackTrace();
            launchButton.setEnabled(false);
            launchButton.setText("Eroare JADE");
        }
    }

    private void loadSensorConfiguration() {
        try {
            String content = new String(Files.readAllBytes(Paths.get("config/sensors.json")));
            sensorConfigs = new JSONArray(content);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(5, 5, 5, 5);
            gbc.fill = GridBagConstraints.HORIZONTAL;

            for (int i = 0; i < sensorConfigs.length(); i++) {
                JSONObject config = sensorConfigs.getJSONObject(i);
                String type = config.getString("type");
                launchedAgentCounts.put(type, 0);

                gbc.gridx = 0; gbc.gridy = i; gbc.weightx = 0.8;
                sensorsPanel.add(new JLabel(type), gbc);

                gbc.gridx = 1; gbc.gridy = i; gbc.weightx = 0.2;
                JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, 100, 1));
                sensorsPanel.add(spinner, gbc);
                sensorSpinners.put(type, spinner);
            }
        } catch (IOException e) {
            logToConsole("EROARE: Nu am putut citi fișierul config/sensors.json");
            e.printStackTrace();
        }
    }

    private void launchAgents() {
        launchButton.setEnabled(false);
        launchButton.setText("Lansare în curs...");
        logToConsole("Inițiere proces de lansare...");

        new Thread(() -> {
            try {
                int totalLaunched = 0;
                for (Map.Entry<String, JSpinner> entry : sensorSpinners.entrySet()) {
                    String sensorType = entry.getKey();
                    int count = (Integer) entry.getValue().getValue();

                    if (count > 0) {
                        totalLaunched += count;
                        logToConsole("Se pregătesc " + count + " senzori de tip '" + sensorType + "'...");
                        JSONObject config = findConfigForType(sensorType);
                        if (config == null) {
                            logToConsole("EROARE: Nu s-a găsit configurația pentru " + sensorType);
                            continue;
                        }

                        int currentCount = launchedAgentCounts.get(sensorType);

                        for (int j = 1; j <= count; j++) {
                            int agentIndex = currentCount + j;
                            String agentName = config.getString("baseName") + agentIndex;
                            String sensorId = "SENS-" + config.getString("type").substring(0, 4).toUpperCase() + "-" + agentIndex;

                            Object[] agentArgs = new Object[]{
                                sensorId, config.getString("type"), config.getString("unit"),
                                config.getInt("sliderMin"), config.getInt("sliderMax"),
                                config.getInt("hwMin"), config.getInt("hwMax"),
                                config.getInt("safeMin"), config.getInt("safeMax"),
                                config.getInt("activityTimeoutSeconds")
                            };

                            peripheralContainer.createNewAgent(agentName, "com.sensorfusion.jade.agents.SensorAgent", agentArgs).start();
                            logToConsole(" > Agent '" + agentName + "' lansat.");
                            Thread.sleep(100);
                        }
                        launchedAgentCounts.put(sensorType, currentCount + count);
                    }
                }

                if (totalLaunched > 0) {
                    logToConsole("===================================");
                    logToConsole("Lansare finalizată cu succes!");
                    logToConsole("===================================");
                } else {
                    logToConsole("Nu a fost selectat niciun agent pentru lansare.");
                }

                SwingUtilities.invokeLater(() -> {
                    // Resetăm spinner-ele la 0
                    for (JSpinner spinner : sensorSpinners.values()) {
                        spinner.setValue(0);
                    }
                    launchButton.setText("LANSARE AGENȚI");
                    launchButton.setEnabled(true);
                });

            } catch (StaleProxyException spe) {
                logToConsole("EROARE: Conexiunea la platforma JADE a fost pierdută.");
                logToConsole("Platforma a fost oprită. Reporniți sistemul.");
                SwingUtilities.invokeLater(() -> {
                    launchButton.setText("Platforma Oprită");
                    launchButton.setBackground(new Color(80, 80, 80));
                    launchButton.setEnabled(false); // Dezactivare permanentă
                 });
            } catch (Exception e) {
                logToConsole("EROARE CRITICĂ LA LANSARE: " + e.getMessage());
                e.printStackTrace();
                 SwingUtilities.invokeLater(() -> {
                    launchButton.setText("Eroare la lansare");
                    launchButton.setBackground(new Color(200, 50, 50));
                    launchButton.setEnabled(true); // Permitem o nouă încercare
                 });
            }
        }).start();
    }

    private JSONObject findConfigForType(String type) {
        for (int i = 0; i < sensorConfigs.length(); i++) {
            JSONObject config = sensorConfigs.getJSONObject(i);
            if (config.getString("type").equals(type)) {
                return config;
            }
        }
        return null;
    }

    public void logToConsole(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + msg + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LauncherGui().setVisible(true));
    }
}
