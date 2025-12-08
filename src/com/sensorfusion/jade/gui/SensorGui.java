package com.sensorfusion.jade.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sensorfusion.jade.agents.SensorAgent;

import javax.swing.*;
import java.awt.*;

public class SensorGui extends JFrame {

    private SensorAgent myAgent;

    // Componente UI
    private JLabel nameLabel;
    private JLabel idLabel;
    private JLabel typeLabel;
    
    // Labels pentru afișarea intervalelor
    private JLabel hwLimitLabel;
    private JLabel safeLimitLabel;

    // Componente Simulare
    private JSlider slider;
    private JLabel valueLabel;
    // Status Text
    private JLabel statusLabel; 

    // Componente Auto-Simulare
    private JToggleButton activeToggle;
    private JSpinner periodSpinner;
    private Timer simulationTimer;
    private final java.util.Random random = new java.util.Random(); // Keep random for potential manual simulation or future use

    // Date de configurare curente
    private String currentUnit = "";
    private int safeMin, safeMax;
    private int hwMin, hwMax;

    /**
     * Constructor
     * @param agent - Referință către agentul JADE (pentru a trimite datele înapoi)
     */
    public SensorGui(SensorAgent agent) {
        super("Panou Simulare Senzor IoT");
        this.myAgent = agent;

        // Configurare fereastră
        setSize(400, 450); // Adjusted height
        setLocationRelativeTo(null); // Centrat
        // IMPORTANT: Nu folosim EXIT_ON_CLOSE, care ar închide toată platforma JADE.
        // În schimb, notificăm agentul să se închidă singur.
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (myAgent != null) {
                    myAgent.requestToDoDelete();
                }
            }
        });
        setResizable(false);

        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- 1. SECȚIUNEA INFO TEHNIC (Sus) ---
        JPanel infoPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Specificații Tehnice"));
        
        // Rând 1: ID
        infoPanel.add(new JLabel("ID Dispozitiv:"));
        idLabel = new JLabel("---");
        idLabel.setForeground(Color.CYAN);
        infoPanel.add(idLabel);

        // Rând 2: Tip
        infoPanel.add(new JLabel("Tip Senzor:"));
        typeLabel = new JLabel("---");
        infoPanel.add(typeLabel);
        
        // Rând 3: Limite Hardware
        infoPanel.add(new JLabel("Interval Fizic (HW):"));
        hwLimitLabel = new JLabel("---");
        hwLimitLabel.setForeground(Color.LIGHT_GRAY);
        infoPanel.add(hwLimitLabel);

        // Rând 4: Limite Safe
        infoPanel.add(new JLabel("Interval Sigur (Safe):"));
        safeLimitLabel = new JLabel("---");
        safeLimitLabel.setForeground(new Color(100, 255, 100));
        infoPanel.add(safeLimitLabel);

        // --- 2. SECȚIUNEA SIMULARE (Jos) ---
        JPanel simPanel = new JPanel();
        simPanel.setLayout(new BoxLayout(simPanel, BoxLayout.Y_AXIS));
        simPanel.setBorder(BorderFactory.createTitledBorder("Simulare Mediu"));

        // Control Panel for Auto-Send
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        
        activeToggle = new JToggleButton("Active", true);
        activeToggle.setForeground(Color.GREEN);

        periodSpinner = new JSpinner(new SpinnerNumberModel(3000, 100, 60000, 100));
        periodSpinner.setToolTipText("Transmission Period (ms)");
        
        controlPanel.add(activeToggle);
        controlPanel.add(new JLabel("Perioada (ms):"));
        controlPanel.add(periodSpinner);

        // Configurare Timer Simulare (default 3000ms, will be updated)
        simulationTimer = new Timer(3000, e -> {
            if (slider.isEnabled() && myAgent != null) {
                int val = slider.getValue();
                int period = (Integer) periodSpinner.getValue();
                myAgent.processSensorData(val, period);
            }
        });

        activeToggle.addActionListener(e -> {
            if (activeToggle.isSelected()) {
                activeToggle.setText("Active");
                activeToggle.setForeground(Color.GREEN);
                startAutoSend();
            } else {
                activeToggle.setText("Inactive");
                activeToggle.setForeground(Color.RED);
                stopAutoSend();
            }
        });

        // Slider
        slider = new JSlider(0, 100, 0);
        slider.setEnabled(false); // Blocat până la configurare
        slider.setMajorTickSpacing(20);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        
        // Listener pentru interacțiunea manuală (Mouse)
        slider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent e) {
                // Dacă userul atinge sliderul, oprim modul auto
                if (activeToggle.isSelected()) {
                    activeToggle.setSelected(false);
                    activeToggle.setText("Inactive");
                    activeToggle.setForeground(Color.RED);
                    stopAutoSend();
                }
            }
        });

        // Valoare Mare
        valueLabel = new JLabel("---");
        valueLabel.setFont(new Font("Monospaced", Font.BOLD, 32));
        valueLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        valueLabel.setBorder(BorderFactory.createEmptyBorder(10, 0, 10, 0));

        // Status Text
        statusLabel = new JLabel("Așteptare Configurare...");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // --- LOGICA EVENIMENTELOR ---
        slider.addChangeListener(e -> {
            // Trimitem datele fie la finalul mișcării (manual), fie oricând în mod auto
            // În mod auto, setValue() nu setează 'valueIsAdjusting', deci intrăm direct aici.
            // La manual, vrem doar când dă drumul la mouse, DAR pentru update vizual vrem mereu.
            
            int val = slider.getValue();
            updateVisuals(val); // Update vizual instant
            
            // Trimitem la agent doar dacă:
            // 1. E mod Auto (timerul a trimis valoarea)
            // 2. SAU Userul a terminat de tras de slider (!getValueIsAdjusting)
            // NOTA: Când activeToggle este activ, slider.getValueIsAdjusting() ar trebui să fie fals,
            // iar valoarea este trimisă de simulationTimer.
            // Când activeToggle este inactiv, trimitem doar la ajustare manuală.
            if (myAgent != null && (!activeToggle.isSelected() || !slider.getValueIsAdjusting())) {
                int period = (Integer) periodSpinner.getValue();
                myAgent.processSensorData(val, period);
            }
        });

        simPanel.add(Box.createVerticalStrut(10));
        simPanel.add(controlPanel); // Adăugăm panoul de control
        simPanel.add(Box.createVerticalStrut(10));
        simPanel.add(slider);
        simPanel.add(valueLabel);
        simPanel.add(statusLabel);
        simPanel.add(Box.createVerticalStrut(10));

        // Adăugare în panel principal
        mainPanel.add(infoPanel);
        mainPanel.add(Box.createVerticalStrut(15));
        mainPanel.add(simPanel);

        add(mainPanel);
    }

    private void startAutoSend() {
        int period = (Integer) periodSpinner.getValue();
        simulationTimer.setDelay(period);
        simulationTimer.start();
        
        periodSpinner.setEnabled(false);
    }

    private void stopAutoSend() {
        simulationTimer.stop();
        periodSpinner.setEnabled(true);
    }

    /**
     * Logică vizuală pentru colorarea interfeței în funcție de cele 3 intervale
     */
    private void updateVisuals(int val) {
        valueLabel.setText(val + " " + currentUnit);

        if (val >= safeMin && val <= safeMax) {
            // Cazul A: NORMAL (Verde)
            valueLabel.setForeground(new Color(80, 200, 120));
            statusLabel.setText("PARAMETRI OPTIMI");
            statusLabel.setForeground(new Color(80, 200, 120));
            
        } else if (val >= hwMin && val <= hwMax) {
            // Cazul B: WARNING (Portocaliu) - E valid hardware, dar nesigur
            valueLabel.setForeground(new Color(255, 180, 0)); 
            statusLabel.setText("⚠ ATENȚIE: VALOARE RISCANTĂ");
            statusLabel.setForeground(new Color(255, 180, 0));
            
        } else {
            // Cazul C: CRITICAL/ERROR (Roșu) - Depășește capacitatea senzorului
            valueLabel.setForeground(new Color(255, 60, 60));
            statusLabel.setText("⛔ EROARE SENZOR / LIMITĂ DEPĂȘITĂ");
            statusLabel.setForeground(new Color(255, 60, 60));
        }
    }

    /**
     * Metoda publică apelată de Agent la pornire pentru a seta limitele.
     */
    public void updateConfiguration(
            String id, String type, String unit,
            int sliderMin, int sliderMax, // Interval Simulare
            int hwMin, int hwMax,         // Interval Hardware
            int safeMin, int safeMax,     // Interval Safe
            int periodSeconds             // Period from JSON
    ) {
        System.out.println("DEBUG: updateConfiguration called for " + type + " with periodSeconds=" + periodSeconds);
        // 1. Actualizare variabile interne
        this.currentUnit = unit;
        this.hwMin = hwMin;
        this.hwMax = hwMax; 
        this.safeMin = safeMin;
        this.safeMax = safeMax;

        // 2. Actualizare Label-uri Info
        setTitle("Senzor: " + type);
        idLabel.setText(id);
        typeLabel.setText(type);
        hwLimitLabel.setText("[" + hwMin + " ... " + hwMax + "] " + unit);
        safeLimitLabel.setText("[" + safeMin + " ... " + safeMax + "] " + unit);

        // 3. Configurare Slider
        slider.setMinimum(sliderMin);
        slider.setMaximum(sliderMax);
        
        // Calcul tick-uri (pentru aspect estetic)
        int range = sliderMax - sliderMin;
        int majorTick = (range > 0) ? range / 5 : 10;
        slider.setMajorTickSpacing(majorTick);
        slider.setMinorTickSpacing(majorTick / 2);
        slider.setLabelTable(null); // Reset etichete vechi
        
        // 4. Configurare Perioada (din JSON)
        int periodMs = periodSeconds * 1000;
        // Ajustăm modelul spinner-ului pentru a permite valori mai mari (ex: 120s = 120000ms)
        periodSpinner.setModel(new SpinnerNumberModel(periodMs, 100, 300000, 100));
        periodSpinner.setValue(periodMs);

        // Pornim cu slider-ul pe o valoare sigură (mijlocul intervalului safe)
        int startVal = (safeMin + safeMax) / 2;
        slider.setValue(startVal);
        slider.setEnabled(true);
        
        // Update inițial
        updateVisuals(startVal);
        
        // Dacă modul Auto este activ, pornim timerul cu noua perioadă
        if (activeToggle.isSelected()) {
            startAutoSend();
        }
    }

    // --- MAIN PENTRU TESTARE VIZUALĂ (Fără JADE) ---
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}

        SwingUtilities.invokeLater(() -> {
            // Pasăm null la agent pentru testare
            SensorGui gui = new SensorGui(null);
            gui.setVisible(true);

            // Simulăm primirea configurației după 1 secundă
            new Timer(1000, e -> {
                gui.updateConfiguration(
                    "SENS-TEST-001", 
                    "Termostat Laborator", 
                    "°C",
                    -50, 150,   // Slider (Userul poate exagera mult)
                    -20, 100,   // Hardware (Senzorul crapă la 100+)
                    18, 24,     // Safe (Temperatura ideală)
                    30          // Period (seconds)
                );
                ((Timer)e.getSource()).stop();
            }).start();
        });
    }
}
