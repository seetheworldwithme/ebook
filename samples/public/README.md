# 公有领域测试样本（samples/public/）

> 用于 `P0V-01`（EPUB 格式覆盖）与后续 `TYPE-04`（CJK / 排版）回归。
> 全部公有领域或开放许可，**可入库**。版权书（如《万相之王》）放 `samples/local/`（已 gitignore，不入库），见 CLAUDE.md §样本策略与红线。

## 样本清单

| 文件 | 格式 | 来源 | 许可证 | 验证点 |
| --- | --- | --- | --- | --- |
| `alice-epub3-images.epub` | EPUB 3.0（reflowable + 图片，189KB） | [Project Gutenberg #11](https://www.gutenberg.org/ebooks/11) | 公有领域（Lewis Carroll, 1865） | EPUB3 解析、nav 导航、XHTML5、封面/图片资源加载 |
| `chinese-shanhaijing.epub` | EPUB 2.0 中文（112KB，`dc:language=zh`，正文 UTF-8） | [Project Gutenberg #25288](https://www.gutenberg.org/ebooks/25288) | 公有领域（古籍） | 中文 CJK 不乱码、繁体排版（`TYPE-04`） |
| `fxl-cole-voyage-of-life.epub` | EPUB 3.0 固定版式（970KB，`rendition:layout=pre-paginated` ×4，11 页 9 图） | [IDPF epub3-samples](https://github.com/IDPF/epub3-samples) release 20230704 | Thomas Cole 画作公有领域；IDPF 打包 | 固定版式渲染、pre-paginated 分页、整页图片 |
| `alice-in-wonderland.epub` | EPUB 2.0（136KB） | Project Gutenberg #11 | 公有领域 | `P0V-02` 基线，已内置 `app/src/main/assets/samples/` |

> 中文样本是 EPUB2：Gutenberg 中文古籍均为老格式 EPUB2；CJK 渲染才是 `TYPE-04` 验证重点，加英文 EPUB3 后 2/3 版本均覆盖。EPUB3 中文样本留待将来。

## 下载方式（2026-08-10 备忘）

`github.com` 主站连接超时（被墙），但 `api.github.com` / `raw.githubusercontent.com` / `www.gutenberg.org` 通；GitHub release 下载走国内加速 `ghproxy.net`。

```bash
# 英文 EPUB3
curl -sL -o alice-epub3-images.epub "https://www.gutenberg.org/ebooks/11.epub3.images"
# 中文 EPUB
curl -sL -o chinese-shanhaijing.epub "https://www.gutenberg.org/cache/epub/25288/pg25288-images.epub"
# FXL（固定版式）—— github.com 不通，走 ghproxy
curl -sL -o fxl-cole-voyage-of-life.epub \
  "https://ghproxy.net/https://github.com/IDPF/epub3-samples/releases/download/20230704/cole-voyage-of-life.epub"
```
