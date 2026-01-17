# Tracking Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![Target](https://img.shields.io/badge/Target-Paper%20/%20Folia%20/%20TypeWriter-blue)

**Tracking Extension** is a player monitoring and analytics tool for **TypeWriter**, engineered for **BTC Studio** infrastructure. It records player movements and integrates with mapping tools like BlueMap.

---

## 🚀 Key Features

### 🗺️ Visualization & Analytics
- **BlueMap Integration**: Visualize player paths and sessions directly on the web map.
- **Session Recording**: Store player movement data in organized sessions using TypeWriter Artifacts.

### 👮 Admin Tools
- **Particle Tracking**: Visual tracking markers for administrators in-game.
- **Inspection Commands**: Real-time inspection of player history and status.

---

## ⚙️ Configuration

Tracking Extension configuration is managed via TypeWriter's manifest system.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/Typewriter-TrackingExtension.git
cd Typewriter-TrackingExtension

# Build the project
./gradlew clean build
```

### Artifact Locations:
- `build/libs/Tracking-[Version].jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

---

## 📜 License
Licensed under the **MIT License**.
