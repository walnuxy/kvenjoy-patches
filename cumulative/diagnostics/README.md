# 检查0.025为什么没有直接编辑

这是一份只读检查，不安装、不启动或关闭雕刻软件、不连接机器，不修改许可、设置和文章。

把两个Check-Inline文件放在0.025解压目录（update-patcher.jar旁边），双击Check-Inline.cmd。若雕刻程序正在运行，优先自动识别它的EXE目录；无法唯一识别时粘贴安装目录/EXE路径。报告在系统临时目录，并显示在控制台。

给另一位Codex运行：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\Check-Inline.ps1 -TargetDir '实际软件目录' -PatcherJar '0.025解压目录\update-patcher.jar'
```

报告可以区分：下载错包/尚未安装、更新了另一目录、进程早于更新未重新加载、0.019独立版、原版/独立版文件混合，以及原安装版确已匹配0.025载荷。

- `vendor`且`installedPayloadVerified=true`：磁盘上有包含直接编辑的原安装版载荷；保存退出并从这个目录重新启动，在“笔记”模式左侧“文章”组找“进入编辑模式”。“文字”模式不提供此功能。
- `standalone`：0.025继续保留0.019独立版旧业务类，因此不会添加直接编辑；不能把“独立版已符合0.025界面载荷”等同于新编辑功能已安装。
- 同时`hasCore`和`hasAppJar=true`：混合目录，更新器会选独立版；须核对EXE启动链，不删除或覆盖文件试探。
- `patcherMatchesPublic025=false`：安装器文件与公开0.025不一致，未执行它；重新下载正确完整包。
- `installedPayloadVerified=false`或verify报错：保留报告，核对安装结果；不能只凭版本标题认定已更新。

程序二进制有机器本地加密，core SHA不同不代表错误。本检查依赖0.025的逐类`--verify`，只在内存解码，不收集原core、许可、正文或完整配置。报告含安装路径/进程PID，交回前可检查；不要公开上传整台电脑的用户目录。
