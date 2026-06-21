#!/usr/bin/env bash
#
# MzDKPlayer 编译脚本
# 支持自定义构建选项
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR"

# ── 默认值 ──────────────────────────────────────────────
BUILD_TYPE="release"
ABI_FILTER="arm64"        # all | arm64 | arm32 | x86
SPLIT_ABI=false
OUTPUT_DIR=""
VERSION_NAME=""
VERSION_CODE=""
SIGN_CONFIG=""            # 空=默认签名 | skip=不签名
EXTRA_ARGS=""

# 调试签名 keystore 默认配置
KEYSTORE_NAME="debug.keystore"
KEYSTORE_ALIAS="mzdk-debug"
KEYSTORE_PASSWORD="mzdk123456"
KEY_PASSWORD="mzdk123456"
KEYSTORE_VALIDITY=36500    # 100年

# ── 颜色 ────────────────────────────────────────────────
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

info()  { echo -e "${CYAN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; }

# ── 帮助 ────────────────────────────────────────────────
usage() {
    cat <<'EOF'
MzDKPlayer 编译脚本

用法: ./build.sh [选项]

选项:
  -t, --type TYPE         构建类型: debug | release (默认: release)
  -a, --abi ABI           目标架构: all | arm64 | arm32 | x86 (默认: all)
  -s, --split             启用 ABI 拆分 (按架构生成独立 APK)
  -o, --output DIR        输出目录 (默认: app/build/outputs/apk)
  -v, --version-name VER  覆盖版本名 (如 1.16.0)
  -c, --version-code NUM  覆盖版本号 (如 100)
  -n, --no-sign           跳过签名 (仅 debug 有效)
  -h, --help              显示帮助

示例:
  ./build.sh                                    # 默认 release 全架构
  ./build.sh -t debug -a arm64                  # debug 仅 arm64
  ./build.sh -t release -a arm64 -s             # release 拆分 APK
  ./build.sh -t release -v 2.0.0 -c 100         # 自定义版本号
  ./build.sh -t release -a arm64 -o ~/apks      # 指定输出目录
EOF
    exit 0
}

# ── 参数解析 ────────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        -t|--type)         BUILD_TYPE="$2"; shift 2 ;;
        -a|--abi)          ABI_FILTER="$2"; shift 2 ;;
        -s|--split)        SPLIT_ABI=true; shift ;;
        -o|--output)       OUTPUT_DIR="$2"; shift 2 ;;
        -v|--version-name) VERSION_NAME="$2"; shift 2 ;;
        -c|--version-code) VERSION_CODE="$2"; shift 2 ;;
        -n|--no-sign)      SIGN_CONFIG="skip"; shift ;;
        -h|--help)         usage ;;
        *)                 error "未知选项: $1"; usage ;;
    esac
done

# ── 校验 ────────────────────────────────────────────────
if [[ "$BUILD_TYPE" != "debug" && "$BUILD_TYPE" != "release" ]]; then
    error "构建类型必须是 debug 或 release"
    exit 1
fi

if [[ "$ABI_FILTER" != "all" && "$ABI_FILTER" != "arm64" && "$ABI_FILTER" != "arm32" && "$ABI_FILTER" != "x86" ]]; then
    error "ABI 必须是 all | arm64 | arm32 | x86"
    exit 1
fi

if [[ "$SIGN_CONFIG" == "skip" && "$BUILD_TYPE" == "release" ]]; then
    warn "release 构建不支持跳过签名，忽略 --no-sign"
    SIGN_CONFIG=""
fi

# ── 构建 Gradle 参数 ────────────────────────────────────
GRADLE_TASK="assemble${BUILD_TYPE^}"
GRADLE_ARGS=()

# ABI 过滤
if [[ "$ABI_FILTER" != "all" ]]; then
    case "$ABI_FILTER" in
        arm64) GRADLE_ARGS+=("-PtargetAbi=arm64") ;;
        arm32) GRADLE_ARGS+=("-PtargetAbi=arm32") ;;
        x86)   GRADLE_ARGS+=("-PtargetAbi=x86") ;;
    esac
fi

# ABI 拆分
if [[ "$SPLIT_ABI" == true ]]; then
    GRADLE_ARGS+=("-Pandroid.injected.split.enabled=true")
fi

# 版本号覆盖
if [[ -n "$VERSION_NAME" ]]; then
    GRADLE_ARGS+=("-PversionName=$VERSION_NAME")
fi
if [[ -n "$VERSION_CODE" ]]; then
    GRADLE_ARGS+=("-PversionCode=$VERSION_CODE")
fi

