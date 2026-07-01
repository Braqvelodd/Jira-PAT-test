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
    private JComboBox<CertItem> certComboBox;
    private JButton refreshCertButton;
    private JSpinner connTimeoutSpinner;
    private JSpinner readTimeoutSpinner;
    private JButton testButton;
    private JButton clearButton;
    
    private JLabel statusLabel;
    private JTextArea logArea;

    public JiraPatConnectionGui() {
        super("Jira PAT Connection Tester");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(780, 680);
        setLocationRelativeTo(null);

        // Try to apply native look and feel
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        initComponents();
        refreshCertificates(); // Initial setup of cert combobox state
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

        // Bypass SSL Checkbox
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 3;
        bypassSslCheck = new JCheckBox("Bypass Server SSL Certificate Validation (Insecure - trusts any cert)", true);
        configPanel.add(bypassSslCheck, gbc);

        // Windows Certificate Store Checkbox
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 3;
        useWindowsKeyStoreCheck = new JCheckBox("Use Windows Certificate Store (Windows-MY for CAC / Client Cert Mutual TLS Auth)", false);
        useWindowsKeyStoreCheck.addActionListener(e -> refreshCertificates());
        configPanel.add(useWindowsKeyStoreCheck, gbc);

        // Certificate Selector Panel
        JPanel certPanel = new JPanel(new GridBagLayout());
        GridBagConstraints certGbc = new GridBagConstraints();
        certGbc.fill = GridBagConstraints.HORIZONTAL;
        certGbc.insets = new Insets(0, 0, 0, 6);

        certGbc.gridx = 0;
        certGbc.gridy = 0;
        certGbc.weightx = 0.0;
        certPanel.add(new JLabel("Select Certificate:"), certGbc);

        certComboBox = new JComboBox<>();
        certGbc.gridx = 1;
        certGbc.gridy = 0;
        certGbc.weightx = 1.0;
        certPanel.add(certComboBox, certGbc);

        refreshCertButton = new JButton("Refresh");
        refreshCertButton.addActionListener(e -> refreshCertificates());
        certGbc.gridx = 2;
        certGbc.gridy = 0;
        certGbc.weightx = 0.0;
        certGbc.insets = new Insets(0, 0, 0, 0);
        certPanel.add(refreshCertButton, certGbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.gridwidth = 3;
        configPanel.add(certPanel, gbc);

        // Timeouts Panel
        JPanel timeoutsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        timeoutsPanel.add(new JLabel("Connect Timeout (s):"));
        connTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 120, 1));
        timeoutsPanel.add(connTimeoutSpinner);
        
        timeoutsPanel.add(new JLabel("Read Timeout (s):"));
        readTimeoutSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 120, 1));
        timeoutsPanel.add(readTimeoutSpinner);

        gbc.gridx = 0;
        gbc.gridy = 5;
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

    private void refreshCertificates() {
        certComboBox.removeAllItems();
        if (!useWindowsKeyStoreCheck.isSelected()) {
            certComboBox.setEnabled(false);
            refreshCertButton.setEnabled(false);
            certComboBox.addItem(new CertItem(null, "Windows Certificate Store disabled"));
            return;
        }

        try {
            KeyStore keyStore = KeyStore.getInstance("Windows-MY");
            keyStore.load(null, null);

            java.util.List<CertItem> items = new java.util.ArrayList<>();
            java.util.Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (keyStore.isKeyEntry(alias)) {
                    java.security.cert.Certificate cert = keyStore.getCertificate(alias);
                    String label = alias;
                    if (cert instanceof X509Certificate) {
                        X509Certificate x509 = (X509Certificate) cert;
                        String subject = x509.getSubjectX500Principal().getName();
                        label = extractCN(subject) + " [Alias: " + alias + "]";
                    }
                    items.add(new CertItem(alias, label));
                }
            }

            if (items.isEmpty()) {
                certComboBox.addItem(new CertItem(null, "No certificates found in Windows-MY store"));
                certComboBox.setEnabled(false);
            } else {
                for (CertItem item : items) {
                    certComboBox.addItem(item);
                }
                certComboBox.setEnabled(true);
            }
            refreshCertButton.setEnabled(true);

        } catch (Exception e) {
            certComboBox.addItem(new CertItem(null, "Error loading: " + e.getMessage()));
            certComboBox.setEnabled(false);
            refreshCertButton.setEnabled(true);
        }
    }

    private String extractCN(String dn) {
        try {
            for (String part : dn.split(",")) {
                part = part.trim();
                if (part.startsWith("CN=")) {
                    return part.substring(3);
                }
            }
        } catch (Exception ignored) {
        }
        return dn;
    }

    private void performConnectionTest() {
        String targetUrl = urlField.getText().trim();
        char[] tokenChars = tokenField.getPassword();
        String patToken = new String(tokenChars).trim();
        boolean bypassSsl = bypassSslCheck.isSelected();
        boolean useWindowsKeyStore = useWindowsKeyStoreCheck.isSelected();
        int connectTimeout = (int) connTimeoutSpinner.getValue() * 1000;
        int readTimeout = (int) readTimeoutSpinner.getValue() * 1000;

        CertItem selectedCert = null;
        if (useWindowsKeyStore) {
            Object selectedItem = certComboBox.getSelectedItem();
            if (selectedItem instanceof CertItem) {
                selectedCert = (CertItem) selectedItem;
            }
        }
        final String selectedAlias = (selectedCert != null) ? selectedCert.alias : null;

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
                if (useWindowsKeyStore) {
                    publish("Target Certificate Alias: " + (selectedAlias != null ? selectedAlias : "<Default / Not Selected>"));
                }
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

                                KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                                kmf.init(keyStore, null);
                                keyManagers = kmf.getKeyManagers();

                                if (selectedAlias != null) {
                                    publish("Wrapping KeyManagers to enforce use of selected certificate alias: " + selectedAlias);
                                    for (int i = 0; i < keyManagers.length; i++) {
                                        if (keyManagers[i] instanceof X509KeyManager) {
                                            keyManagers[i] = new SelectedAliasKeyManager((X509KeyManager) keyManagers[i], selectedAlias);
                                        }
                                    }
                                } else {
                                    publish("No certificate alias specified. Relying on default JVM KeyManager selection.");
                                }
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
        if (enabled) {
            refreshCertificates(); // Restores correct enabled/disabled state of combobox & refresh button
        } else {
            certComboBox.setEnabled(false);
            refreshCertButton.setEnabled(false);
        }
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

    // Friendly item class to display in JComboBox
    public static class CertItem {
        final String alias;
        final String label;

        public CertItem(String alias, String label) {
            this.alias = alias;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // Custom KeyManager to enforce using the selected certificate alias during mutual SSL/TLS handshake
    private static class SelectedAliasKeyManager implements X509KeyManager {
        private final X509KeyManager delegate;
        private final String selectedAlias;

        SelectedAliasKeyManager(X509KeyManager delegate, String selectedAlias) {
            this.delegate = delegate;
            this.selectedAlias = selectedAlias;
        }

        @Override
        public String chooseClientAlias(String[] keyType, java.security.Principal[] issuers, java.net.Socket socket) {
            return selectedAlias;
        }

        @Override
        public String[] getClientAliases(String keyType, java.security.Principal[] issuers) {
            return new String[]{selectedAlias};
        }

        @Override
        public String[] getServerAliases(String keyType, java.security.Principal[] issuers) {
            return delegate.getServerAliases(keyType, issuers);
        }

        @Override
        public String chooseServerAlias(String keyType, java.security.Principal[] issuers, java.net.Socket socket) {
            return delegate.chooseServerAlias(keyType, issuers, socket);
        }

        @Override
        public X509Certificate[] getCertificateChain(String alias) {
            return delegate.getCertificateChain(alias);
        }

        @Override
        public java.security.PrivateKey getPrivateKey(String alias) {
            return delegate.getPrivateKey(alias);
        }
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
