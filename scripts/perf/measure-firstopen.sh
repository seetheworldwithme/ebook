#!/usr/bin/env bash
# REL-05 —— 首开（恢复阅读）测量。START→FIRST_PAGE 间隔 = 首开总耗时。
# 用法： ./measure-firstopen.sh [N] [title-hint]   （默认 N=15，hint=首开性能样本）
# 路径：书库 → 点书卡 → 详情 → 继续阅读 → reader（START 触发）→ 首页渲染（FIRST_PAGE）。
# 每次冷启新进程（规避 openBook 幂等早返回）；仅计 START→FIRST_PAGE，不含冷启动/导航。
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device
HERE="$(cd "$(dirname "$0")" && pwd)"

N="${1:-15}"
HINT="${2:-首开性能样本}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

echo "=== 首开测量（N=${N}，target=${HINT}）==="
echo "run,total_ms,open_ms,render_ms"

# warmup：冷启动 + 开一次（设 lastOpenedAt + locator，让后续显示「继续阅读」），不计入
force_stop; sleep 2; cold_start_raw >/dev/null
wait_for_node "$HINT" 25 || { echo "❌ 书库未见「${HINT}」——先 import_file.sh 导入"; exit 1; }
tap_book "$HINT" || { echo "❌ warmup 未找到书卡"; exit 1; }
wait_for_node "阅读进度" 12 || true
tap_continue || true
wait_first_page 25 || true
sleep 2

for ((i=1;i<=N;i++)); do
  force_stop; sleep 2
  cold_start_raw >/dev/null                       # 冷启动 → 书库
  if ! wait_for_node "$HINT" 25; then echo "$i,NA_lib,,"; continue; fi
  if ! tap_book "$HINT"; then echo "$i,NA_card,,"; continue; fi
  wait_for_node "阅读进度" 12 || { echo "$i,NA_detail,,"; continue; }
  adb_sh logcat -c >/dev/null 2>&1                # 清 log，仅留本次 open 窗口
  if ! tap_continue; then echo "$i,NA_continue,,"; continue; fi
  if ! wait_first_page 25; then echo "$i,NA_marker,,"; continue; fi
  adb_sh "logcat -d -v threadtime" > "$TMP/log.txt" 2>/dev/null
  python3 "$HERE/parse_markers.py" < "$TMP/log.txt" > "$TMP/marker.txt"
  total="$(awk -F= '/^total=/{print $2}' "$TMP/marker.txt")"
  openv="$(awk -F= '/^open=/{print $2}' "$TMP/marker.txt")"
  rend="$(awk -F= '/^render=/{print $2}' "$TMP/marker.txt")"
  echo "$i,$total,$openv,$rend"
  [[ "$total" =~ ^[0-9]+$ ]] && echo "$total" >> "$TMP/total.txt"
  [[ "$openv" =~ ^[0-9]+$ ]] && echo "$openv" >> "$TMP/open.txt"
  [[ "$rend"  =~ ^[0-9]+$ ]] && echo "$rend"  >> "$TMP/render.txt"
  sleep 0.3
done

echo "--- 总耗时 START→FIRST_PAGE（ms）---"
{ [[ -s "$TMP/total.txt" ]] && stats < "$TMP/total.txt"; } || echo "(无有效样本)"
echo "--- 开书成本 START→READY（ms）---"
{ [[ -s "$TMP/open.txt" ]] && stats < "$TMP/open.txt"; } || echo "(无有效样本)"
echo "--- 渲染成本 READY→FIRST_PAGE（ms）---"
{ [[ -s "$TMP/render.txt" ]] && stats < "$TMP/render.txt"; } || echo "(无有效样本)"
