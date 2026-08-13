#!/usr/bin/env python3
"""REL-05 —— 从 stdin 读 uiautomator dump XML，定位含 needle 的可点击目标中心。

直接点 Text 子节点不可靠（Compose 卡片点击区是可点击祖先）。策略：
  1) 找含 needle 的节点（匹配 text 或 content-desc）的中心点；
  2) 在所有 clickable="true" 节点里，选 bounds 包含该中心点、且面积最小的那个（最具体祖先）；
  3) 输出其中心坐标（向下偏移 10px 避开纯文字基线）。无 clickable 则退回节点本身中心。

用法： adb shell cat /sdcard/ui.xml | tap_node.py <needle>
输出 "cx cy"（exit 0）或空（exit 1）。
"""
import re, sys

NODE = re.compile(r'<node\b[^>]*>')
BOUNDS = re.compile(r'\bbounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')


def parsed(data: str):
    """返回 [(text+desc, (x1,y1,x2,y2), clickable_bool)] 按文档顺序。"""
    out = []
    for m in NODE.finditer(data):
        tag = m.group(0)
        b = BOUNDS.search(tag)
        if not b:
            continue
        x1, y1, x2, y2 = map(int, b.groups())
        t = re.search(r'\btext="([^"]*)"', tag)
        d = re.search(r'\bcontent-desc="([^"]*)"', tag)
        label = ((t.group(1) if t else "") + " " + (d.group(1) if d else ""))
        click = 'clickable="true"' in tag
        out.append((label, (x1, y1, x2, y2), click))
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print("usage: tap_node.py <needle>", file=sys.stderr); return 2
    needle = sys.argv[1]
    nodes = parsed(sys.stdin.read())

    target = None
    for label, b, _ in nodes:
        if needle in label and b[2] > b[0] and b[3] > b[1]:
            target = b; break
    if target is None:
        return 1
    cx, cy = (target[0] + target[2]) // 2, (target[1] + target[3]) // 2

    # 选包含中心点、面积最小的 clickable 祖先
    best = None; best_area = None
    for _, b, click in nodes:
        if not click:
            continue
        x1, y1, x2, y2 = b
        if x1 <= cx <= x2 and y1 <= cy <= y2:
            area = (x2 - x1) * (y2 - y1)
            if best_area is None or area < best_area:
                best_area = area; best = b
    if best is not None:
        cx, cy = (best[0] + best[2]) // 2, (best[1] + best[3]) // 2
    print(f"{cx} {cy + 10}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
