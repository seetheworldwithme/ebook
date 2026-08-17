# V1 PDF/CBZ 格式回归样本

由 `scripts/gen_format_fixtures.py` 程序化生成（纯合成内容，无版权问题），请勿手改；重新生成：

```bash
python3 scripts/gen_format_fixtures.py
```

## 样本清单

| 文件 | 说明 | 用途 |
| --- | --- | --- |
| `minimal.pdf` | 手写最小 3 页文本型 PDF（Helvetica 文本，612×792pt） | PDF 导入/打开/翻页/单页连续切换/进度/书签/强杀恢复 真机回归 |
| `sample.cbz` | 4 页彩色 PNG 漫画（红/绿/蓝/白，DEFLATE ZIP） | CBZ 导入/打开/翻页/进度/书签/恢复 真机回归 |

## 真机回归清单（vivo V2329A）

1. PDF：SAF 导入（元数据/书库展示 application/pdf）→ 打开翻页 → 排版面板切「滚动/分页」生效 → pinch 缩放 → 进度 % + 强杀恢复 → 书签 → **搜索/高亮/TTS/字号±/主题入口全部不出现**（能力矩阵 gating）
2. CBZ：导入 → 打开翻页（红→绿→蓝→白顺序）→ 进度/书签/恢复 → 排版面板无翻页方式/无排版区（只有显示区）
3. 回归：EPUB/TXT 全链路不受影响
