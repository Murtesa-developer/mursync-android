# Mursync

Mursync is a lightweight synchronization tool designed to bridge the gap between your Android device and Linux desktop. It enables seamless state sharing and remote control features, focusing on productivity and convenience for GNOME and KDE Plasma users.

## 🚀 The Problem Mursync Solves

Managing notification states and desktop security across multiple devices can be tedious. Mursync addresses several common pain points:
*   **Split Focus**: Manually enabling "Do Not Disturb" on both your phone and PC during deep work.
*   **Security Gaps**: Forgetting to lock your desktop when stepping away, requiring you to walk back or leave it exposed.
*   **Camera Quality**: The need for a quick way to share your mobile screen or use it as a secondary visual source on your desktop without proprietary, heavy software.

## ✨ Features

### 1. Smart DND Sync
*   **Bi-directional Logic**: Automatically syncs your Android "Do Not Disturb" state with your Linux desktop.
*   **Manual Control**: Toggle desktop DND manually from the app interface or Quick Settings.
*   **Native Integration**: Supports GNOME and KDE Plasma notification systems.

### 2. Remote Desktop Control
*   **One-Tap Lock**: Securely lock your desktop screen from anywhere in your local network.
*   **Quick Settings Integration**: Lock your PC directly from the Android notification shade without opening the app.

### 3. High-Performance Screen Sharing
*   **Virtual Camera Support**: Streams your Android screen to the desktop using GStreamer and PipeWire, allowing it to be used as a virtual camera source.
*   **Optimized for Linux**: Designed specifically to work with modern Linux display pipelines.

### 4. Robust Connectivity
*   **Background Service**: A persistent foreground service ensures you stay connected even when the app is closed.
*   **Auto-Reconnect**: Intelligent heartbeat and reconnection logic to handle network shifts.
*   **Secure Pairing**: Token-based handshake ensures that only your authorized device can control your workstation.

### 5. Seamless Setup
*   **Device Discovery**: Built-in network scanner to find your desktop server automatically.
*   **Environment Detection**: Tailored configurations for GNOME and KDE.

## 🛠 Tech Stack

*   **Android**: Kotlin, Jetpack Core, Material Design 3, MediaProjection API.
*   **Desktop Server**: C++11, GTK3, GStreamer, PipeWire, Socket Programming.

## 📦 Installation & Setup

1.  **Desktop**: Compile and run the `mursync_server` on your Linux machine (requires `gtk+-3.0` and `gstreamer-1.0`).
2.  **Android**: Install the Mursync APK.
3.  **Pairing**: Open the app, select your Desktop Environment, and scan for your device. The first connection will establish a secure token pair.

---
*Created by Murtesa*
