#!/usr/bin/env bash
# REL-05 性能基线 —— 公共库（被其余脚本 source）。
# 仅定义函数与变量，不 set 选项（由调用方 set -euo pipefail）。

PKG="com.xuziyue.ebook"
ACTIVITY="$PKG/.MainActivity"
DB="databases/ebook.db"

# ---- adb / 设备解析 ---------------------------------------------------------
_resolve_adb() {
  if [[ -x "${ANDROID_HOME:-}/platform-tools/adb" ]]; then echo "${ANDROID_HOME}/platform-tools/adb"
  elif [[ -x "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then echo "$HOME/Library/Android/sdk/platform-tools/adb"
  elif command -v adb >/dev/null 2>&1; then echo "adb"
  else echo ""; fi
}
ADB="$(_resolve_adb)"

_resolve_serial() {
  if [[ -n "${PERF_DEVICE:-}" ]]; then echo "$PERF_DEVICE"; return; fi
  "$ADB" devices | awk '$NF=="device"{print $1; exit}'
}
SERIAL="$(_resolve_serial)"

require_device() {
  if [[ -z "$SERIAL" ]]; then
    echo "❌ 未检测到 adb 设备（设置 PERF_DEVICE 指定 serial）" >&2; exit 1
  fi
}

adb_sh() { "$ADB" -s "$SERIAL" shell "$@"; }

# ---- 设备属性（基线固化头） --------------------------------------------------
_getprop() { adb_sh getprop "$1" | tr -d '\r'; }

device_props() {
  local memtotal
  memtotal="$(adb_sh cat /proc/meminfo | awk '/MemTotal/{print $2; exit}' | tr -d '\r')"
  cat <<EOF
device_model=$(_getprop ro.product.model)
device_brand=$(_getprop ro.product.brand)
android_release=$(_getprop ro.build.version.release)
android_sdk=$(_getprop ro.build.version.sdk)
build_id=$(_getprop ro.build.display.id)
abis=$(_getprop ro.product.cpu.abilist)
mem_total_kb=${memtotal}
app_version=$(adb_sh "dumpsys package $PKG" | grep -oE 'versionName=[0-9.]+' | head -1 | cut -d= -f2 | tr -d '\r')
EOF
}

# ---- DB 查询 ----------------------------------------------------------------
book_count() {
  adb_sh "run-as $PKG sqlite3 $DB 'SELECT count(*) FROM books;'" 2>/dev/null | tr -d '\r'
}

# 取目标书：优先标题含 $1 的；否则取最近打开的（lastOpenedAt 最大）。
pick_book_id() {
  local hint="${1:-}"
  if [[ -n "$hint" ]]; then
    adb_sh "run-as $PKG sqlite3 $DB \"SELECT id FROM books WHERE title LIKE '%${hint}%' ORDER BY lastOpenedAt DESC LIMIT 1;\"" 2>/dev/null | tr -d '\r'
  else
    adb_sh "run-as $PKG sqlite3 $DB 'SELECT id FROM books ORDER BY lastOpenedAt DESC LIMIT 1;'" 2>/dev/null | tr -d '\r'
  fi
}

book_field() {  # book_field <id> <column>
  adb_sh "run-as $PKG sqlite3 $DB \"SELECT $2 FROM books WHERE id='$1';\"" 2>/dev/null | tr -d '\r'
}

# ---- 冷启动 / 应用控制 -------------------------------------------------------
force_stop() { adb_sh am force-stop "$PKG"; }

# am start -W，输出原始（含 TotalTime / LaunchState）。
cold_start_raw() {
  adb_sh am start -W -n "$ACTIVITY" 2>&1 | tr -d '\r'
}

# ---- 进程 / 内存 -------------------------------------------------------------
pidof_app() { adb_sh pidof "$PKG" 2>/dev/null | tr -d '\r' | awk '{print $1}'; }

# 抓一次 meminfo 快照，输出单行：total_pss java native graphics（KB，缺则 -）。
meminfo_snapshot() {
  local f
  f="$(mktemp)"
  adb_sh dumpsys meminfo "$PKG" >"$f" 2>/dev/null
  local total java native graphics
  total=$(grep -m1 'TOTAL PSS:' "$f" | awk '{print $3}')
  java=$(grep -m1 'Java Heap:' "$f" | awk '{print $3}')
  native=$(grep -m1 'Native Heap:' "$f" | awk '{print $3}')
  graphics=$(grep -m1 'Graphics:' "$f" | awk '{print $2}')
  rm -f "$f"
  echo "${total:--} ${java:--} ${native:--} ${graphics:--}"
}

# ---- UI 等待 -----------------------------------------------------------------
# 轮询 uiautomator dump，直到出现含 $1 的节点或超时（秒）。成功返回 0。
wait_for_node() {
  local needle="$1" timeout="${2:-15}" i=0
  while (( i < timeout )); do
    if adb_sh uiautomator dump /sdcard/__perf_ui.xml >/dev/null 2>&1; then
      if adb_sh cat /sdcard/__perf_ui.xml 2>/dev/null | tr -d '\r' | grep -q "$needle"; then
        return 0
      fi
    fi
    sleep 1; ((i++))
  done
  return 1
}

# ---- 统计 --------------------------------------------------------------------
# 从 stdin（每行一个数，单位 ms）输出：n min p50 p95 max mean
# 用 sort -n 预排序（BSD awk 无 asort），a[1]=min a[n]=max。
stats() {
  sort -n | awk '{a[NR]=$1+0; s+=$1+0} END{
    n=NR; if(n==0){print "n=0"; exit}
    r50=0.50*n; k50=int(r50); if(r50>k50)k50++
    r95=0.95*n; k95=int(r95); if(r95>k95)k95++
    if(k50<1)k50=1; if(k95<1)k95=1
    printf "n=%d min=%.0f p50=%.0f p95=%.0f max=%.0f mean=%.0f\n", n, a[1], a[k50], a[k95], a[n], s/n
  }'
}

# ---- 点击书卡 ----------------------------------------------------------------
# 本文件所在目录（bash 用 BASH_SOURCE；zsh 兜底 $0）。
HERE_PERF="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"

# dump UI 并用 tap_node.py 找含 needle 的节点中心；输出 "cx cy" 或空。
# uiautomator dump 在 UI 过渡期会偶发失败（返回旧文件），故重试到成功且命中。
tap_node_center() {
  local needle="$1" i c
  for ((i=0;i<5;i++)); do
    if adb_sh uiautomator dump /sdcard/__perf_ui.xml >/dev/null 2>&1; then
      c="$(adb_sh cat /sdcard/__perf_ui.xml 2>/dev/null | tr -d '\r' \
            | python3 "$HERE_PERF/tap_node.py" "$needle" 2>/dev/null || true)"
      if [[ -n "$c" ]]; then echo "$c"; return 0; fi
    fi
    sleep 0.6
  done
  return 1
}

# 点击含 needle 的书卡；找不到则上滑重试最多 6 次。成功 0。
tap_book() {
  local needle="$1" tries=0 center
  while (( tries < 6 )); do
    center="$(tap_node_center "$needle")"
    if [[ -n "$center" ]]; then adb_sh input tap $center; return 0; fi
    adb_sh input swipe 540 1600 540 700 300   # 上滑找
    sleep 0.6
    ((tries++))
  done
  return 1
}

# 等 FIRST_PAGE 标记出现（轮询 logcat），超时 $1 秒。出现 0。
wait_first_page() {
  local timeout="${1:-20}" k
  for ((k=0;k<timeout*2;k++)); do
    if adb_sh "logcat -d" 2>/dev/null | grep -q "PERF_READER_OPEN_FIRST_PAGE"; then return 0; fi
    sleep 0.5
  done
  return 1
}

# 详情页 → reader：点「继续阅读」或「开始阅读」。成功 0。
tap_continue() {
  local c needle
  for needle in "继续阅读" "开始阅读"; do
    sleep 0.5
    c="$(tap_node_center "$needle")"
    if [[ -n "$c" ]]; then adb_sh input tap $c; return 0; fi
  done
  return 1
}

# ---- 通用 --------------------------------------------------------------------
now_ms() { python3 -c 'import time;print(int(time.time()*1000))'; }
