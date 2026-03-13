package com.example.intrusiondetection; // make sure this matches your package name

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationCompat;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.graphics.Color;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    // ⚠️ Must match EXACTLY what you set in your ESP8266 code
    private static final String MQTT_BROKER   = "tcp://broker.hivemq.com:1883";
    private static final String MQTT_TOPIC    = "myproject/intrusiondetection"; // ← same as ESP8266
    private static final String CLIENT_ID     = "androidClient_" + System.currentTimeMillis();
    private static final String CHANNEL_ID    = "IntrusionAlerts";

    private TextView statusIcon, statusLabel, lastDetection, connectionStatus;
    private MqttClient mqttClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get references to UI elements
        statusIcon       = findViewById(R.id.status_icon);
        statusLabel      = findViewById(R.id.status_label);
        lastDetection    = findViewById(R.id.last_detection);
        connectionStatus = findViewById(R.id.connection_status);

        // Create notification channel (required for Android 8+)
        createNotificationChannel();

        // Connect to MQTT in background thread
        new Thread(this::connectToMQTT).start();
    }

    private void connectToMQTT() {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            mqttClient = new MqttClient(MQTT_BROKER, CLIENT_ID, new MemoryPersistence());
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    updateConnectionStatus("● Disconnected", "#F44336");
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    // Motion detected! Update UI and show notification
                    String time = DateFormat.getDateTimeInstance().format(new Date());
                    String alertText = "Intrusion Detected @ " + time;
                    updateUI(alertText);
                    showNotification("🚨 Intrusion Alert!", alertText);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) { }
            });

            mqttClient.connect(options);
            mqttClient.subscribe(MQTT_TOPIC, 0);
            updateConnectionStatus("● Connected to MQTT", "#4CAF50");

        } catch (MqttException e) {
            e.printStackTrace();
            updateConnectionStatus("● Connection Failed", "#F44336");
        }
    }

    private void updateUI(String message) {
        runOnUiThread(() -> {
            statusIcon.setText("🚨");
            statusLabel.setText("INTRUSION DETECTED");
            statusLabel.setTextColor(Color.parseColor("#C62828"));
            lastDetection.setText(message);
        });
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
                    CHANNEL_ID, "Intrusion Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts when motion is detected");
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
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(1, builder.build());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}