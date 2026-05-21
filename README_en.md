# MzDKPlayer - Android TV Local Danmaku Media Player
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/mzhsy1/MzDKPlayer/total)

中文 | [English](https://www.google.com/search?q=README_en.md)

> GitHub: [https://github.com/mzhsy1/MzDKPlayer](https://github.com/mzhsy1/MzDKPlayer)
> Gitee Mirror: [https://gitee.com/mzhsy/MzDKPlayer](https://gitee.com/mzhsy/MzDKPlayer)

**MzDKPlayer** is a local music and video player specifically designed for **Android TV**. It features Danmaku (bullet comment) support, various network protocol integration, and wide-ranging audio/video format compatibility.

---

## Features

### Core Capabilities

* 🎬 **Video Playback** - Supports local and network streaming for various video formats.
* 🎵 **Audio Playback** - High-fidelity playback with lyrics, album art, metadata display, and playlist management.
* 🖼️ **Image Viewer** - Browse local and network images.
* 🏡 **Media Library** - Integrated Movies/TV Shows/Music libraries with automated metadata fetching from **TMDB**.
* 🕛 **History** - Keep track of your recently played videos and audio.
* 🔍 **Search** - Quickly find movies or TV series in your library.
* 💬 **Danmaku Support** - Bilibili-style danmaku display with deep customization options.
* ⚙️ **Settings** - Flexible configuration for the app and playback engine.
* 🌐 **Network Protocols**:
* ✅ **SMB** (Supported)
* ✅ **FTP** (Supported)
* ✅ **WebDAV** (Supported; Note: LAN services like freenas/feiniu may only support HTTP, while public clouds like Aliyun support HTTPS).
* ✅ **NFS** (Supported)
* ✅ **HTTP** (Supported via NGINX servers)
* 🎚️ **Track Switching** - Seamless switching between audio tracks, video tracks, and subtitle tracks.

### 🔊 Advanced Playback: Audio Passthrough

MzDKPlayer supports Audio Passthrough, allowing raw audio signals to be sent directly to your AV receiver, soundbar, or compatible TV for a cinema-grade experience.

* **Settings Path**: `Settings` -> `Audio Settings` -> `Audio Passthrough`
* **Compatibility**: **This setting applies ONLY to the VLC playback engine.** The ExoPlayer engine intelligently manages audio output based on device capabilities automatically, requiring no manual intervention.
* **Recommendations**:
* **Default Status**: Recommended to keep **OFF**. ExoPlayer provides automatic adaptation for most devices.
* **Prerequisites**: Only enable this if you have an external receiver or high-end decoding hardware and are certain it supports the specific audio format (e.g., DTS-HD, TrueHD).
* **Troubleshooting**: If you experience **no sound** after enabling this, your hardware may not support the specific audio format (e.g., some TVs do not support TrueHD passthrough). **Please turn off this setting** to allow the player to decode the audio into PCM.

### Format Support

#### 📺 Video

* **Containers**: MP4, MKV, MOV, AVI, WMV, FLV, WebM
* **Blu-ray/Pro**: **ISO (Blu-ray Image)**, **M2TS**, **MTS**, TS, VOB
* **Codecs**: H.264 (AVC), **H.265 (HEVC)**, **AV1**, VP9, MPEG-2
* **Features**: 4K/8K UHD, HDR10/HLG, **Dolby Vision**

#### 🎵 Audio

* **Lossless/Hi-Fi**: **FLAC**, WAV, ALAC (Apple Lossless)
* **General**: MP3, AAC, OGG, Opus, WMA
* **Cinema Grade**: **DTS**, **DTS-HD**, **TrueHD**, AC3 (Dolby Digital), E-AC3

#### 🖼️ Image

* **Standard**: JPEG (JPG), PNG, WebP, BMP
* **Modern**: HEIC / HEIF
* *Note: Apple Live Photos are currently not supported.*

#### 💬 Subtitles

* **External**: **SRT**, **ASS**, **SSA**, VTT
* **Embedded**: MKV Internal, **PGS (Blu-ray)**, DVB, Teletext

---

> ⚠️ **Note**: Accessing TMDB in certain regions may require a proxy or Host modification for stability.

> 💡 **Tip 1**: For the best experience, set MzDKPlayer as your system's default video player.

> 💡 **Tip 2**: If your device has limited performance, enabling Danmaku while playing 70-80GB Blu-ray files may cause lag.

> 💡 **Tip 3**: If a video fails to play using the default ExoPlayer engine, try switching to the **VLC player engine** in `Settings` -> `Playback & Video` -> `Default Player Engine`.



---

## App Preview

*(Screenshots omitted in this text, please refer to the Chinese README for visuals)*

---

## Technical Architecture

### Tech Stack

* **Media Engine**: ExoPlayer + Custom Extensions
* **UI Framework**: Jetpack Compose for TV
* **Danmaku Engine**: AKDanmaku
* **Subtitle Rendering**: ASS Subtitle Library
* **Networking**: Custom implementations for SMB/FTP/WebDAV/NFS

### Key Components

* `VideoPlayerScreen` - Main playback interface
* `BuilderMzPlayer` - Player construction and configuration
* `AkDanmakuPlayer` - Danmaku logic handler
* `MovieDetailsScreen` / `TVSeriesDetailsScreen` - TMDB-powered detail pages

---

## Hardware Requirements

### Recommended

* **SoC**: Amlogic S928X-J
* **RAM**: 4GB+
* **OS**: Android TV 11+

### Balanced

* **SoC**: MT9653 or equivalent
* **RAM**: 2GB
* **OS**: Android TV 7+

### Minimum

* **SoC**: Amlogic S905L or equivalent
* **RAM**: 1GB
* **OS**: Android TV 7+

> ⚠️ **Disclaimer**: The code is a work in progress (and honestly a bit messy). Expect bugs. If your hardware is weak, high-bitrate videos or danmaku might stutter.

---

## Build & Usage

### Build Requirements

* Android Studio (Latest Version)
* Android SDK 36+
* Java 17

### Build Steps

1. Clone the repository.
2. Open with Android Studio.
3. Connect your Android TV via ADB.
4. Build and Run.

### Remote Control Shortcuts

* **Left/Right**: Seek backward/forward
* **Center/OK**: Play/Pause
* **Menu**: Show control panel
* **Up**: Danmaku settings
* **Down**: Audio track selection

---

## Roadmap

⚠️ **Status**: Early Development (Alpha)

* [x] FTP / WebDAV / NFS Support
* [x] Audio and Image file support
* [x] Playlist management
* [x] Movie/TV Show detail pages
* [ ] **Online Danmaku fetching**
* [ ] Settings UI optimization

---

## Contributing

Issues and Pull Requests are more than welcome! We especially appreciate help with **playback stability** and optimization.

---

## License

For educational and exchange purposes only. Do not use for commercial purposes. The developer is not responsible for any issues arising from the use of this software.

---

**Note**: Features like Dolby Vision, Atmos, and DTS-HD require compatible hardware.

