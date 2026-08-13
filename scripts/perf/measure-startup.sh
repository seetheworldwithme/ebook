#!/usr/bin/env bash
# REL-05 —— 冷启动测量（am start -W TotalTime）。
# 用法： ./measure-startup.sh [N]          # 当前书库冷启动 N 次（默认 25）
# 注：1000 本测试由 run-baseline 先 seed-library.sh insert 1000 再调本脚本。
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device

N="${1:-25}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

echo "=== 冷启动测量（N=${N}，当前书库 $(book_count) 本）==="
echo "run,TotalTime_ms,LaunchState"

# warmup 1 次（排除 dex/profile 首次开销），不计入
force_stop; sleep 2; cold_start_raw >/dev/null; sleep 1

for ((i=1;i<=N;i++)); do
  force_stop
  sleep 2          # 确保 OS 完成回收 → 真·COLD
  adb_sh logcat -c >/dev/null 2>&1
  raw="$(cold_start_raw)"
  tt="$(echo "$raw" | awk '/TotalTime:/{print $2; exit}')"
  st="$(echo "$raw" | awk '/LaunchState:/{print $2; exit}')"
  tt="${tt:-NaN}"; st="${st:-?}"
  echo "$i,$tt,$st"
  echo "$tt" >> "$TMP/samples.txt"
  sleep 0.5
done

echo "--- 统计（ms）---"
awk 'NF && $1!="NaN"{print}' "$TMP/samples.txt" | stats
