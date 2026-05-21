# MzDKPlayer - 安卓TV本地弹幕音视频播放器
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/mzhsy1/MzDKPlayer/total)

[English](README_en.md) | 中文
> GitHub https://github.com/mzhsy1/MzDKPlayer Gitee 镜像站点 https://gitee.com/mzhsy/MzDKPlayer

> MzDKPlayer 是一款专为安卓电视（Android TV）设计的本地音乐与视频播放器，支持弹幕功能、多种网络协议播放及音频视频格式播放。

---

## 功能特性

### 核心功能

- 🎬 **视频播放** - 支持多种视频格式的本地与网络协议播放
- 🎵 **音频播放** - 支持多种音频格式的本地与网络协议播放 歌词专辑封面显示与音乐信息，播放列表等常见功能
- 🖼️ **图片查看** - 支持多种图片格式的本地与网络协议查看
- 🏡 **媒体库**   - 包含电影/电视剧/音乐库，从TMDB获取电影电视剧信息
- 🕛 **历史记录** - 播放历史记录，包含音视频
- 🔍 **搜索功能** - 搜索电影/电视剧
- 💬 **弹幕功能** - 支持B站风格弹幕显示与自定义
- ⚙️ **设置功能** - 基本的应用与播放设置
- 🌐 **网络协议支持**：
  - ✅ SMB协议（当前已支持）
  - ✅ FTP协议（当前已支持）
  - ✅ WebDAV协议（当前已支持，其中如飞牛nas提供的局域网WebDAV服务只支持http,不支持https,其他公网如阿里云正常支持）
  - ✅ NFS协议（当前已支持）
  - ✅ HTTP协议（当前已支持 NGINX服务器）
- 🎚️ **多轨道选择** - 支持音轨、视频轨、字幕轨切换

### 🔊 播放进阶：音频直通 (Passthrough) 说明

MzDKPlayer 支持音频直通功能，可以将原始音频信号（源码）直接输出至功放、回音壁或支持多声道解码的电视，以获得影院级的听觉体验。

* **路径**：`设置` -> `音频设置` -> `音频透传 (Passthrough)`
* **适用范围**：**此开关仅对 VLC 播放引擎生效**。ExoPlayer 引擎会根据您的设备自动智能判断，无需手动干预。
* **使用建议**：
* **默认状态**：建议保持 **关闭 (Off)**。ExoPlayer 已能满足大部分设备的自动适配需求。
* **开启前提**：仅当您拥有外接功放或高端音频解码设备，且确定其支持所播放视频的音频编码格式（如 DTS-HD, TrueHD）时再开启。
* **故障排查**：若开启后播放时遇到**无声**情况，说明您的音频设备不支持当前视频的音轨格式（例如某些电视不支持 TrueHD 直通）。**此时请务必关闭该开关**，让播放器通过软件解码转换为 PCM 输出。

### 格式支持

#### 📺 视频格式 (Video)

* **常用封装**：MP4, MKV, MOV, AVI, WMV, FLV, WebM
* **蓝光/专业格式**：**ISO (蓝光原盘镜像)**, **M2TS**, **MTS**, TS, VOB
* **视频编码**：H.264 (AVC), **H.265 (HEVC)**, **AV1**, VP9, MPEG-2
* **特性支持**：4K/8K 超高清播放、HDR10/HLG、杜比视界 (Dolby Vision)

#### 🎵 音频格式 (Audio)

* **无损/高保真**：**FLAC**, WAV, ALAC (Apple Lossless)
* **通用格式**：MP3, AAC, OGG, Opus, WMA
* **影院级音轨**：**DTS**, **DTS-HD**, **TrueHD**, AC3 (Dolby Digital), E-AC3


#### 🖼️ 图片格式 (Image)

* **标准格式**：JPEG (JPG), PNG, WebP, BMP
* **现代格式**：HEIC / HEIF
* *注：暂不支持 Apple Live Photo。*

#### 💬 字幕支持 (Subtitles)

* **外挂字幕**：**SRT**, **ASS**, **SSA**, VTT
* **内嵌字幕**：MKV 内嵌字幕、**PGS (蓝光原盘字幕)**、DVB、Teletext

---

> ⚠️ **注意**：TMDB 在国内可能需要代理或修改 Hosts 才能稳定访问。

