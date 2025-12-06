package clase;

import com.formdev.flatlaf.FlatDarkLaf;
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

public class LauncherGui extends JFrame {

    private final Map<String, JSpinner> sensorSpinners = new LinkedHashMap<>();
    private JSONArray sensorConfigs;
    
    private JButton launchButton;
    private JTextArea logArea;
    private JPanel sensorsPanel;

    public LauncherGui() {
        super("Lansator Platformă JADE");

        setSize(600, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        initComponents();
        loadSensorConfiguration();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panoul de sus cu titlu
        JLabel titleLabel = new JLabel("Configurare Lansare Agenți", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // Panoul central pentru selecția senzorilor
        sensorsPanel = new JPanel(new GridBagLayout());
        JScrollPane scrollPane = new JScrollPane(sensorsPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Selecție Senzori"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Panoul de jos cu buton și log
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
                
                // Label pentru tipul senzorului
                gbc.gridx = 0;
                gbc.gridy = i;
                gbc.weightx = 0.8;
                sensorsPanel.add(new JLabel(type), gbc);
                
                // Spinner pentru cantitate
                gbc.gridx = 1;
                gbc.gridy = i;
                gbc.weightx = 0.2;
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
    
    
    
            // Dezactivăm spinner-ele după lansare
    
            for (JSpinner spinner : sensorSpinners.values()) {
    
                spinner.setEnabled(false);
    
            }
    
    
    
            new Thread(() -> {
    
                try {
    
                    // 1. Obținem instanța runtime JADE
    
                    jade.core.Runtime rt = jade.core.Runtime.instance();
    
                    
    
                    // 2. Creăm un profil pentru un container PERIFERIC
    
                    logToConsole("Conectare la containerul principal JADE...");
    
                    jade.core.Profile p = new jade.core.ProfileImpl();
    
                    p.setParameter(jade.core.Profile.MAIN_HOST, "localhost");
    
                    p.setParameter(jade.core.Profile.MAIN_PORT, "1099");
    
                    
    
                    // 3. Creăm containerul periferic. Agenții vor trăi aici.
    
                    jade.wrapper.AgentContainer peripheralContainer = rt.createAgentContainer(p);
    
                    logToConsole("Conectare reușită. Se lansează senzorii...");
    
                    
    
                    Thread.sleep(1000); // Pauză mică
    
    
    
                    // 4. Lansare agenți senzori pe baza selecției din GUI
    
                    for (Map.Entry<String, JSpinner> entry : sensorSpinners.entrySet()) {
    
                        String sensorType = entry.getKey();
    
                        int count = (Integer) entry.getValue().getValue();
    
                        
    
                        if (count > 0) {
    
                            logToConsole("Se pregătesc " + count + " senzori de tip '" + sensorType + "'...");
    
                            JSONObject config = findConfigForType(sensorType);
    
                            if (config == null) {
    
                                logToConsole("EROARE: Nu s-a găsit configurația pentru " + sensorType);
    
                                continue;
    
                            }
    
    
    
                            for (int j = 1; j <= count; j++) {
    
                                String agentName = config.getString("baseName") + j;
    
                                String sensorId = "SENS-" + config.getString("type").substring(0, 4).toUpperCase() + "-" + j;
    
    
    
                                Object[] agentArgs = new Object[]{
    
                                    sensorId, config.getString("type"), config.getString("unit"),
    
                                    config.getInt("sliderMin"), config.getInt("sliderMax"),
    
                                    config.getInt("hwMin"), config.getInt("hwMax"),
    
                                    config.getInt("safeMin"), config.getInt("safeMax")
    
                                };
    
                                
    
                                // Creăm agentul în containerul PERIFERIC
    
                                peripheralContainer.createNewAgent(agentName, "clase.SensorAgent", agentArgs).start();
    
                                logToConsole(" > Agent '" + agentName + "' lansat.");
    
                                Thread.sleep(100); // Pauză mică între lansări
    
                            }
    
                        }
    
                    }
    
                    
    
                    logToConsole("===================================");
    
                    logToConsole("Lansare finalizată cu succes!");
    
                    logToConsole("===================================");
    
                    SwingUtilities.invokeLater(() -> launchButton.setText("Finalizat"));
    
    
    
                } catch (Exception e) {
    
                    logToConsole("EROARE CRITICĂ LA LANSARE: " + e.getMessage());
    
                    e.printStackTrace();
    
                     SwingUtilities.invokeLater(() -> {
    
                        launchButton.setText("Eroare la lansare");
    
                        launchButton.setBackground(new Color(200, 50, 50));
    
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
        SwingUtilities.invokeLater(() -> {
            new LauncherGui().setVisible(true);
        });
    }
}
