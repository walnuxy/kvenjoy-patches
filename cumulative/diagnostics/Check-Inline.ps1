param([string]$TargetDir,[string]$PatcherJar)
$env:PSModulePath = $PSHOME + '\Modules;' + $env:PSModulePath
$ErrorActionPreference = 'Stop'
$expected = '20b4b38db24c19d57bb3130c42155080518c4ec17dc36eeac768665580ea32d9'
$report = [ordered]@{ diagnostic = 'inline-0.025'; timeUtc = [DateTimeOffset]::UtcNow.ToString('o'); readOnly = $true }
try {
    if (-not $TargetDir) {
        $active = @(Get-CimInstance Win32_Process | Where-Object { $_.Name -eq '奎享雕刻.exe' -and $_.ExecutablePath } | Select-Object -ExpandProperty ExecutablePath -Unique)
        if ($active.Count -eq 1) { $TargetDir = $active[0] }
        else { $TargetDir = Read-Host '粘贴正在使用的软件EXE完整路径，或它所在的安装目录' }
    }
    $target = (Resolve-Path -LiteralPath $TargetDir.Trim().Trim('"')).ProviderPath
    if (Test-Path -LiteralPath $target -PathType Leaf) { $target = [IO.Path]::GetDirectoryName($target) }
    $report.targetDir = $target
    $core = Join-Path $target 'core'; $app = Join-Path $target 'app\app.jar'
    $hasCore = Test-Path -LiteralPath $core -PathType Leaf
    $hasApp = Test-Path -LiteralPath $app -PathType Leaf
    $report.hasCore = $hasCore; $report.hasAppJar = $hasApp
    $report.installerBranch = if ($hasApp) { 'standalone' } elseif ($hasCore) { 'vendor' } else { 'unknown' }
    $report.branchReason = if ($hasApp -and $hasCore) {
        '混合目录：同时有core和app/app.jar，0.025更新器会选独立版。必须核实EXE实际启动链。'
    } elseif ($hasApp) {
        '0.019独立版：公共0.025保留其旧业务类，不会安装笔记直接编辑功能。'
    } elseif ($hasCore) {
        '原安装版：公共0.025应包含笔记直接编辑，需要继续核验实际载荷和运行中的程序。'
    } else { '目录不匹配：未发现原安装版core或独立版app/app.jar。' }
    $main = if ($hasApp) { $app } else { $core }
    if (Test-Path -LiteralPath $main -PathType Leaf) {
        $report.programSha256 = (Get-FileHash -LiteralPath $main -Algorithm SHA256).Hash.ToLowerInvariant()
        $report.programModifiedUtc = (Get-Item -LiteralPath $main).LastWriteTimeUtc.ToString('o')
    }
    $prefix = $target.TrimEnd('\') + '\'
    $report.runningProcesses = @(Get-CimInstance Win32_Process | Where-Object {
        ($_.ExecutablePath -and $_.ExecutablePath.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) -or
        ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -and $_.CommandLine.IndexOf($prefix,[StringComparison]::OrdinalIgnoreCase) -ge 0)
    } | ForEach-Object {
        @{ pid = $_.ProcessId; executable = $_.ExecutablePath; startedUtc = if ($_.CreationDate) { $_.CreationDate.ToUniversalTime().ToString('o') } else { $null } }
    })
    $report.processMayPredateUpdate = @($report.runningProcesses | Where-Object { $_.startedUtc -and $report.programModifiedUtc -and [DateTimeOffset]::Parse($_.startedUtc) -lt [DateTimeOffset]::Parse($report.programModifiedUtc) }).Count -gt 0
    if (-not $PatcherJar) {
        foreach ($candidate in @((Join-Path $PSScriptRoot 'update-patcher.jar'),(Join-Path $PSScriptRoot '..\update-patcher.jar'),(Join-Path $PSScriptRoot '..\cumulative\update-patcher.jar'))) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { $PatcherJar = $candidate; break }
        }
    }
    if (-not $PatcherJar) { $PatcherJar = Read-Host '粘贴下载的0.025包内 update-patcher.jar 完整路径（留空则仅识别程序分支）' }
    $report.patcherMatchesPublic025 = $false
    if ($PatcherJar) {
        $PatcherJar = (Resolve-Path -LiteralPath $PatcherJar.Trim().Trim('"')).ProviderPath
        $report.patcherSha256 = (Get-FileHash -LiteralPath $PatcherJar -Algorithm SHA256).Hash.ToLowerInvariant()
        $report.patcherMatchesPublic025 = $report.patcherSha256 -eq $expected
        if ($report.patcherMatchesPublic025) {
            $java = if ($hasApp) { Join-Path $target 'runtime\bin\java.exe' } else { Join-Path $target 'jre\bin\java.exe' }
            if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw '目标目录缺少自带java.exe，未执行载荷校验。' }
            # The public --verify command decrypts locally in memory, compares components, and never opens a port.
            $start = New-Object Diagnostics.ProcessStartInfo
            $start.FileName = $java
            $start.Arguments = '-Dfile.encoding=UTF-8 -jar "' + $PatcherJar + '" --verify "' + $target + '"'
            $start.UseShellExecute = $false; $start.CreateNoWindow = $true
            $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
            $start.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
            $start.StandardErrorEncoding = New-Object Text.UTF8Encoding($false)
            $process = [Diagnostics.Process]::Start($start)
            try {
                $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
                if (-not $process.WaitForExit(30000)) { $process.Kill(); throw '只读载荷校验超过30秒，已结束本工具自己启动的校验进程，未操作软件窗口。' }
                $report.verifyExitCode = $process.ExitCode
                $report.verifyOutput = $stdout.Result.Trim(); $report.verifyError = $stderr.Result.Trim()
                $report.installedPayloadVerified = $process.ExitCode -eq 0 -and $report.verifyOutput.StartsWith('VERIFIED 0.025 ')
            } finally { $process.Dispose() }
        } else { $report.verifyError = '所选安装器不是已发布0.025的确切文件；没有执行它。' }
    }
    $report.inlinePayloadExpected = $report.installerBranch -eq 'vendor' -and $report.installedPayloadVerified -eq $true
    $report.uiEntry = '主界面切换到“笔记”模式，左侧“文章”分组中的“进入编辑模式”。“文字”模式不提供此入口。'
    $report.limits = '本工具只能核验磁盘和进程信息，不能证明另一安装目录/旧窗口已加载新类，也不读取界面当前模式。程序SHA不要求与别的电脑相同。'
} catch { $report.diagnosticError = $_.Exception.Message }
$output = Join-Path $env:TEMP ('kvenjoy-inline-check-' + [DateTime]::Now.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,6) + '.json')
$json = ConvertTo-Json -InputObject $report -Depth 7
[IO.File]::WriteAllText($output,$json,(New-Object Text.UTF8Encoding($false)))
Write-Host $json
Write-Host "`n报告：$output`n将报告交给Codex即可；不含许可内容、正文或串口数据，但包含安装路径。"
