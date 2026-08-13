#!/usr/bin/env bash
# REL-05 —— 把本地文件经 base64 灌进 app 内部 files 目录，触发 ACTION_VIEW 导入。
# 用法： ./import_file.sh <local-file> [remote-name]
# 按扩展名决定 MIME（.epub→application/epub+zip，.txt→text/plain）。
# 去重按 contentHash：重复导入不会产生新书（AlreadyExists）。
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device

LOCAL="${1:?need local file}"
REMOTE="${2:-$(basename "$LOCAL")}"
case "$REMOTE" in
  *.epub) MIME="application/epub+zip";;
  *.txt)  MIME="text/plain";;
  *) echo "❌ 仅支持 .epub / .txt（got ${REMOTE}）"; exit 1;;
esac

echo "→ 传输 $LOCAL → files/${REMOTE}（base64，$(du -h "$LOCAL" | cut -f1)）…"
# vivo run-as：shell 重定向（>/</sh -c）CWD/权限错乱，必须用 tee（app uid 自己开文件）+ 绝对路径。
base64 < "$LOCAL" | adb_sh "run-as $PKG base64 -d | run-as $PKG tee /data/data/$PKG/files/$REMOTE >/dev/null"

# macOS stat 用 -f%z，Linux 用 -c%s；都试
size_local="$(stat -f%z "$LOCAL" 2>/dev/null || stat -c%s "$LOCAL" 2>/dev/null)"
# 读大小同样不能走重定向（vivo 限制），用 wc 文件参数（app uid 自己 open）
size_remote="$(adb_sh "run-as $PKG wc -c /data/data/$PKG/files/$REMOTE" | tr -d '\r' | awk '{print $1}')"
if [[ "$size_local" != "$size_remote" ]]; then
  echo "❌ 体积不一致：本地 $size_local / 远端 $size_remote"; exit 1
fi
echo "  ✓ 落盘 $size_remote bytes"

before="$(book_count)"
echo "→ 触发导入（MIME=${MIME}）…"
adb_sh am start -a android.intent.action.VIEW -t "$MIME" \
  -d "file:///data/data/$PKG/files/$REMOTE" -n "$ACTIVITY" >/dev/null 2>&1
# 导入 + 元数据提取异步；轮询书数变化或超时
for k in $(seq 1 20); do
  sleep 1
  after="$(book_count)"
  [[ "$after" -gt "$before" ]] && break
done
after="$(book_count)"
if [[ "$after" -gt "$before" ]]; then
  echo "✓ 导入成功：$before → $after 本（+ $((after-before))）"
else
  echo "⚠ 书数未增（$before → ${after}）：可能已在书库（去重）或导入失败——查 Toast/日志"
fi
