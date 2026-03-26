package com.example.intrusiondetection;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.graphics.Color;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import javax.net.ssl.SSLSocketFactory;

import java.net.InetAddress;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IntrusionDetection";

    // Try these brokers in order — if one is down or blocked, try the next
    private static final String[] BROKER_URIS = {
            "tcp://broker.hivemq.com:1883",     // HiveMQ - standard
            "tcp://broker.emqx.io:1883",        // EMQX - standard
            "ssl://broker.hivemq.com:8883",     // HiveMQ - encrypted
            "ssl://broker.emqx.io:8883",        // EMQX - encrypted
            "tcp://test.mosquitto.org:1883",    // Mosquitto - standard
            "ssl://test.mosquitto.org:8883"     // Mosquitto - encrypted
    };
    private int brokerIndex = 0;

    private static final String MQTT_TOPIC = "myproject/intrusiondetection"; // ← same as ESP8266
    private static final String CLIENT_ID = "androidClient_" + System.currentTimeMillis();
    private static final String CHANNEL_ID = "IntrusionAlerts";

    // ⏱️ How long before screen resets back to "All Clear" (5 seconds)
    private static final int RESET_DELAY_MS = 5000;

    // Connection retry settings
    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 2000;

    private TextView statusIcon, statusLabel, lastDetection, connectionStatus, debugInfo;
    private MqttAsyncClient mqttClient;

    // Handler is used to schedule the "reset to all clear" after 5 seconds
    private final Handler resetHandler = new Handler(Looper.getMainLooper());
    private Runnable resetRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.status_icon);
        statusLabel = findViewById(R.id.status_label);
        lastDetection = findViewById(R.id.last_detection);
        connectionStatus = findViewById(R.id.connection_status);
        debugInfo = findViewById(R.id.debug_info);

        createNotificationChannel();
        requestNotificationPermission();

        // Connect to MQTT in background thread
        new Thread(this::connectToMQTT).start();
    }

    // Ask user to allow notifications (required for Android 13+)
    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                        1);
            }
        }
    }

    private void connectToMQTT() {
        // First test if the phone can reach the broker at all
        updateDebug("Testing network...");
        try {
            InetAddress address = InetAddress.getByName("broker.hivemq.com");
            updateDebug("DNS OK: " + address.getHostAddress());
        } catch (Exception e) {
            updateDebug("DNS FAILED: " + e.getMessage());
            updateConnectionStatus("● Cannot reach broker — DNS failed", "#F44336");
            return;
        }

        brokerIndex = 0;
        createClientAndConnect();
    }

    private void createClientAndConnect() {
        try {
            String broker = BROKER_URIS[brokerIndex];
            Log.i(TAG, "Trying broker: " + broker);

            // Use MqttAsyncClient directly — no Android service wrapper needed
            mqttClient = new MqttAsyncClient(
                    broker,
                    CLIENT_ID,
                    new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);  // 10 seconds — fail fast, don't hang
            options.setKeepAliveInterval(60);

            // Enable SSL for encrypted connections (ssl:// and wss://)
            if (broker.startsWith("ssl://") || broker.startsWith("wss://")) {
                options.setSocketFactory(SSLSocketFactory.getDefault());
            }

            // Use MqttCallbackExtended to get notified on reconnect
            mqttClient.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    Log.i(TAG, (reconnect ? "Reconnected" : "Connected") + " to " + serverURI);
                    try {
                        mqttClient.subscribe(MQTT_TOPIC, 0);
                        updateConnectionStatus("● Connected to MQTT", "#4CAF50");
                    } catch (MqttException e) {
                        Log.e(TAG, "Failed to subscribe after connect", e);
                    }
                }

                @Override
                public void connectionLost(Throwable cause) {
                    Log.w(TAG, "Connection lost", cause);
                    updateConnectionStatus("● Disconnected — reconnecting...", "#FF9800");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    Log.i(TAG, "Message arrived on " + topic + ": " + payload);

                    String time = DateFormat.getDateTimeInstance().format(new Date());
                    String alertText = "Intrusion Detected @ " + time;

                    showIntrusionAlert(alertText);
                    showNotification("🚨 Intrusion Alert!", alertText);
                    scheduleReset();
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            // Try connecting with retries
            attemptConnection(options, 1);

        } catch (MqttException e) {
            Log.e(TAG, "Failed to create MQTT client for " + BROKER_URIS[brokerIndex], e);
            tryNextBroker();
        }
    }

    private void attemptConnection(MqttConnectOptions options, int attempt) {
        String broker = BROKER_URIS[brokerIndex];
        Log.i(TAG, "Attempt " + attempt + "/" + MAX_RETRIES + " on " + broker);
        updateConnectionStatus("● Connecting... (" + attempt + "/" + MAX_RETRIES + ")", "#FF9800");

        try {
            mqttClient.connect(options, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // Success is handled by connectComplete in MqttCallbackExtended
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    Log.e(TAG, "Attempt " + attempt + " failed on " + broker, exception);
                    updateDebug("Fail #" + attempt + " on " + broker + ": " + exception.getMessage());

                    if (attempt < MAX_RETRIES) {
                        updateConnectionStatus("● Retrying... (" + attempt + "/" + MAX_RETRIES + " failed)", "#FF9800");
                        try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                        attemptConnection(options, attempt + 1);
                    } else {
                        // All retries failed on this broker — try the next one
                        tryNextBroker();
                    }
                }
            });
        } catch (MqttException e) {
            Log.e(TAG, "Connect call failed on attempt " + attempt, e);
            if (attempt < MAX_RETRIES) {
                try { Thread.sleep(RETRY_DELAY_MS); } catch (InterruptedException ignored) {}
                attemptConnection(options, attempt + 1);
            } else {
                tryNextBroker();
            }
        }
    }

    private void tryNextBroker() {
        brokerIndex++;
        if (brokerIndex < BROKER_URIS.length) {
            Log.i(TAG, "Switching to next broker: " + BROKER_URIS[brokerIndex]);
            updateConnectionStatus("● Trying alternate connection...", "#FF9800");
            createClientAndConnect();
        } else {
            // All brokers and all retries exhausted
            updateConnectionStatus("● Connection Failed — check internet", "#F44336");
            updateDebug("All 9 attempts failed. Screenshot this and send to developer.");
        }
    }

    // 🚨 Show red intrusion alert on screen
    private void showIntrusionAlert(String message) {
        runOnUiThread(() -> {
            statusIcon.setText("🚨");
            statusLabel.setText("INTRUSION DETECTED");
            statusLabel.setTextColor(Color.parseColor("#C62828"));
            lastDetection.setText(message);
        });
    }

    // ✅ Reset screen back to normal "All Clear" state
    private void showAllClear() {
        runOnUiThread(() -> {
            statusIcon.setText("🏠");
            statusLabel.setText("All Clear");
            statusLabel.setTextColor(Color.parseColor("#2E7D32"));
            lastDetection.setText("No intrusion detected");
        });
    }

    // ⏱️ Cancel any pending reset, then schedule a new one 5 seconds from now
    private void scheduleReset() {
        if (resetRunnable != null) {
            resetHandler.removeCallbacks(resetRunnable);
        }
        resetRunnable = this::showAllClear;
        resetHandler.postDelayed(resetRunnable, RESET_DELAY_MS);
    }

    private void updateConnectionStatus(String text, String colorHex) {
        runOnUiThread(() -> {
            connectionStatus.setText(text);
            connectionStatus.setTextColor(Color.parseColor(colorHex));
        });
    }

    private void updateDebug(String text) {
        Log.d(TAG, "DEBUG: " + text);
        runOnUiThread(() -> {
            String current = debugInfo.getText().toString();
            // Keep last few lines
            String[] lines = current.split("\n");
            if (lines.length > 4) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 4; i < lines.length; i++) {
                    sb.append(lines[i]).append("\n");
                }
                current = sb.toString();
            }
            debugInfo.setText(current + text + "\n");
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Intrusion Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Alerts when motion is detected");
            channel.enableLights(true);
            channel.setLightColor(Color.RED);
            channel.enableVibration(true);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private void showNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setVibrate(new long[] { 0, 500, 200, 500 });

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up reset timer
        if (resetRunnable != null) {
            resetHandler.removeCallbacks(resetRunnable);
        }
        // Disconnect MQTT
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error disconnecting MQTT", e);
        }
    }
}