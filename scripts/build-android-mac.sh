#!/usr/bin/env bash
#
# scripts/build-android-mac.sh
# 在 macOS 上打包 Android 安装包（APK），输出到 releases/android/。
#
# 用法：
#   bash scripts/build-android-mac.sh          # 默认 debug（可直接 adb install）
#   bash scripts/build-android-mac.sh debug    # 同上
#   bash scripts/build-android-mac.sh release  # release 包（需先配置签名，见下）
#
# 产物：releases/android/ebook-{version}-{type}-{YYYYMMDD-HHMM}.apk + .sha256
#
# 兼容 macOS 自带 bash 3.2（不使用 ${VAR^} 等 4.0+ 语法）。
#
# release 签名说明：
#   项目默认只配了 debug 签名。要打可直接安装的 release 包，需在 app/build.gradle.kts
#   配 signingConfigs.release（storeFile / keyAlias / storePassword / keyPassword）。
#   未配签名时 assembleRelease 产出 app-release-unsigned.apk（不可直装，脚本会告警）。

set -euo pipefail

# ===== 定位仓库根（本脚本在 scripts/ 下，根是其上一级）=====
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

# ===== 构建类型 → Gradle task =====
BUILD_TYPE="${1:-debug}"
case "$BUILD_TYPE" in
  debug)   TASK="assembleDebug" ;;
  release) TASK="assembleRelease" ;;
  *) echo "用法：$0 [debug|release]" >&2; exit 1 ;;
esac

# ===== 版本号（读 app/build.gradle.kts 的 versionName）=====
VERSION="$(grep -E '^[[:space:]]*versionName' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
[[ -z "$VERSION" ]] && VERSION="unknown"

# ===== 编译 =====
echo "▶ 编译 ${BUILD_TYPE} APK（versionName=${VERSION}）..."
./gradlew "$TASK" -q

# ===== 定位产物 APK =====
APK_DIR="app/build/outputs/apk/${BUILD_TYPE}"
APK_FILE="$(ls "$APK_DIR"/*.apk 2>/dev/null | head -1 || true)"
if [[ -z "$APK_FILE" ]]; then
  echo "✗ 未找到产物 APK（$APK_DIR/*.apk）" >&2
  exit 1
fi

# release 无签名时产物名含 unsigned —— 告警（不中断，仍复制出来供排查）
if [[ "$APK_FILE" == *unsigned* ]]; then
  echo "⚠ 产物为 unsigned（$(basename "$APK_FILE")）—— 未配置 release 签名，不可直接安装。"
  echo "  配 signingConfigs.release 后重试，或用：$0 debug"
fi

# ===== 输出到 releases/android/ =====
OUT_DIR="releases/android"
mkdir -p "$OUT_DIR"
STAMP="$(date +%Y%m%d-%H%M)"
OUT_NAME="ebook-${VERSION}-${BUILD_TYPE}-${STAMP}.apk"
OUT_PATH="${OUT_DIR}/${OUT_NAME}"
cp "$APK_FILE" "$OUT_PATH"

# sha256 校验文件（releases 惯例，便于核对完整性）
shasum -a 256 "$OUT_PATH" | awk '{print $1}' > "${OUT_PATH}.sha256"

echo
echo "✓ 打包完成"
echo "  APK    : ${OUT_PATH}"
echo "  大小   : $(du -h "$OUT_PATH" | awk '{print $1}')"
echo "  sha256 : $(cat "${OUT_PATH}.sha256")"
if [[ "$BUILD_TYPE" == "debug" ]]; then
  echo "  安装   : adb install -r \"${OUT_PATH}\""
fi
