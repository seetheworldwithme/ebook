#!/usr/bin/env python3
"""REL-05 —— 从 stdin 读 logcat(threadtime)，解析 PERF_READER_OPEN_* 标记时间戳。

输出键值对（ms，缺标记则 -）：
  start=<ms>  ready=<ms>  first_page=<ms>
  total=<first_page-start>   open=<ready-start>   render=<first_page-ready>

logcat threadtime 行首： MM-DD HH:MM:SS.mmm PID TID LEVEL TAG: MSG
"""
import re, sys

TS = re.compile(r'^\d\d-\d\d (\d\d):(\d\d):(\d\d)\.(\d\d\d)')


def ts_ms(line: str):
    m = TS.match(line)
    if not m: return None
    h, mi, s, ms = map(int, m.groups())
    return h * 3600000 + mi * 60000 + s * 1000 + ms


def main() -> int:
    start = ready = first = None
    for line in sys.stdin:
        if "PERF_READER_OPEN_" not in line:
            continue
        t = ts_ms(line)
        if t is None:
            continue
        if "PERF_READER_OPEN_START" in line and start is None:
            start = t
        elif "PERF_READER_OPEN_READY" in line and ready is None:
            ready = t
        elif "PERF_READER_OPEN_FIRST_PAGE" in line and first is None:
            first = t

    def sub(a, b):
        return str(a - b) if (a is not None and b is not None) else "-"

    print(f"start={start if start is not None else '-'}")
    print(f"ready={ready if ready is not None else '-'}")
    print(f"first_page={first if first is not None else '-'}")
    print(f"total={sub(first, start)}")
    print(f"open={sub(ready, start)}")
    print(f"render={sub(first, ready)}")
    return 0 if (start is not None and first is not None) else 1


if __name__ == "__main__":
    sys.exit(main())
