package com.sensorfusion.jade.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sensorfusion.jade.agents.ControllerAgent;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ControllerGui extends JFrame {

    // Referință către agentul care deține această fereastră
    private ControllerAgent myAgent;
    
    // Componente UI
    private JTextArea logArea;
    private DefaultTableModel tableModel;
    private JTable table;

    /**
     * Constructor principal
     * @param agent - Referință către ControllerAgent
     */
    public ControllerGui(ControllerAgent agent) {
        super("Dispecerat Central IoT");
        this.myAgent = agent;
        
        // Configurare fereastră
        setSize(950, 600);
        setLocation(400, 100);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE); // Important: Doar distruge fereastra
        
        // Adăugăm un listener pentru a notifica agentul la închidere
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (myAgent != null) {
                    myAgent.guiClosed();
                }
            }
        });
        
        // Inițializare componente
        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // --- 1. HEADER (Titlu + Buton Urgență) ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        
        JLabel titleLabel = new JLabel("Monitorizare Rețea Senzori");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(100, 200, 255)); // Albastru deschis
        
        JButton btnShutdown = new JButton("SHUTDOWN SYSTEM");
        btnShutdown.setBackground(new Color(200, 50, 50));
        btnShutdown.setForeground(Color.WHITE);
        btnShutdown.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnShutdown.setFocusPainted(false);
        
        // Acțiune Buton
        btnShutdown.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                this, 
                "Sigur doriți să opriți întregul sistem?\nToți senzorii vor fi deconectați.",
                "Confirmare Oprire",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE
            );
            
            if (confirm == JOptionPane.YES_OPTION) {
                logToConsole("⚠️ COMANDĂ MANUALĂ: OPRIRE SISTEM...");
                if (myAgent != null) {
                    myAgent.shutdownSystem();
                } else {
                    System.out.println("Demo Mode: Sistem oprit.");
                    System.exit(0);
                }
            }
        });

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(btnShutdown, BorderLayout.EAST);

        // --- 2. TABEL (Central) ---
        String[] columns = {"ID Senzor", "Tip", "Valoare", "Unitate", "Status", "Ultima Actualizare"};
        
        // Model tabel care nu permite editarea celulelor
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(30);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 14));
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        // Setăm renderer-ul custom pentru culori
        StatusColorRenderer renderer = new StatusColorRenderer();
        for (int i=0; i<table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Senzori Conectați"));

        // --- 3. LOG (Jos) ---
        logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        // logArea.setForeground(new Color(200, 200, 200)); // Opțional: Gri deschis
        
        JScrollPane logScroll = new JScrollPane(logArea);
        logScroll.setBorder(BorderFactory.createTitledBorder("Jurnal Evenimente"));

        // Asamblare
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(tableScroll, BorderLayout.CENTER);
        mainPanel.add(logScroll, BorderLayout.SOUTH);

        add(mainPanel);
    }

    /**
     * Metoda principală apelată de Agent pentru a actualiza datele.
     * Folosește SwingUtilities.invokeLater pentru a fi thread-safe.
     */
    public void updateSensor(String id, String type, String val, String unit, String status) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            boolean found = false;

            for (int i = 0; i < tableModel.getRowCount(); i++) {
                if (tableModel.getValueAt(i, 0).equals(id)) {
                    tableModel.setValueAt(val, i, 2);
                    tableModel.setValueAt(unit, i, 3);
                    tableModel.setValueAt(status, i, 4);
                    tableModel.setValueAt(time, i, 5);
                    found = true;
                    break;
                }
            }

            if (!found) {
                tableModel.addRow(new Object[]{id, type, val, unit, status, time});
                logToConsole("Senzor nou detectat: " + id + " [" + type + "]");
            }

            // Forțăm redesenarea întregului tabel pentru a actualiza culorile
            table.repaint();
        });
    }

    /**
     * Adaugă un mesaj în consola de jos.
     */
    public void logToConsole(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
            logArea.append("[" + time + "] " + msg + "\n");
            // Scroll automat jos
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // --- Renderer pentru Culori (Clasă Internă) ---
    private class StatusColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            
            Object statusObj = table.getModel().getValueAt(row, 4);
            String status = (statusObj != null) ? statusObj.toString() : "";

            Color defaultForeground = isSelected ? UIManager.getColor("Table.selectionForeground") : UIManager.getColor("Table.foreground");
            Color defaultBackground = isSelected ? UIManager.getColor("Table.selectionBackground") : UIManager.getColor("Table.background");

            c.setForeground(defaultForeground);
            c.setBackground(defaultBackground);

            switch(status) {
                case "NORMAL":
                    c.setForeground(isDarkTheme() ? Color.BLACK : Color.WHITE);
                    c.setBackground(new Color(46, 139, 87)); // SeaGreen
                    break;
                case "WARNING":
                     c.setForeground(isDarkTheme() ? Color.BLACK : Color.WHITE);
                    c.setBackground(new Color(255, 165, 0)); // Orange
                    break;
                case "SENSOR_ERROR":
                case "CRITICAL":
                     c.setForeground(isDarkTheme() ? Color.BLACK : Color.WHITE);
                    c.setBackground(new Color(220, 20, 60));   // Crimson
                    break;
                case "INACTIVE":
                    c.setForeground(isDarkTheme() ? new Color(200, 200, 200) : Color.WHITE);
                    c.setBackground(Color.DARK_GRAY);
                    break;
                case "REGISTERED":
                    c.setForeground(isDarkTheme() ? Color.BLACK : Color.WHITE);
                    c.setBackground(new Color(70, 130, 180)); // SteelBlue
                    break;
            }
            return c;
        }
        
        private boolean isDarkTheme() {
            Color bg = UIManager.getColor("Panel.background");
            return (bg.getRed() + bg.getGreen() + bg.getBlue()) / 3 < 128;
        }
    }

    // --- MAIN PENTRU TESTARE VIZUALĂ (Fără JADE) ---
    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}

        SwingUtilities.invokeLater(() -> {
            ControllerGui gui = new ControllerGui(null);
            gui.setVisible(true);

            gui.logToConsole("Sistem de test inițializat.");
            gui.updateSensor("TEST-01", "Temperatură", "24", "°C", "NORMAL");
            gui.updateSensor("TEST-02", "Presiune", "150", "Bar", "WARNING");
            gui.updateSensor("TEST-03", "Umiditate", "999", "%", "SENSOR_ERROR");
            gui.updateSensor("TEST-04", "Lumină", "N/A", "lm", "INACTIVE");
            gui.updateSensor("TEST-05", "Gaz", "N/A", "ppm", "REGISTERED");
        });
    }
}