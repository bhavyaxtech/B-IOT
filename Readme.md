# 🔐 IoT Intrusion Detection System

A real-time intrusion detection system built with a **NodeMCU ESP8266**, **HC-SR501 motion sensor**, and an **Android app** — connected over the internet using the **MQTT protocol**.

When motion is detected, an instant alert is pushed to your Android phone — no matter where you are.

---

## 📸 How It Works

```
┌─────────────────┐        ┌──────────────────┐        ┌─────────────────┐
│  HC-SR501       │        │  MQTT Broker     │        │  Android App    │
│  Motion Sensor  │──────▶ │  broker.hivemq   │──────▶ │  (your phone)   │
│  + NodeMCU      │  WiFi  │  .com            │  WiFi  │                 │
│  ESP8266        │        │                  │        │  🚨 Alert!      │
└─────────────────┘        └──────────────────┘        └─────────────────┘

   Detects motion               Routes the                Shows notification
   Publishes alert              MQTT message              & updates screen
```

---

## 🧰 Hardware Required

| Component | Purpose |
|-----------|---------|
| NodeMCU ESP8266 | Microcontroller with built-in WiFi |
| HC-SR501 Motion Sensor | Detects infrared motion |
| Breadboard | Holds components together |
| Jumper Cables (x3) | Connects sensor to board |
| USB Cable (Micro-USB) | Powers and programs the NodeMCU |

---

## 🔌 Wiring

| HC-SR501 Pin | NodeMCU Pin |
|---|---|
| VCC | 3V3 |
| OUT | D3 |
| GND | GND |

> ⚠️ Always use **3V3** — never VIN or 5V on the NodeMCU.

---

## 🛠️ Software & Tools

| Tool | Purpose |
|------|---------|
| Arduino IDE | Program the NodeMCU ESP8266 |
| Android Studio | Build the Android app |
| MQTT Explorer | Test and debug MQTT messages |
| broker.hivemq.com | Free public MQTT broker (no account needed) |

### Arduino Libraries
- `ESP8266WiFi.h` — built-in with ESP8266 board package
- `PubSubClient` by Nick O'Leary — MQTT communication

### Android Libraries
- `org.eclipse.paho.client.mqttv3` — MQTT client
- `org.eclipse.paho.android.service` — MQTT background service

---

## 🚀 Getting Started

### 1. Set Up Arduino IDE

1. Install Arduino IDE from https://www.arduino.cc/en/software
2. Add ESP8266 board support via **File → Preferences → Additional Boards Manager URLs**:
   ```
   https://arduino.esp8266.com/stable/package_esp8266com_index.json
   ```
3. Install **esp8266 by ESP8266 Community** from Boards Manager
4. Install **PubSubClient by Nick O'Leary** from Library Manager

### 2. Configure & Upload ESP8266 Code

Open `esp8266/intrusion_detection.ino` and update these values:

```cpp
const char* ssid      = "YOUR_WIFI_NAME";
const char* password  = "YOUR_WIFI_PASSWORD";
const char* mqttTopic = "myproject/intrusiondetection"; // must match Android app
```

Select **Tools → Board → NodeMCU 1.0 (ESP-12E Module)**, then upload with **Ctrl + U**.

### 3. Set Up the Android App

1. Open the `android/` folder in Android Studio
2. In `MainActivity.java`, confirm the topic matches:
   ```java
   private static final String MQTT_TOPIC = "myproject/intrusiondetection";
   ```
3. Enable **USB Debugging** on your phone
4. Click the ▶ Run button to deploy to your phone

---

## 📁 Project Structure

```
IoT-Intrusion-Detection/
│
├── esp8266/
│   └── intrusion_detection.ino      # Arduino code for NodeMCU
│
├── android/
│   ├── app/src/main/
│   │   ├── java/.../MainActivity.java   # Main app logic + MQTT client
│   │   ├── res/layout/activity_main.xml # Screen layout
│   │   └── AndroidManifest.xml          # App permissions & services
│   └── build.gradle                     # Dependencies
│
├── Hardware_and_Arduino_Setup.md    # Step-by-step hardware guide
├── IoT_Intrusion_Detection_Guide.md # Full beginner's guide
└── README.md                        # This file
```

---

## 🧪 Testing Without Hardware

You can test the Android app without any physical hardware using **MQTT Explorer**:

1. Download from http://mqtt-explorer.com
2. Connect to `broker.hivemq.com` on port `1883`
3. Publish a message:
   - **Topic:** `myproject/intrusiondetection`
   - **Message:** `Intrusion Detected`
4. Your Android app should immediately show the 🚨 alert

Alternatively, run the Python test script:

```python
pip install paho-mqtt
python test_mqtt.py
```

---

## 📱 App Features

- Connects to MQTT broker automatically on launch
- Shows **real-time connection status** (green = connected)
- Displays **timestamp** of last detected intrusion
- Fires an **Android push notification** when motion is detected
- Works even when the app is in the background

---

## 🔧 Troubleshooting

| Problem | Fix |
|---------|-----|
| No COM port in Arduino IDE | Install CP2102 driver from silabs.com |
| WiFi not connecting | Check ssid/password spelling in code |
| Sensor triggers immediately | Wait 30 seconds for calibration to complete |
| App shows "Connection Failed" | Try switching phone to mobile data |
| No notification on phone | Settings → Apps → Allow Notifications for this app |
| App receives no messages | Confirm topic string is identical in both ESP8266 and Android code |

---

## 📖 Based On

> *Building Arduino Projects for the Internet of Things* — Chapter 5: IoT Patterns: Realtime Clients
> by Adeel Javed (Apress, 2016)

**Key differences from the book:**
- Uses **NodeMCU ESP8266** instead of Arduino UNO + separate WiFi Shield
- Uses **broker.hivemq.com** instead of the deprecated iot.eclipse.org
- Android app modernised for **Android API 21+** with notification channels

---

## 📜 License

This project is for educational purposes.