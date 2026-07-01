import java.awt.*;
import java.awt.event.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

public class JiraPatConnectionGui extends JFrame {

    private JTextField urlField;
    private JPasswordField tokenField;
    private JCheckBox showTokenCheck;
    private JCheckBox bypassSslCheck;
    private JCheckBox useWindowsKeyStoreCheck;
    private JSpinner connTimeoutSpinner;
    private JSpinner readTimeoutSpinner;
    private JButton testButton;
    private JButton clearButton;
    
    private JLabel statusLabel;
    private JTextArea logArea;

    public JiraPatConnectionGui() {
        super("Jira PAT Connection Tester");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 650);
        setLocationRelativeTo(null);

        // Try to apply native look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        initComponents();
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // --- TOP PANEL: Configuration ---
        JPanel configPanel = new JPanel(new GridBagLayout());
        configPanel.setBorder(BorderFactory.createTitledBorder("Configuration"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Jira URL
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        configPanel.add(new JLabel("Target JIRA URL:"), gbc);

        urlField = new JTextField("https://tso-jira.mcw.usmc.mil/rest/api/2/myself");
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        configPanel.add(urlField, gbc);

        // PAT Token
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 1;
        gbc.weightx = 0.0;
        configPanel.add(new JLabel("PAT Token:"), gbc);

        tokenField = new JPasswordField(30);
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        configPanel.add(tokenField, gbc);

        showTokenCheck = new JCheckBox("Show");
        showTokenCheck.addActionListener(e -> {
            if (showTokenCheck.isSelected()) {
                tokenField.setEchoChar((char) 0);
            } else {
                tokenField.setEchoChar('\u2022'); // Default bullet
            }
        });
        gbc.gridx = 2;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        configPanel.add(showTokenCheck, gbc);

        // Bypass SSL
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        bypassSslCheck = new JCheckBox("Bypass Server SSL Certificate Validation (Insecure - trusts any cert)", true);
        configPanel.add(bypassSslCheck, gbc);

        // Windows Certificate Store (CAC)
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        useWindowsKeyStoreCheck = new JCheckBox("Use Windows Certificate Store (Windows-MY for CAC / Client Cert Mutual TLS Auth)", false);
        configPanel.add(useWindowsKeyStoreCheck, gbc);

        // Timeouts Panel
        JPanel timeoutsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        timeoutsPanel.add(new JLabel("Connect Timeout (s):"));
        connTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 120, 1));
        timeoutsPanel.add(connTimeoutSpinner);
        
