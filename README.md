# Tracking Extension

![Java Version](https://img.shields.io/badge/Java-21-orange)
![Target](https://img.shields.io/badge/Target-Paper-blue)
![Typewriter](https://img.shields.io/badge/Typewriter-0.9.0--beta--177-purple)

**Tracking Extension** is a player movement recorder for **TypeWriter**, built for **BTC Studio** infrastructure. It stores sessions of player positions in an artifact and can draw them on BlueMap.

---

## 🚀 Key Features

- **Session recording**: positions are recorded at a fixed interval and stored in a Typewriter artifact.
- **BlueMap integration**: individual points and connected path lines, each switchable.
- **In-game inspection**: replay a player's session to an administrator with particle markers.

---

## 🧩 Available Entries

| Type | Entry | Role |
|---|---|---|
| Manifest | `tracking_manifest` | `storage`, `pointIntervalSeconds` (default 20), `trackingEnabled`, `blueMapEnabled`, `showBlueMapPoints`, `showBlueMapPath` |
| Artifact | `tracking_artifact` | Stores the tracking sessions and points |

## ⌨️ Commands and Permissions

`/tracking` (permission `typewriter.tracking.inspect`, players only):

- `/tracking inspect <player> [session]`: without a session, lists the player's sessions; with one, starts the inspection.
- `/tracking sessions <player>`: lists the player's sessions.
- `/tracking`: stops the inspection in progress.

---

## 📋 Requirements

- **Typewriter** `0.9.0-beta-177` (with PacketEvents, which Typewriter already requires; the inspection particles are sent through it)
- **Paper** (no Folia support is promised)
- Optional: BlueMap, for the map layers

## ⚙️ Configuration

Everything is configured from the Typewriter web panel through the `tracking_manifest` entry.

## 🛠 Building & Deployment

Requires **Java 21**.

```bash
# Clone the repository
git clone https://github.com/RenaudRl/Typewriter-TrackingExtension.git
cd Typewriter-TrackingExtension

# Build the project
./gradlew clean build
```

Output: `build/libs/TrackingExtension-<version>.jar`

---

## 🤝 Credits & Inspiration
- **[TypeWriter](https://github.com/gabber235/Typewriter)** - The engine this extension is built for.
- **[BTC Studio](https://github.com/RenaudRl)** - Maintenance and specialized optimizations.

## 📖 Documentation

Full documentation: [BTC Studio Docs](https://docs.borntocraftstudio.net/extensions/free/tracking/).

---

## 📜 Licence

**GNU General Public License v3.0 or later** — [LICENSE](LICENSE) — with a
**linking exception** for the Typewriter engine — [LICENSE-EXCEPTION.md](LICENSE-EXCEPTION.md).

| | |
|---|---|
| You may | Run it anywhere, **including on a monetised server**. Study it, modify it, use it as a base, and redistribute it — **even for a fee**. GPLv3 §4 explicitly allows charging for a copy. |
| You must | Publish the complete corresponding source of your version under GPLv3, preserve the copyright notices, and **state that you modified it and when** (§5(a)). |
| You may not | Ship a closed-source or proprietary version, relicense under stricter terms, or strip the attribution and present this work as your own — §8 terminates your rights automatically. |
| Marks | **"Born To Craft"** and **"BTC Studio"** are **not** covered by the GPL. Fork it freely, sell your fork if you like — but **rebrand it**. |

> Reselling this code is legally allowed and practically pointless: whoever buys a
> copy from you receives, under the GPL, the right to redistribute it for free.
> That is the protection — not a clause forbidding sale, which the GPL does not
> permit us to add.

### About Typewriter

This is a **third-party extension**. It uses the public extension API of the
[Typewriter](https://github.com/gabber235/Typewriter) engine by gabber235 and
contains none of its source. Born To Craft Studio is not affiliated with or
endorsed by the Typewriter project.

The engine itself is **not** free software — its licence forbids redistributing
it. **Get it from the Typewriter project, and never redistribute it**, including
inside a fork of this repository.

Full attribution, the statement of modifications required by §5(a), and the
trademark reservation are in **[NOTICE.md](NOTICE.md)**. Read it before
redistributing.

© 2026 Born To Craft Studio.
