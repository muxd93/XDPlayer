# 05 - API 接口文档

本项目涉及两类 API：
1. **TMDB API** - 项目作为客户端调用外部 TMDB REST API 获取影视元数据
2. **WebConfigServer REST API** - 项目作为服务端在 TV 端内嵌 HTTP 服务器，供局域网家属配置

## 5.1 TMDB API（客户端调用）

### 基础信息

| 项 | 值 |
|----|-----|
| Base URL | `https://api.themoviedb.org/3/` |
| 认证方式 | API Key（通过 `BuildConfig.TMDB_API_KEY` 注入） |
| 接口定义 | [TmdbApiService.kt](../app/src/main/java/org/mz/mzdkplayer/data/api/TmdbApiService.kt) |
| 创建器 | [TmdbServiceCreator.kt](../app/src/main/java/org/mz/mzdkplayer/data/api/TmdbServiceCreator.kt) |
| Repository | [TmdbRepository.kt](../app/src/main/java/org/mz/mzdkplayer/data/repository/TmdbRepository.kt) |

### 接口列表

#### 5.1.1 获取热门电影

```
GET /movie/popular
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| language | String | 否 | zh-CN | 语言 |
| page | Int | 否 | 1 | 页码 |

**返回**：`MovieListResponse`（含 `results: List<Movie>`）

#### 5.1.2 获取高评分电影

```
GET /movie/top_rated
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| language | String | 否 | en-US | 语言 |
| page | Int | 否 | 1 | 页码 |

#### 5.1.3 搜索电影

```
GET /search/movie
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| query | String | 是 | - | 搜索关键词 |
| language | String | 否 | zh-CN | 语言 |
| year | String | 否 | "" | 年份过滤 |
| page | Int | 否 | 1 | 页码 |

#### 5.1.4 搜索电视剧

```
GET /search/tv
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| query | String | 是 | - | 搜索关键词 |
| language | String | 否 | zh-CN | 语言 |
| year | String | 否 | "" | 年份过滤 |
| page | Int | 否 | 1 | 页码 |

#### 5.1.5 获取电影详情

```
GET /movie/{movieId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| movieId | Int | 是 | 电影 ID（路径参数） |
| language | String | 否 | 默认 zh-CN |

**返回**：`MovieDetails`

#### 5.1.6 获取电视剧详情

```
GET /tv/{seriesId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| seriesId | Int | 是 | 剧集 ID |
| language | String | 否 | 默认 zh-CN |

**返回**：`TVSeriesDetails`

#### 5.1.7 获取单集详情

```
GET /tv/{seriesId}/season/{seasonNumber}/episode/{episodeNumber}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| seriesId | Int | 是 | 剧集 ID |
| seasonNumber | Int | 是 | 季号 |
| episodeNumber | Int | 是 | 集号 |
| language | String | 否 | 默认 zh-CN |

**返回**：`TVEpisode`

### 统一返回封装

所有 TMDB 调用通过 `TmdbRepository.safeApiCall` 包装，返回 `Resource<T>`：

```kotlin
sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val exception: Exception? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
}
```

### 配置要求

在 `local.properties` 中配置 API Key：

```properties
TMDB_API_KEY=your_tmdb_api_key
```

> ⚠️ TMDB 在国内可能需要代理或修改 Hosts 才能稳定访问。

## 5.2 WebConfigServer REST API（服务端提供）

### 基础信息

| 项 | 值 |
|----|-----|
| 实现类 | [WebConfigServer.kt](../app/src/main/java/org/mz/mzdkplayer/tool/WebConfigServer.kt) |
| 框架 | NanoHTTPD |
| 端口 | 18080 |
| 绑定地址 | 0.0.0.0（局域网可访问） |
| 数据格式 | JSON |
| CORS | 全部允许（`Access-Control-Allow-Origin: *`） |
| 认证 | 无需认证（局域网内直接访问） |
| 启用条件 | `SettingsRepository.webConfigEnabled == true` |

### 统一响应格式

**成功**：
```json
{
  "ok": true,
  "data": { ... }
}
```

**失败**：
```json
{
  "ok": false,
  "error": "错误信息"
}
```

### 接口列表

#### 5.2.1 栏位管理

##### 获取所有栏位

```
GET /api/slots
```

**返回 data**：`List<HomeSlotEntity>`

##### 创建栏位

```
POST /api/slots
```

**Body**：HomeSlotEntity JSON

##### 更新栏位

```
PUT /api/slots/{id}
```

**Body**：HomeSlotEntity JSON

##### 删除栏位

```
DELETE /api/slots/{id}
```

##### 重排序栏位

```
PUT /api/slots/reorder
```

**Body**：
```json
{ "slotIds": [3, 1, 2, 4] }
```

##### 上传栏位缩略图

```
POST /api/slots/{id}/thumbnail
```

**Body**：multipart/form-data 图片文件

#### 5.2.2 文件夹视频管理

##### 获取文件夹内视频

```
GET /api/slots/{id}/videos
```

**返回 data**：`List<FolderVideoEntity>`

##### 添加视频到文件夹

```
POST /api/slots/{id}/videos
```

##### 删除文件夹内视频

```
DELETE /api/slots/{id}/videos/{vid}
```

##### 重排序文件夹视频

```
PUT /api/slots/{id}/videos/reorder
```

##### 触发文件夹扫描

```
POST /api/slots/{id}/scan
```

#### 5.2.3 SMB 连接管理

##### 获取 SMB 连接列表

```
GET /api/smb-connections
```

##### 添加 SMB 连接

```
POST /api/smb-connections
```

##### 更新 SMB 连接

```
PUT /api/smb-connections/{id}
```

##### 删除 SMB 连接

```
DELETE /api/smb-connections/{id}
```

#### 5.2.4 目录浏览

##### 浏览 SMB 目录

```
GET /api/browse/smb?connId={id}&path={path}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| connId | String | SMB 连接 ID |
| path | String | 目录路径 |

