#!/usr/bin/env bash
# REL-05 —— 直接 seed 一个「真实文件书」（绕过 import 路径）。
# 用途：file:// 导入对 .txt 扩展名探测失效（adb 限制），用本脚本把文件 tee 进
# files/books/ 并直接插 DB 行，让 reader 能打开它（测 TXT 转换的内存峰值）。
# 用法： ./seed-book-file.sh <local-file> <bookId> <title> [format] [mediaType]
set -euo pipefail
. "$(dirname "$0")/perf-common.sh"
require_device

LOCAL="${1:?need local file}"; BID="${2:?need bookId}"; TITLE="${3:?need title}"
FMT="${4:-TXT}"; MEDIA="${5:-text/plain}"
ext="${LOCAL##*.}"
REMOTE="files/books/${BID}.${ext}"

exist="$(adb_sh "run-as $PKG sqlite3 $DB \"SELECT count(*) FROM books WHERE id='${BID}';\"" | tr -d '\r')"
if [[ "$exist" != "0" ]]; then echo "skip: id=${BID} 已存在"; exit 0; fi

echo "→ push $LOCAL → $REMOTE …"
base64 < "$LOCAL" | adb_sh "run-as $PKG base64 -d | run-as $PKG tee /data/data/$PKG/$REMOTE >/dev/null"
size="$(stat -f%z "$LOCAL" 2>/dev/null || stat -c%s "$LOCAL" 2>/dev/null)"
now="$(now_ms)"
adb_sh "run-as $PKG sqlite3 $DB \"INSERT INTO books (id,contentHash,title,authors,description,language,format,mediaType,filePath,fileSize,coverPath,importedAt,lastOpenedAt,status) VALUES ('${BID}','${BID}-hash','${TITLE}','[]','seeded file book','zh-CN','${FMT}','${MEDIA}','${REMOTE}',${size},NULL,${now},NULL,'UNREAD');\"" >/dev/null
echo "OK seeded id=${BID} title=${TITLE} size=${size} total=$(book_count)"