# ── 打印构建信息 ────────────────────────────────────────
echo ""
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info " MzDKPlayer 构建配置"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
info " 构建类型:  $BUILD_TYPE"
info " 目标架构:  $ABI_FILTER"
info " ABI 拆分:  $SPLIT_ABI"
[[ -n "$VERSION_NAME" ]] && info " 版本名:    $VERSION_NAME"
[[ -n "$VERSION_CODE" ]] && info " 版本号:    $VERSION_CODE"
[[ -n "$OUTPUT_DIR" ]]   && info " 输出目录:  $OUTPUT_DIR"
info "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# ── 环境修正 ────────────────────────────────────────────
# AGP 9.x 要求 ANDROID_SDK_HOME 指向偏好设置目录的父目录（如 $HOME），
# 而非 SDK 路径本身。SDK 路径由 ANDROID_HOME 或 local.properties 指定。
if [[ -n "${ANDROID_SDK_HOME:-}" && "${ANDROID_SDK_HOME}" == "${ANDROID_HOME:-}" ]]; then
    export ANDROID_SDK_HOME="$HOME"
fi

# ── 签名配置 ────────────────────────────────────────────
LOCAL_PROPS="$PROJECT_DIR/local.properties"
KEYSTORE_PATH="$PROJECT_DIR/$KEYSTORE_NAME"

ensure_keystore() {
    # 检查 local.properties 中是否已有签名配置
    if [[ -f "$LOCAL_PROPS" ]]; then
        local existing_store
        existing_store=$(grep -E "^STORE_FILE=" "$LOCAL_PROPS" 2>/dev/null | cut -d'=' -f2- || true)
        if [[ -n "$existing_store" && -f "$existing_store" ]]; then
            info "使用已有签名配置: $existing_store"
            return 0
        fi
    fi

    # 自动生成调试 keystore
    if [[ ! -f "$KEYSTORE_PATH" ]]; then
        info "未找到签名 keystore，自动生成调试签名..."
        keytool -genkeypair \
            -keystore "$KEYSTORE_PATH" \
            -alias "$KEYSTORE_ALIAS" \
            -storepass "$KEYSTORE_PASSWORD" \
            -keypass "$KEY_PASSWORD" \
            -keyalg RSA \
            -keysize 2048 \
            -validity "$KEYSTORE_VALIDITY" \
            -dname "CN=MzDKPlayer, OU=Dev, O=mz, L=Beijing, ST=Beijing, C=CN" \
            2>&1 || {
                error "生成 keystore 失败，请检查 keytool 是否可用"
                exit 1
            }
        info "调试 keystore 已生成: $KEYSTORE_PATH"
    else
        info "使用已有调试 keystore: $KEYSTORE_PATH"
    fi

    # 写入 local.properties
    {
        echo ""
        echo "# 自动生成的调试签名配置"
        echo "STORE_FILE=$KEYSTORE_PATH"
        echo "STORE_PASSWORD=$KEYSTORE_PASSWORD"
        echo "KEY_ALIAS=$KEYSTORE_ALIAS"
        echo "KEY_PASSWORD=$KEY_PASSWORD"
    } >> "$LOCAL_PROPS"
    info "签名配置已写入 local.properties"
}

# release 构建时确保有签名配置
if [[ "$BUILD_TYPE" == "release" && "$SIGN_CONFIG" != "skip" ]]; then
    ensure_keystore
fi

# ── 执行构建 ────────────────────────────────────────────
info "开始构建..."
cd "$PROJECT_DIR"

if ! ./gradlew "$GRADLE_TASK" "${GRADLE_ARGS[@]}" --no-daemon; then
    error "构建失败!"
    exit 1
fi

# ── 收集产物 ────────────────────────────────────────────
APK_SOURCE_DIR="$PROJECT_DIR/app/build/outputs/apk/$BUILD_TYPE"

if [[ ! -d "$APK_SOURCE_DIR" ]]; then
    error "未找到 APK 输出目录: $APK_SOURCE_DIR"
    exit 1
fi

APK_COUNT=$(find "$APK_SOURCE_DIR" -name "*.apk" | wc -l)
if [[ "$APK_COUNT" -eq 0 ]]; then
    error "未找到 APK 文件"
    exit 1
fi

# 复制到自定义输出目录
if [[ -n "$OUTPUT_DIR" ]]; then
    mkdir -p "$OUTPUT_DIR"
    find "$APK_SOURCE_DIR" -name "*.apk" -exec cp -v {} "$OUTPUT_DIR/" \;
    info "APK 已复制到: $OUTPUT_DIR"
    echo ""
    ls -lh "$OUTPUT_DIR"/*.apk 2>/dev/null
else
    info "APK 输出目录: $APK_SOURCE_DIR"
    echo ""
    find "$APK_SOURCE_DIR" -name "*.apk" -exec ls -lh {} \;
fi

echo ""
info "构建完成! 共 $APK_COUNT 个 APK"