##### 浏览本地目录

```
GET /api/browse/local?path={path}
```

#### 5.2.5 已安装应用

```
GET /api/installed-apps
```

**返回 data**：`List<{ packageName, label }>`

#### 5.2.6 设置管理

##### 获取设置

```
GET /api/settings
```

**返回 data**：
```json
{
  "appMode": "elder",
  "slotCount": 15,
  "showRecent": true,
  "recentCount": 10,
  "webConfigEnabled": true
}
```

> 注：`slotCount`、`showRecent`、`recentCount` 为只读展示，分别由 TV 端和 `/api/elder-config` 管理。

##### 更新设置

```
PUT /api/settings
```

**Body**（仅可更新以下字段）：
```json
{
  "webConfigEnabled": true
}
```

> 注：`recentCount` 统一由 `/api/elder-config` 管理。`webConfigEnabled` 建议仅在 TV 端设置。

#### 5.2.7 老人模式配置

> **配置分工说明**：
> - **TV 端配置**（标准模式设置页）：`slotCount`、`showRecent`、`autoResume`、`stayOnPageAfterEnd`
> - **Web 端配置**（本接口）：`controlHideSeconds`、`seekStepSeconds`、`minSubtitleFontSize`、`hideDanmaku`、`hideNetworkSpeed`、`hideAdvancedControls`、`recentCount`

##### 获取老人模式配置

```
GET /api/elder-config
```

**返回 data**：`ElderModeConfig`
```json
{
  "controlHideSeconds": 3,
  "seekStepSeconds": 10,
  "autoResume": true,
  "stayOnPageAfterEnd": true,
  "minSubtitleFontSize": 36,
  "hideDanmaku": true,
  "hideNetworkSpeed": true,
  "hideAdvancedControls": true,
  "slotCount": 15,
  "showRecent": true,
  "recentCount": 10
}
```

##### 更新老人模式配置

```
PUT /api/elder-config
```

**Body**（部分更新，仅可更新以下字段，TV 端管理的字段不会被覆盖）：
```json
{
  "controlHideSeconds": 3,
  "seekStepSeconds": 10,
  "minSubtitleFontSize": 36,
  "hideDanmaku": true,
  "hideNetworkSpeed": true,
  "hideAdvancedControls": true,
  "recentCount": 10
}
```

#### 5.2.8 系统信息

##### 获取 TV 信息

```
GET /api/info
```

**返回 data**：
```json
{
  "ip": "192.168.1.100",
  "mode": "elder",
  "webConfigEnabled": true
}
```

##### 获取配置页面二维码

```
GET /api/qrcode
```

**返回**：二维码图片（PNG）

## 5.3 RemoteInputServer API

### 基础信息

| 项 | 值 |
|----|-----|
| 实现类 | [RemoteInputServer.kt](../app/src/main/java/org/mz/mzdkplayer/tool/RemoteInputServer.kt) |
| 端口 | 动态（构造时传入） |
| 用途 | 手机端远程输入 SMB 连接配置 |

### 接口

#### 获取输入页面

```
GET /
```

**返回**：HTML 页面（含表单）

#### 提交配置

```
POST /
```

**Body**（JSON）：
```json
{
  "ip": "192.168.1.100",
  "username": "admin",
  "password": "password",
  "port": "445",
  "shareName": "share",
  "aliasName": "我的NAS"
}
```

**返回**：`success` 或错误信息
