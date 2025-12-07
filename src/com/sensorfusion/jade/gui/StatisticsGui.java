package com.sensorfusion.jade.gui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.sensorfusion.jade.agents.StatisticsAgent;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;

public class StatisticsGui extends JFrame {

    private StatisticsAgent myAgent;

    private JList<String> sensorList;
    private DefaultListModel<String> sensorListModel;
    private JComboBox<String> timeRangeComboBox;
    private JButton loadDataButton;
    private JButton refreshButton; // Butonul de refresh
    private JButton clearButton;
    private JTable dataTable;
    private DefaultTableModel dataTableModel;
    private ChartPanel chartPanel;

    // Mapping colors to sensor IDs for consistent charting
    private final Map<String, Color> sensorColors = new ConcurrentHashMap<>();
    private final Color[] predefinedColors = {
            Color.RED, Color.BLUE, Color.GREEN, Color.ORANGE, Color.CYAN,
            Color.MAGENTA, Color.PINK, Color.YELLOW, Color.LIGHT_GRAY
    };
    private int colorIndex = 0;

    public StatisticsGui(StatisticsAgent agent) {
        super("Analiză Statistici Senzori");
        this.myAgent = agent;

        setSize(1000, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        initComponents();
        addListeners();
    }

    private void initComponents() {
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplitPane.setDividerLocation(250);

        // --- Left Panel (Controls) ---
        JPanel controlsPanel = new JPanel(new BorderLayout(10, 10));
        controlsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Sensor List Panel with Refresh Button
        JPanel sensorListPanel = new JPanel(new BorderLayout());
        
        sensorListModel = new DefaultListModel<>();
        sensorList = new JList<>(sensorListModel);
        sensorList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        JScrollPane listScrollPane = new JScrollPane(sensorList);
        
        refreshButton = new JButton("⟳"); // Refresh icon
        
        JPanel titlePanel = new JPanel(new BorderLayout());
        TitledBorder sensorListBorder = new TitledBorder("Senzori Disponibili");
        titlePanel.setBorder(sensorListBorder);
        titlePanel.add(listScrollPane, BorderLayout.CENTER);
        
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.add(new JLabel("Senzori Disponibili"), BorderLayout.WEST);
        headerPanel.add(refreshButton, BorderLayout.EAST);
        
        sensorListPanel.add(headerPanel, BorderLayout.NORTH);
        sensorListPanel.add(listScrollPane, BorderLayout.CENTER);
        
        JPanel timeRangePanel = new JPanel(new BorderLayout());
        timeRangePanel.setBorder(new TitledBorder("Perioadă Timp"));
        String[] timeRanges = {"Ultimele 15 minute", "Ultima oră", "Ultimele 24 de ore"};
        timeRangeComboBox = new JComboBox<>(timeRanges);
        timeRangePanel.add(timeRangeComboBox, BorderLayout.CENTER);

        loadDataButton = new JButton("Încarcă Date");

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        loadDataButton = new JButton("Încarcă Date");
        clearButton = new JButton("Curăță");
        buttonPanel.add(loadDataButton);
        buttonPanel.add(clearButton);
        
        JPanel topControls = new JPanel(new BorderLayout());
        topControls.add(sensorListPanel, BorderLayout.CENTER);
        topControls.add(timeRangePanel, BorderLayout.SOUTH);
        
        controlsPanel.add(topControls, BorderLayout.CENTER);
        controlsPanel.add(buttonPanel, BorderLayout.SOUTH);

        // --- Right Panel (Display) ---
        JPanel displayPanel = new JPanel(new BorderLayout(10, 10));
        
        chartPanel = new ChartPanel();
        chartPanel.setBorder(new TitledBorder("Grafic Valori"));

        dataTableModel = new DefaultTableModel(new String[]{"Senzor", "Timestamp", "Valoare"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        dataTable = new JTable(dataTableModel);
        JScrollPane tableScrollPane = new JScrollPane(dataTable);
        tableScrollPane.setPreferredSize(new Dimension(-1, 200));
        tableScrollPane.setBorder(new TitledBorder("Date Brute"));
        
        displayPanel.add(chartPanel, BorderLayout.CENTER);
        displayPanel.add(tableScrollPane, BorderLayout.SOUTH);

        mainSplitPane.setLeftComponent(controlsPanel);
        mainSplitPane.setRightComponent(displayPanel);
        add(mainSplitPane);
    }

    private void addListeners() {
        loadDataButton.addActionListener(e -> {
            List<String> selectedSensors = sensorList.getSelectedValuesList();
            String selectedRange = (String) timeRangeComboBox.getSelectedItem();
            if (myAgent != null && !selectedSensors.isEmpty() && selectedRange != null) {
                myAgent.requestData(selectedSensors, selectedRange);
            } else if (myAgent != null) { // Only show message if agent exists
                JOptionPane.showMessageDialog(this, "Selectați cel puțin un senzor și o perioadă de timp.", "Atenție", JOptionPane.WARNING_MESSAGE);
            }
        });

        refreshButton.addActionListener(e -> {
            if (myAgent != null) {
                myAgent.requestSensorList();
            }
        });
        
        clearButton.addActionListener(e -> clearDisplay());
    }
    
    // --- Public methods to be called by the agent ---

    public void setSensorList(List<String> sensors) {
        SwingUtilities.invokeLater(() -> {
            sensorListModel.clear();
            sensors.forEach(sensorListModel::addElement);
        });
    }

    public void displayData(Map<String, List<Point>> chartData, List<Object[]> tableData) {
        SwingUtilities.invokeLater(() -> {
            // Assign colors to new sensors
            chartData.keySet().forEach(sensorId -> sensorColors.putIfAbsent(sensorId, getNextColor()));

            // Update chart
            chartPanel.setData(chartData, sensorColors);

            // Update table
            dataTableModel.setRowCount(0); // Clear previous data
            tableData.forEach(dataTableModel::addRow);
        });
    }

    public void clearDisplay() {
        SwingUtilities.invokeLater(() -> {
            sensorList.clearSelection();
            chartPanel.setData(null, null);
            dataTableModel.setRowCount(0);
        });
    }

    private Color getNextColor() {
        Color color = predefinedColors[colorIndex % predefinedColors.length];
        colorIndex++;
        return color;
    }

    /**
     * Inner class for drawing the chart.
     */
    private class ChartPanel extends JPanel {
        private Map<String, List<Point>> data;
        private Map<String, Color> colors;

        private final int PADDING = 30;
        private final int LABEL_PADDING = 15;
        private final Color AXIS_COLOR = Color.LIGHT_GRAY;
        private final Color GRID_COLOR = new Color(70, 70, 70);

        public ChartPanel() {
            setBackground(UIManager.getColor("Panel.background"));
        }

        public void setData(Map<String, List<Point>> data, Map<String, Color> colors) {
            this.data = data;
            this.colors = colors;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (data == null || data.isEmpty() || data.values().stream().allMatch(List::isEmpty)) {
                g.setColor(getForeground());
                g.drawString("Nu există date de afișat. Selectați senzorii și perioada.", PADDING, getHeight() / 2);
                return;
            }

            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Calculate min/max values and adjust drawing space
            double minX = Double.MAX_VALUE;
            double maxX = Double.MIN_VALUE;
            double minY = Double.MAX_VALUE;
            double maxY = Double.MIN_VALUE;

            for (List<Point> sensorData : data.values()) {
                for (Point point : sensorData) {
                    minX = Math.min(minX, point.getX());
                    maxX = Math.max(maxX, point.getX());
                    minY = Math.min(minY, point.getY());
                    maxY = Math.max(maxY, point.getY());
                }
            }

            // Ensure there's a range even if min/max are the same
            if (minY == maxY) {
                minY -= 1;
                maxY += 1;
            }
            if (minX == maxX) {
                minX -= 1;
                maxX += 1;
            }

            double xScale = (getWidth() - 2 * PADDING - LABEL_PADDING) / (maxX - minX);
            double yScale = (getHeight() - 2 * PADDING - LABEL_PADDING) / (maxY - minY);

            // Draw background grid (optional)
            g2.setColor(GRID_COLOR);
            for (int i = 0; i < 10; i++) {
                int x0 = PADDING + LABEL_PADDING + (int) (i * (getWidth() - 2 * PADDING - LABEL_PADDING) / 10.0);
                int y0 = getHeight() - PADDING - LABEL_PADDING - (int) (i * (getHeight() - 2 * PADDING - LABEL_PADDING) / 10.0);
                g2.drawLine(x0, getHeight() - PADDING - LABEL_PADDING, x0, PADDING); // Vertical lines
                g2.drawLine(PADDING + LABEL_PADDING, y0, getWidth() - PADDING, y0);   // Horizontal lines
            }


            // Draw axes
            g2.setColor(AXIS_COLOR);
            // Y-axis
            g2.drawLine(PADDING + LABEL_PADDING, getHeight() - PADDING - LABEL_PADDING, PADDING + LABEL_PADDING, PADDING);
            // X-axis
            g2.drawLine(PADDING + LABEL_PADDING, getHeight() - PADDING - LABEL_PADDING, getWidth() - PADDING, getHeight() - PADDING - LABEL_PADDING);

            // Draw labels and tick marks for Y-axis
            DecimalFormat df = new DecimalFormat("#.##");
            int yAxisTickCount = 10;
            for (int i = 0; i <= yAxisTickCount; i++) {
                int y = getHeight() - PADDING - LABEL_PADDING - (int) (i * (getHeight() - 2 * PADDING - LABEL_PADDING) / (double) yAxisTickCount);
                double yValue = minY + (maxY - minY) * i / (double) yAxisTickCount;
                String yLabel = df.format(yValue);
                g2.drawString(yLabel, PADDING + LABEL_PADDING - g2.getFontMetrics().stringWidth(yLabel) - 5, y + g2.getFontMetrics().getAscent() / 2);
            }

            // Draw labels and tick marks for X-axis
            int xAxisTickCount = 5;
            for (int i = 0; i <= xAxisTickCount; i++) {
                int x = PADDING + LABEL_PADDING + (int) (i * (getWidth() - 2 * PADDING - LABEL_PADDING) / (double) xAxisTickCount);
                double timeValue = minX + (maxX - minX) * i / (double) xAxisTickCount;
                int hours = (int) (timeValue / 60);
                int minutes = (int) (timeValue % 60);
                String xLabel = String.format("%02d:%02d", hours, minutes);
                g2.drawString(xLabel, x - g2.getFontMetrics().stringWidth(xLabel) / 2, getHeight() - PADDING + LABEL_PADDING);
            }

            // Draw data lines and points
            for (Map.Entry<String, List<Point>> entry : data.entrySet()) {
                String sensorId = entry.getKey();
                List<Point> sensorData = entry.getValue();
                Color sensorColor = colors.getOrDefault(sensorId, Color.WHITE);
                g2.setColor(sensorColor);
                g2.setStroke(new BasicStroke(2)); // Thicker line

                for (int i = 0; i < sensorData.size() - 1; i++) {
                    int x1 = (int) (PADDING + LABEL_PADDING + (sensorData.get(i).getX() - minX) * xScale);
                    int y1 = (int) (getHeight() - PADDING - LABEL_PADDING - (sensorData.get(i).getY() - minY) * yScale);
                    int x2 = (int) (PADDING + LABEL_PADDING + (sensorData.get(i + 1).getX() - minX) * xScale);
                    int y2 = (int) (getHeight() - PADDING - LABEL_PADDING - (sensorData.get(i + 1).getY() - minY) * yScale);
                    g2.drawLine(x1, y1, x2, y2);
                }

                // Draw points (optional)
                g2.setColor(sensorColor.darker());
                for (Point point : sensorData) {
                    int x = (int) (PADDING + LABEL_PADDING + (point.getX() - minX) * xScale);
                    int y = (int) (getHeight() - PADDING - LABEL_PADDING - (point.getY() - minY) * yScale);
                    g2.fillOval(x - 3, y - 3, 6, 6);
                }
            }

            // Draw a more robust legend
            int legendX = PADDING + LABEL_PADDING + 10;
            int legendY = PADDING + 10;
            int legendWidth = 0;
            int legendHeight = data.size() * 15 + 10;
            
            FontMetrics fm = g2.getFontMetrics();
            for (String sensorId : data.keySet()) {
                legendWidth = Math.max(legendWidth, fm.stringWidth(sensorId) + 30);
            }

            // Draw legend background
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRect(legendX, legendY, legendWidth, legendHeight);

            // Draw legend items
            g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12));
            int currentY = legendY + 15;
            for (Map.Entry<String, List<Point>> entry : data.entrySet()) {
                g2.setColor(colors.getOrDefault(entry.getKey(), Color.WHITE));
                g2.fillRect(legendX + 5, currentY - 10, 10, 10);
                g2.drawString(entry.getKey(), legendX + 20, currentY);
                currentY += 15;
            }
        }
    }

    // Main method for visual testing
    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {
            System.err.println("Failed to initialize LaF.");
        }

        // Try to launch as a real agent first
        try {
            Runtime rt = Runtime.instance();
            // Exit the JVM when the platform is shut down
            rt.setCloseVM(true);
            Profile p = new ProfileImpl(false); // Non-main container
            p.setParameter(Profile.MAIN_HOST, "localhost");
            p.setParameter(Profile.MAIN_PORT, "1099");
            AgentContainer ac = rt.createAgentContainer(p);

            String agentName = "statistics-gui-launcher-" + System.currentTimeMillis();
            ac.createNewAgent(agentName, "com.sensorfusion.jade.agents.StatisticsAgent", new Object[]{}).start();

        } catch (Exception e) {
            System.err.println("JADE platform not found, launching GUI in offline/mock data mode.");
            // Fallback to offline mode if the platform is not running
            SwingUtilities.invokeLater(() -> {
                StatisticsGui gui = new StatisticsGui(null);

                // Populate with dummy sensors
                List<String> dummySensors = new ArrayList<>();
                dummySensors.add("SenzorTermic1 (Mock)");
                dummySensors.add("SenzorPresiune1 (Mock)");
                dummySensors.add("SenzorUmiditate1 (Mock)");
                gui.setSensorList(dummySensors);

                // Simulate some chart data
                Map<String, List<Point>> dummyChartData = new ConcurrentHashMap<>();
                List<Point> data1 = new ArrayList<>();
                data1.add(new Point(600, ThreadLocalRandom.current().nextInt(10, 30)));
                data1.add(new Point(601, ThreadLocalRandom.current().nextInt(10, 30)));
                data1.add(new Point(602, ThreadLocalRandom.current().nextInt(10, 30)));
                data1.add(new Point(603, ThreadLocalRandom.current().nextInt(10, 30)));
                dummyChartData.put("SenzorTermic1 (Mock)", data1);

                List<Point> data2 = new ArrayList<>();
                data2.add(new Point(600, ThreadLocalRandom.current().nextInt(80, 120)));
                data2.add(new Point(601, ThreadLocalRandom.current().nextInt(80, 120)));
                data2.add(new Point(602, ThreadLocalRandom.current().nextInt(80, 120)));
                data2.add(new Point(603, ThreadLocalRandom.current().nextInt(80, 120)));
                dummyChartData.put("SenzorPresiune1 (Mock)", data2);

                List<Object[]> dummyTableData = new ArrayList<>();
                dummyTableData.add(new Object[]{"SenzorTermic1 (Mock)", "2025-12-06 10:00:00", 22});
                dummyTableData.add(new Object[]{"SenzorTermic1 (Mock)", "2025-12-06 10:01:00", 23});
                dummyTableData.add(new Object[]{"SenzorPresiune1 (Mock)", "2025-12-06 10:00:00", 98});

                gui.displayData(dummyChartData, dummyTableData);

                gui.setVisible(true);
            });
        }
    }
}