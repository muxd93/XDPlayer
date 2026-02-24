# MzDKPlayer - Android TV Local Media Player with Danmaku Support

> GitHub: [https://github.com/mzhsy1/MzDKPlayer](https://github.com/mzhsy1/MzDKPlayer)
> Gitee Mirror: [https://gitee.com/mzhsy/MzDKPlayer](https://gitee.com/mzhsy/MzDKPlayer)

**MzDKPlayer** is a local music and video player specifically designed for Android TV. It supports danmaku (bullet chat), various network protocols, and a wide range of audio/video formats.

---

## Features

### Core Functions

* 🎬 **Video Playback** - Supports local and network protocol playback for multiple video formats.
* 🎵 **Audio Playback** - Supports local/network audio, lyrics display, album art, music info, and playlist management.
* 🖼️ **Image Viewer** - View local and network images in various formats.
* 🏡 **Media Library** - Includes Movie, TV Series, and Music libraries with metadata fetched from TMDB.
* 🕛 **History** - Playback history for both audio and video.
* 🔍 **Search** - Quickly find movies and TV shows.
* 💬 **Danmaku** - Bilibili-style danmaku display with customization options.
* ⚙️ **Settings** - Basic application and playback configurations.
* 🌐 **Network Protocols**:
* ✅ **SMB** (Supported)
* ✅ **FTP** (Supported)
* ✅ **WebDAV** (Supported; Note: Some local WebDAV services like PNnas only support HTTP, while public clouds like Aliyun support HTTPS).
* ✅ **NFS** (Supported)
* ✅ **HTTP** (Supported via NGINX servers)


* 🎚️ **Multi-track Selection** - Support for switching audio tracks, video tracks, and subtitle tracks.

### Advanced Encoding Support

* **Video Encoding**:
* Dolby Vision Hardware Decoding
* HDR Video Hardware Decoding


* **Audio Encoding**:
* Full Dolby Audio Passthrough (Requires hardware support)
* Full DTS Audio Passthrough (Requires hardware support)


* **Subtitles**:
* ASS/SSA format support
* SRT and other common subtitle formats



### Movie & TV Show Details (New ✨)

When a video file is clicked, the app automatically parses the title and year to fetch detailed information from TMDB:

* Supports both Movies and TV Series.
* **Movie Details**: Displays posters, ratings, synopsis, release year, country, genres, etc.
* **TV Show Details**: Displays posters, ratings, synopsis, total seasons/episodes, and current episode info.
* **Filename Cleaning**: Optimized Chinese matching (e.g., `Avatar.2009.mkv` → "Avatar 2009").
* **Graceful Degradation**: Reverts to default icons and original filenames during network issues.

> ⚠️ **Note**: TMDB access may require a proxy or Hosts modification in certain regions.

### External App Integration (New ✨)

* 🚀 **Third-party Invocation** - Open videos directly from apps like Xunlei, Aliyun Drive, Baidu Netdisk, ES File Explorer, or Chrome.
* 🔌 **Dolby Vision + Atmos Support** - External calls still benefit from full hardware decoding and Atmos flags (hardware permitting).
* 📡 **Versatile Link Handling** - Supports Web links (http/https), local files (file://), and shared content (content://).
* ⚠️ **Danmaku Limitation** - Danmaku is **automatically disabled** when triggered from external apps because external apps usually only pass the video link, not the directory path. Please use the internal file browser for danmaku support.

---

## Technical Architecture

### Tech Stack

* **Media Playback**: ExoPlayer + Custom Extensions
* **UI Framework**: Jetpack Compose for TV
* **Danmaku Engine**: AKDanmaku
* **Subtitle Rendering**: Libass-based rendering
* **Network Protocols**: Custom implementations for SMB, FTP, WebDAV, and NFS

### Core Components

* `VideoPlayerScreen`: Main playback interface.
* `BuilderMzPlayer`: Player construction and configuration.
* `AkDanmakuPlayer`: Danmaku playback component.
* `MovieDetailsScreen` / `TVSeriesDetailsScreen`: Metadata display pages.

---

## Hardware Requirements

### Recommended

* **Chipset**: Amlogic S928X-J
* **RAM**: 4GB or higher
* **OS**: Android TV 11+

### General

* **Chipset**: MT9653 or equivalent
* **RAM**: 2GB
* **OS**: Android TV 7+

### Minimum

* **Chipset**: Amlogic S905L or equivalent
* **RAM**: 1GB
* **OS**: Android TV 7+

> ⚠️ **Disclaimer**: The code is currently unoptimized. If it runs, it's a success. Bugs are expected. Underpowered hardware may cause stuttering when playing high-bitrate videos with danmaku enabled.

---

## Build & Usage

### Build Requirements

* Latest Android Studio
* Android SDK 36+
* Java 17

### Build Steps

1. Clone the repository.
2. Open the project in Android Studio.
3. Connect an Android TV device with ADB debugging enabled.
4. Build and Run.

### Remote Control Guide

* **Left/Right**: Seek backward/forward.
* **Center (OK)**: Pause/Play.
* **Menu**: Show control interface.
* **Up**: Danmaku settings.
* **Down**: Audio track selection.

---

## Project Status

⚠️ **Development Phase**: Early stage, known bugs exist.

### Roadmap

* [x] FTP / WebDAV / NFS Support
* [x] Audio and Image file support
* [x] Playlist management
* [x] Movie/TV Detail pages
* [ ] Online Danmaku loading
* [ ] Settings UI optimization

---

## Contribution

Issues and Pull Requests are welcome. Contributions regarding **playback stability** are especially appreciated!

---

## Disclaimer

This software is for educational and exchange purposes only. Do not use it for commercial purposes. The developer is not responsible for any issues caused by the use of this software.

---
