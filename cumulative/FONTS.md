# 字体资源和来源

累计包沿用0.008的字体资源：70套可再分发字体，包含中文3、英文55、符号8、其他语言4；来源、固定提交、逐字体数据和许可位于安装器JAR的 `fonts/manifest.json`、`fonts/licenses/`。

来源：LingDong-/chinese-hershey-font 与 msurguy/stroke-font-studio，遵循每项资源相应的MIT、SIL OFL或Hershey许可。原有个人字体不重新打包。

另外15套中文手写字体从 jiangmuran/CNCDraw 的固定提交 bc76a8dd0841de277e2261b3f9916a1db64290f4 下载；完整URL、大小、SHA-256及覆盖记录见 `fonts/chinese-downloads.json`。每套覆盖GB2312的6,763个非空汉字，通常总字形数6,888；不代表全部生僻字。首次缺失时下载约51.3MB。本包不重新托管这15套字库，也不授予额外使用许可。
