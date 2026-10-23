# Constituency Leader & PA Portal

A robust, real-time Android constituency management application built with Kotlin and Jetpack Compose. Designed for leaders and Personal Assistants (PAs) to efficiently track, manage, and synchronize constituency requests and tickets in real time.

---

## 🌟 Key Features

- **Secure Authentication**: Google Sign-In via Jetpack Credential Manager.
- **Ticket Portal**: Create, search, update, and permanently/locally manage constituency tickets and requests.
- **PA Credential Management**: Add, view, and manage Personal Assistant accounts and profiles with isolated role-based permissions.
- **Real-Time Cloud & Local Persistence**: Dual-layer architecture combining offline-first Room database with real-time Firebase Firestore synchronization.
- **Material Design 3 (M3)**: Modern UI featuring dynamic color support, adaptive layouts, custom vector iconography, and dark/light theme support.

---

## 🛠️ Tech Stack & Architecture

- **Language**: Kotlin 100%
- **UI Framework**: Jetpack Compose & Material 3
- **Architecture**: MVVM (Model-View-ViewModel) with Clean Architecture principles
- **Local Storage**: Room Database (SQLite)
- **Cloud Backend & Auth**: Firebase Firestore & Firebase Authentication
- **Asynchrony**: Kotlin Coroutines & Flow

---

## 📂 Project Structure

```
app/src/main/java/com/example/
├── data/
│   ├── local/        # Room database, DAOs, and entities
│   ├── model/        # Data models and domain entities
│   ├── remote/       # Firebase Firestore sync services
│   └── repository/   # Repository layer coordinating local & remote data
├── ui/
│   ├── screens/      # Jetpack Compose screens & dialogs (LeaderDashboard, TicketsTab, etc.)
│   ├── theme/        # Material 3 ColorScheme, Typography, and Shapes
│   └── viewmodel/    # LeaderViewModel & state management
└── MainActivity.kt   # Entry point and navigation host
```

---

## 🚀 Setup & Build Instructions

1. **Prerequisites**:
   - Android Studio Koala or newer / Gradle (Kotlin DSL).
   - Android SDK (compileSdk 36, minSdk 24).
2. **Configuration**:
   - Ensure Firebase configuration is linked (`google-services.json` or automated provisioning).
3. **Building & Running**:
   - Run Clean and Rebuild in Android Studio, or build via Gradle:
     ```bash
     gradle assembleDebug
     ```

---

## 📄 License

This project is proprietary and built for constituency leadership management.
