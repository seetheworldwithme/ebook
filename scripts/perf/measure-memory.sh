#!/usr/bin/env bash
# REL-05 —— 内存测量。开 3 种大小文件对比 PSS，证明不随文件大小线性增长。
# 用法： ./measure-memory.sh [small-hint] [large-hint] [epub10-hint]
#   small  = 小 EPUB（~100KB）  large = 大 TXT（~20MB）  epub10 = 10MB EPUB
# 门槛（design §8）：导入/阅读大文件时不按文件大小线性占用内存，不 OOM。
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device

SMALL="${1:-山海經}"
LARGE="${2:-大文件样本}"
EPUB="${3:-首开性能样本}"

echo "=== 内存测量（格式：total_pss java_heap native_heap graphics，KB）==="

open_and_snap() {  # open_and_snap <hint> <label>
  local hint="$1" label="$2" s pss
  force_stop; sleep 2; cold_start_raw >/dev/null
  if ! wait_for_node "$hint" 25; then echo "${label}|未找到|0"; return; fi
  if ! tap_book "$hint"; then echo "${label}|未找到书卡|0"; return; fi
  wait_for_node "阅读进度" 12 || true
  if ! tap_continue; then echo "${label}|未点到继续阅读|0"; return; fi
  wait_first_page 45 || true
  sleep 6
  s="$(meminfo_snapshot)"; pss="$(echo "$s" | awk '{print $1}')"
  echo "${label}|${s}|${pss}"
}

# ① idle（书库）
force_stop; sleep 2; cold_start_raw >/dev/null
wait_for_node "$EPUB" 25 || wait_for_node "书库" 10 || true
sleep 5
idle="$(meminfo_snapshot)"; idle_pss="$(echo "$idle" | awk '{print $1}')"
echo "idle|${idle}|${idle_pss}"

# ② 三种文件大小
small_line="$(open_and_snap "$SMALL" "small_epub")"
echo "$small_line"
large_line="$(open_and_snap "$LARGE" "large_txt")"
echo "$large_line"
epub_line="$(open_and_snap "$EPUB" "epub_10mb")"
echo "$epub_line"

# 解析
small_pss="$(echo "$small_line" | awk -F'|' '{print $3}')"
large_pss="$(echo "$large_line" | awk -F'|' '{print $3}')"
epub_pss="$(echo "$epub_line" | awk -F'|' '{print $3}')"

echo "--- 汇总（KB）---"
echo "idle_pss=${idle_pss}"
echo "small_pss=${small_pss} delta_small=$((small_pss-idle_pss))"
echo "large_pss=${large_pss} delta_large=$((large_pss-idle_pss))"
echo "epub10_pss=${epub_pss} delta_10mb=$((epub_pss-idle_pss))"
