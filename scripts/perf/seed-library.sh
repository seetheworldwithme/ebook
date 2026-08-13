#!/usr/bin/env bash
# REL-05 性能基线 —— 合成书籍种子（启动测试用 1000 本）。
#   ./seed-library.sh insert <N>   插入 N 条合成书籍行
#   ./seed-library.sh count        当前合成种子数
#   ./seed-library.sh cleanup      删除全部合成行，恢复原状
# 合成行：文件不存在、coverPath=NULL，仅用于「书库冷启动计数」（冷启动只查 DB+渲染占位）。
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device

TAG="perfseed"

count_seed() {
  adb_sh "run-as $PKG sqlite3 $DB \"SELECT count(*) FROM books WHERE id LIKE 'perf-seed-%';\"" | tr -d '\r'
}

insert_n() {
  local n="${1:?need N}"
  local now; now=$(now_ms)
  echo "-> insert $n synthetic books (tag=$TAG) ..."
  local sql="INSERT INTO books (id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) VALUES "
  local i=1 values=""
  while (( i <= n )); do
    local idx; idx=$(printf "%05d" "$i")
    # 标题含 $TAG 便于清理；双引号保证中文插值，SQL 字面量用单引号包裹
    local title="$TAG $idx"
    local row="('perf-seed-$idx','perf-hash-$idx','$title','[]','synthetic seed for cold-start count','zh-CN','EPUB','application/epub+zip','files/books/perf-seed-$idx.epub',0,NULL,$now,NULL,'READING')"
    if [[ -n "$values" ]]; then values+=","; fi
    values+="$row"
    if (( i % 200 == 0 )); then
      adb_sh "run-as $PKG sqlite3 $DB \"$sql $values;\"" >/dev/null
      values=""
    fi
    ((i++))
  done
  [[ -n "$values" ]] && adb_sh "run-as $PKG sqlite3 $DB \"$sql $values;\"" >/dev/null
  echo "OK seed=$(count_seed) total=$(book_count)"
}

cleanup() {
  local before; before=$(count_seed)
  adb_sh "run-as $PKG sqlite3 $DB \"DELETE FROM books WHERE id LIKE 'perf-seed-%';\"" >/dev/null
  echo "OK deleted $before synthetic books. total=$(book_count)"
}

case "${1:-}" in
  insert) shift; insert_n "${1:-1000}";;
  count)  count_seed;;
  cleanup|restore) cleanup;;
  *) echo "usage: $0 {insert <N>|count|cleanup}"; exit 1;;
esac
