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

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "IntrusionDetection";

    // Use WebSocket URI for the public HiveMQ broker
    private static final String MQTT_BROKER = "ws://broker.hivemq.com:8000/mqtt";
    private static final String MQTT_TOPIC = "myproject/intrusiondetection"; // ← same as ESP8266
    private static final String CLIENT_ID = "androidClient_" + System.currentTimeMillis();
    private static final String CHANNEL_ID = "IntrusionAlerts";

    // ⏱️ How long before screen resets back to "All Clear" (5 seconds)
    private static final int RESET_DELAY_MS = 5000;

    private TextView statusIcon, statusLabel, lastDetection, connectionStatus;
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
        try {
            // Use MqttAsyncClient directly — no Android service wrapper needed
            mqttClient = new MqttAsyncClient(
                    MQTT_BROKER,
                    CLIENT_ID,
                    new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);

            // Use MqttCallbackExtended to get notified on reconnect
            mqttClient.setCallback(new MqttCallbackExtended() {

                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    // Called on initial connect AND on every auto-reconnect
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
                    updateConnectionStatus("● Disconnected", "#F44336");
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
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // Not used — we only subscribe, not publish
                }
            });

            // Initiate the connection
            mqttClient.connect(options);

        } catch (MqttException e) {
            Log.e(TAG, "Failed to connect to MQTT", e);
            updateConnectionStatus("● Connection Failed", "#F44336");
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