> 💡 小提示1：如果经常使用，建议在电视系统里把本播放器设为默认视频播放器，体验更顺滑。

> 💡 小提示2：如果设备性能不足，播放70，80G的原盘视频时开启弹幕可能会造成播放卡顿

> 💡 小提示3：如果遇到使用exoplayer（默认）播放器无法正常播放可以尝试在设置 播放与视频->默认播放器内核选择VLC播放器
---


## 应用演示

### 主界面与文件列表

![主界面截图](screenshots/Screenshot_20251223_175954.webp)
![主界面截图](screenshots/Screenshot_20251116_163213.webp)
![主界面截图](screenshots/Screenshot_20251223_174613.webp)
![主界面截图](screenshots/Screenshot_20251220_123857.webp)

### 播放界面与弹幕效果

![视频播放界面截图](screenshots/Screenshot_20251104_190350.webp)  
![视频播放界面截图](screenshots/Screenshot_20251104_190409.webp)
![音频播放界面截图](screenshots/Screenshot_20260126_182132.webp)

### 电影/电视剧详情页面

![电影详情页面截图](screenshots/Screenshot_20251220_112824.webp)
![电视剧详情页面截图](screenshots/Screenshot_20251220_112844.webp)

### 设置页面

![设置页面截图](screenshots/Screenshot_20251220_123916.webp)

---

## 技术架构

### 主要技术栈

- **媒体播放**：ExoPlayer + 自定义扩展
- **界面框架**：Jetpack Compose for TV
- **弹幕引擎**：AKDanmaku
- **字幕渲染**：ASS字幕渲染库
- **网络协议**：自定义SMB/FTP/WebDAV客户端实现

### 核心组件

- `VideoPlayerScreen` - 主播放器界面
- `BuilderMzPlayer` - 播放器构建与配置
- `AkDanmakuPlayer` - 弹幕播放组件
- `MovieDetailsScreen` / `TVSeriesDetailsScreen` - 电影/电视剧详情页面
- `FullDescriptionDialog` - 详细简介弹窗

---

## 硬件要求

### 推荐配置

- **芯片组**：Amlogic S928X-J
- **内存**：4GB RAM及以上
- **系统**：Android TV 11及以上

### 一般配置

- **芯片组**：MT9653或同等性能芯片
- **内存**：2GB RAM
- **系统**：Android TV 7及以上

### 最低要求

- **芯片组**：晶晨S905L或同等性能芯片
- **内存**：1GB RAM
- **系统**：Android TV 7及以上

> ⚠️ **注意**：代码写的烂，不会优化，能跑就成功，都是bug，设备性能不足可能导致视频与弹幕播放卡顿，或无法正常播放高码率视频

---

## 构建安装与使用

### 构建要求

- Android Studio 当下最新版本即可
- Android SDK 36+
- Java 17

### 构建步骤

1. 克隆项目到本地
2. 使用Android Studio打开项目
3. 连接支持ADB调试的安卓TV设备
4. 构建并运行应用

### 基本使用

1. 主界面选择视频文件（本地或网络）播放器会自动选择同目录下相同文件名的xml弹幕文件
2. 播放界面使用遥控器控制：
   - 左右键：快进/快退
   - 确认键：暂停/播放
   - 菜单键：显示控制界面
   - 遥控器上键弹幕设置，下键音轨选择
3. 点击视频文件可查看电影/电视剧详情（包括海报、简介、评分、年份、国家、类型等信息）

---

## 项目状态

⚠️ **开发阶段**：初始阶段，存在已知Bug

### 近期开发计划

- [x] FTP协议支持
- [x] WebDAV协议支持
- [x] NFS协议支持
- [x] 音频文件，图片文件支持
- [x] 播放列表管理
- [x] 电影/电视剧详情页面
- [ ] 网络弹幕加载功能
- [ ] 设置界面优化

---

## 贡献

欢迎提交 Issue 和 Pull Request 来帮助改进这个项目。尤其欢迎对   **播放器稳定性** 的贡献！

---

## 免责声明

本软件仅供学习交流使用，请勿用于商业用途。使用本软件造成的任何问题，开发者不承担相关责任。

---

**注意**：杜比视界、杜比全景声、DTS-HD等功能的正常使用需要设备硬件支持，部分功能可能需要特定的音频视频设备才能获得最佳体验。
