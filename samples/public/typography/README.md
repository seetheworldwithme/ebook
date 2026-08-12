# TYPE-04 排版回归样本（samples/public/typography/）

> 中文 / 日文 / RTL / 竖排 / ruby 注音 的 EPUB 回归样本集（设计文档需求 `TYPE-04`，P0）。
> 全部**合成或公有领域短文本**，无版权问题，可入库。

## 样本清单

| 文件 | 排版特征 | 内容 | 语言 | 验证点 |
| --- | --- | --- | --- | --- |
| `ruby.epub` | ruby 注音（HTML5 `<ruby>/<rt>`） | 自造「汉字 / 注音 / 阅读」拼音示例 | `zh-CN` | 注音小字显示在汉字上方；EPUB3 解析 |
| `rtl.epub` | RTL（右起） | 通用阿拉伯语问候 + 一句说明 | `ar` | 正文从右向左排版；翻页方向右起；OPF `page-progression-direction="rtl"` |
| `vertical.epub` | 竖排（`writing-mode: vertical-rl`） | 李白《静夜思》（公有领域） | `zh-CN` | 文字自上而下、列自右向左排列 |

> CJK 横排样本复用上级目录 `samples/public/chinese-shanhaijing.epub`（山海经，Gutenberg 公有领域），不在本目录重复。

## 生成方式

样本由 `scripts/gen_typography_fixtures.py` 生成（纯 stdlib，结构对齐 app 自有 `TxtEpubConverter`：mimetype STORED 首项 + container/opf/nav/ncx/xhtml）。改内容改脚本，再跑：

```bash
python3 scripts/gen_typography_fixtures.py
```

`dcterms:modified` 固定为常量，产物可复现、测试不抖动。

## 自动化校验（CI）

- **结构层（JVM，必跑）**：`reader/readium/src/test/.../typography/TypographySamplesTest.kt`
  解压每个样本，断言特征标记存在（`<ruby>/<rt>`、`dir="rtl"`/`page-progression-direction="rtl"`、`writing-mode: vertical-rl`）且是合法 EPUB。
- **开书冒烟（仪器，连机跑）**：`app/src/androidTest/.../typography/TypographySamplesOpenTest.kt`
  经生产 `ReadiumFacade` + `OpenBookUseCase` 打开每个样本，断言非空 + 语言/阅读方向元数据符合预期。
  运行：`./gradlew :app:connectedDebugAndroidTest`（需真机/模拟器）。

## 真机肉眼回归清单（vivo V2329A，手动）

把三个 `.epub` 导入 App 打开，逐项核对（自动化无法替代视觉）：

- [ ] **ruby**：汉字「汉 / 注 / 音」上方出现对应拼音小字（hàn / zhù / yīn…），不串位、不缺失。
- [ ] **rtl**：阿拉伯正文从右向左排列；翻页方向为右起；标题居右。
- [ ] **vertical**：古诗自上而下书写，列序自右向左；CJK 不乱码。
- [ ] **山海经**（CJK 横排基线）：繁体正文不乱码、正常翻页。

## 结论：应用无需为这些特征加专门处理代码

- **EPUB ruby / RTL / 竖排** 均由源 XHTML/CSS 承载，**透传给 Readium 的 `EpubNavigatorFragment`（WebView + ReadiumCSS）渲染**；本项目零自定义 CSS、零排版处理代码，且**不需要新增**——引擎原生支持。
- **TXT 路径**（如《万相之王》）经 `TxtEpubConverter.chapter()` 仅产出扁平 `<p>`，TXT 文本无内联标记，故**不可能有 ruby / RTL / 竖排**；无需处理。
- 因此 `TYPE-04` = **样本集 + 结构自动校验 + 真机肉眼回归**，不改应用渲染代码。样本集的真正价值是**防 Readium 升级**（3.3.0 → 未来）后这些排版特征的解析/渲染回归。