        timeoutsPanel.add(new JLabel("Read Timeout (s):"));
        readTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 120, 1));
        timeoutsPanel.add(readTimeoutSpinner);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        configPanel.add(timeoutsPanel, gbc);

        mainPanel.add(configPanel, BorderLayout.NORTH);

        // --- CENTER PANEL: Logs and Output ---
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Results & Output"));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Status Label at bottom of center panel
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font(statusLabel.getFont().getName(), Font.BOLD, 13));
        statusLabel.setBorder(new EmptyBorder(5, 5, 5, 5));
        centerPanel.add(statusLabel, BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // --- SOUTH PANEL: Actions ---
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        
        clearButton = new JButton("Clear Logs");
        clearButton.addActionListener(e -> {
            logArea.setText("");
            statusLabel.setText("Ready");
            statusLabel.setForeground(Color.BLACK);
        });
        actionPanel.add(clearButton);

        testButton = new JButton("Test Connection");
        testButton.setFont(new Font(testButton.getFont().getName(), Font.BOLD, 12));
        testButton.addActionListener(e -> performConnectionTest());
        actionPanel.add(testButton);

        mainPanel.add(actionPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void performConnectionTest() {
        String targetUrl = urlField.getText().trim();
        char[] tokenChars = tokenField.getPassword();
        String patToken = new String(tokenChars).trim();
        boolean bypassSsl = bypassSslCheck.isSelected();
        boolean useWindowsKeyStore = useWindowsKeyStoreCheck.isSelected();
        int connectTimeout = (int) connTimeoutSpinner.getValue() * 1000;
        int readTimeout = (int) readTimeoutSpinner.getValue() * 1000;

        if (targetUrl.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a target URL.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Disable UI
        setUiEnabled(false);
        logArea.setText("");
        statusLabel.setText("Connecting...");
        statusLabel.setForeground(Color.BLUE);

        // Run network operation in SwingWorker thread
        SwingWorker<ConnectionResult, String> worker = new SwingWorker<ConnectionResult, String>() {
            @Override
            protected ConnectionResult doInBackground() {
                // 1. Diagnostics Logging
                publish("=== System Diagnostics ===");
                publish("Java Version: " + System.getProperty("java.version") + " (" + System.getProperty("java.vendor") + ")");
                publish("Java VM:      " + System.getProperty("java.vm.name"));
                publish("OS Platform:  " + System.getProperty("os.name") + " (version: " + System.getProperty("os.version") + ")");
                try {
                    SSLContext context = SSLContext.getInstance("TLS");
                    context.init(null, null, null);
                    String[] defaultProtocols = context.getDefaultSSLParameters().getProtocols();
                    String[] supportedProtocols = context.getSupportedSSLParameters().getProtocols();
                    publish("Default TLS Protocols:   " + String.join(", ", defaultProtocols));
                    publish("Supported TLS Protocols: " + String.join(", ", supportedProtocols));
                } catch (Exception e) {
                    publish("Diagnostics Error: Failed to query JVM TLS capabilities: " + e.getMessage());
                }
                publish("============================================");
                publish("Target URL: " + targetUrl);
                publish("Bypass SSL: " + bypassSsl);
                publish("Use Windows Certificate Store: " + useWindowsKeyStore);
                publish("Connect Timeout: " + (connectTimeout / 1000) + "s");
                publish("Read Timeout: " + (readTimeout / 1000) + "s");
                publish("--------------------------------------------");

                HttpURLConnection connection = null;
                BufferedReader reader = null;
                try {
                    URL url = new URL(targetUrl);
                    connection = (HttpURLConnection) url.openConnection();

                    // SSL Setup
                    if (connection instanceof HttpsURLConnection) {
                        KeyManager[] keyManagers = null;
                        TrustManager[] trustAllCerts = null;

                        // Set up Client KeyManagers using Windows Certificate Store if checked
                        if (useWindowsKeyStore) {
                            publish("Attempting to load Windows Certificate Store (Windows-MY)...");
                            try {
                                KeyStore keyStore = KeyStore.getInstance("Windows-MY");
                                keyStore.load(null, null);

                                int certCount = 0;
                                java.util.Enumeration<String> aliases = keyStore.aliases();
                                while (aliases.hasMoreElements()) {
                                    aliases.nextElement();
                                    certCount++;
                                }
                                publish("Successfully loaded Windows Certificate Store. Found " + certCount + " certificate aliases.");

                                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                                kmf.init(keyStore, null);
                                keyManagers = kmf.getKeyManagers();
                                publish("KeyManagers loaded successfully.");
                            } catch (Exception e) {
                                publish("Warning: Failed to load Windows-MY keystore: " + e.getMessage());
                                publish("Proceeding without Windows client certificates.");
                            }
                        }

                        // Set up trust-all TrustManagers if checked
                        if (bypassSsl) {
                            publish("Configuring TrustManager to bypass server certificate validation (Insecure)...");
                            trustAllCerts = new TrustManager[]{
                                new X509TrustManager() {
                                    public X509Certificate[] getAcceptedIssuers() { return null; }
                                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                                }
                            };
                        }

                        // Apply SSL Socket Factory
                        if (useWindowsKeyStore || bypassSsl) {
                            publish("Initializing custom SSLContext (TLS)...");
                            SSLContext sslContext = SSLContext.getInstance("TLS");
                            sslContext.init(keyManagers, trustAllCerts, new SecureRandom());

                            HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                            httpsConnection.setSSLSocketFactory(sslContext.getSocketFactory());

                            if (bypassSsl) {
                                HostnameVerifier allHostsValid = (hostname, session) -> true;
                                httpsConnection.setHostnameVerifier(allHostsValid);
                            }
                        }
                    }

                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(connectTimeout);
                    connection.setReadTimeout(readTimeout);

                    // Add headers
                    if (!patToken.isEmpty()) {
                        publish("Applying Authorization header (Bearer token length: " + patToken.length() + ")");
                        connection.setRequestProperty("Authorization", "Bearer " + patToken);
                    } else {
                        publish("Warning: Running test without JIRA PAT token.");
                    }
                    connection.setRequestProperty("Accept", "application/json");

                    publish("Connecting...");
                    long startTime = System.currentTimeMillis();
                    int responseCode = connection.getResponseCode();
                    long duration = System.currentTimeMillis() - startTime;
                    publish("Response received in " + duration + " ms.");
                    publish("HTTP Status Code: " + responseCode);

                    // Read response body
                    StringBuilder responseContent = new StringBuilder();
                    if (responseCode >= 200 && responseCode < 300) {
                        reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    } else {
                        // For non-2xx status, try to read error stream
                        java.io.InputStream errStream = connection.getErrorStream();
                        if (errStream != null) {
                            reader = new BufferedReader(new InputStreamReader(errStream));
                        } else {
                            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        }
                    }

                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseContent.append(line).append("\n");
                    }

                    return new ConnectionResult(responseCode, responseContent.toString(), null);

                } catch (Exception e) {
                    return new ConnectionResult(-1, null, e);
                } finally {
                    if (reader != null) {
                        try { reader.close(); } catch (Exception ignored) {}
                    }
                    if (connection != null) {
                        connection.disconnect();
                    }
                }
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String message : chunks) {
                    logArea.append(message + "\n");
                }
            }

            @Override
            protected void done() {
                try {
                    ConnectionResult result = get();
                    if (result.exception != null) {
                        logArea.append("\n❌ Connection Error:\n");
                        logArea.append(getStackTraceString(result.exception));
                        statusLabel.setText("Connection Failed: " + result.exception.getMessage());
                        statusLabel.setForeground(new Color(198, 40, 40)); // Dark red
                    } else {
                        logArea.append("\nResponse Content:\n");
                        logArea.append(result.responseBody.isEmpty() ? "<Empty Response>" : result.responseBody);
                        
                        if (result.responseCode >= 200 && result.responseCode < 300) {
                            statusLabel.setText("✅ Connection Successful! Status: " + result.responseCode);
                            statusLabel.setForeground(new Color(46, 125, 50)); // Dark green
                        } else {
                            statusLabel.setText("❌ Connection Failed. Status: " + result.responseCode);
                            statusLabel.setForeground(new Color(198, 40, 40)); // Dark red
                        }
                    }
                } catch (Exception e) {
                    logArea.append("\nException occurred in SwingWorker: " + e.getMessage() + "\n");
                    statusLabel.setText("Error occurred during execution.");
                    statusLabel.setForeground(Color.RED);
                } finally {
                    setUiEnabled(true);
                }
            }
        };

        worker.execute();
    }

    private void setUiEnabled(boolean enabled) {
        urlField.setEnabled(enabled);
        tokenField.setEnabled(enabled);
        showTokenCheck.setEnabled(enabled);
        bypassSslCheck.setEnabled(enabled);
        useWindowsKeyStoreCheck.setEnabled(enabled);
        connTimeoutSpinner.setEnabled(enabled);
        readTimeoutSpinner.setEnabled(enabled);
        testButton.setEnabled(enabled);
        clearButton.setEnabled(enabled);
    }

    private String getStackTraceString(Throwable throwable) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    private static class ConnectionResult {
        final int responseCode;
        final String responseBody;
        final Exception exception;

        ConnectionResult(int responseCode, String responseBody, Exception exception) {
            this.responseCode = responseCode;
            this.responseBody = responseBody;
            this.exception = exception;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JiraPatConnectionGui app = new JiraPatConnectionGui();
            app.setVisible(true);
        });
    }
}
