param(
    [ValidateSet('Start','Monitor','Mark','Bug','Snapshot','Stop','Status')][string]$Action = 'Status',
    [string]$TargetDir,
    [string]$Session,
    [string]$Case = '',
    [string]$Text = '',
    [int]$IntervalSeconds = 5
)
# Codex may launch Windows PowerShell from PowerShell 7; prefer this host's bundled modules.
$env:PSModulePath = $PSHOME + '\Modules;' + $env:PSModulePath
$ErrorActionPreference = 'Stop'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$sessions = Join-Path $PSScriptRoot 'Sessions'

function Write-Json([string]$Path, $Value) {
    $temporary = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    [IO.File]::WriteAllText($temporary, (ConvertTo-Json -InputObject $Value -Depth 12), $utf8)
    if ([IO.File]::Exists($Path)) { [IO.File]::Replace($temporary,$Path,[NullString]::Value) }
    else { [IO.File]::Move($temporary,$Path) }
}
function Clock-Value {
    return @{ utc = [DateTimeOffset]::UtcNow.ToString('o'); local = [DateTimeOffset]::Now.ToString('o') }
}
function Target-Processes([string]$Target) {
    $prefix = $Target.TrimEnd('\') + '\'
    return @(Get-CimInstance Win32_Process -ErrorAction Stop | Where-Object {
        ($_.ExecutablePath -and $_.ExecutablePath.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) -or
        ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -and $_.CommandLine.IndexOf($prefix,[StringComparison]::OrdinalIgnoreCase) -ge 0)
    })
}
function Sample-Processes([string]$Target) {
    $result = @()
    foreach ($item in @(Target-Processes $Target)) {
        try {
            $process = Get-Process -Id $item.ProcessId -ErrorAction Stop
            $result += @{
                pid = $process.Id; name = $process.ProcessName
                cpuTotalSeconds = $process.TotalProcessorTime.TotalSeconds
                workingSetBytes = $process.WorkingSet64; privateBytes = $process.PrivateMemorySize64
                handles = $process.HandleCount; threads = $process.Threads.Count
                responding = $process.Responding; hasWindow = ($process.MainWindowHandle -ne 0)
                startUtc = $process.StartTime.ToUniversalTime().ToString('o')
            }
        } catch { $result += @{ pid = $item.ProcessId; unavailable = $true } }
    }
    return $result
}
function Ports-Value {
    # Device names only. No serial open, firmware query, hardware serial number, or device-instance ID.
    return @(Get-CimInstance Win32_PnPEntity -ErrorAction Stop |
        Where-Object { $_.Name -match '\(COM\d+\)' } |
        ForEach-Object { @{ name = $_.Name; status = $_.Status; errorCode = $_.ConfigManagerErrorCode } })
}
function Event-Value([string]$Kind, $Details) {
    $id = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssfff') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,6)
    $value = @{ id = $id; time = Clock-Value; kind = $Kind; case = $Case; details = $Details }
    Write-Json (Join-Path $Session "events\$id.json") $value
    return $id
}
function Snapshot-Value {
    $data = @{ time = Clock-Value; processes = @(Sample-Processes $script:target) }
    try { $data.ports = @(Ports-Value) } catch { $data.portEnumerationError = $_.Exception.Message }
    return $data
}
function Resolve-Session {
    if (-not $script:Session) {
        $latest = Join-Path $sessions 'latest.txt'
        if (-not (Test-Path -LiteralPath $latest)) { throw '请先运行 01-开始采集.cmd。' }
        $script:Session = [IO.File]::ReadAllText($latest,$utf8).Trim()
    }
    $script:Session = (Resolve-Path -LiteralPath $script:Session).ProviderPath
    $config = Get-Content -LiteralPath (Join-Path $script:Session 'session.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    $script:target = $config.targetDir
    return $config
}

try {
    if ($Action -eq 'Start') {
        if (-not $TargetDir) { $TargetDir = Read-Host '输入待测原安装目录或 EXE 路径' }
        $target = (Resolve-Path -LiteralPath $TargetDir.Trim().Trim('"')).ProviderPath
        if (Test-Path -LiteralPath $target -PathType Leaf) { $target = [IO.Path]::GetDirectoryName($target) }
        if (-not (Test-Path -LiteralPath (Join-Path $target 'core') -PathType Leaf)) { throw '所选目录没有原安装版 core。' }
        if ($IntervalSeconds -lt 2 -or $IntervalSeconds -gt 60) { throw '采样间隔应为 2–60 秒。' }
        New-Item -ItemType Directory -Path $sessions -Force | Out-Null
        $latest = Join-Path $sessions 'latest.txt'
        if (Test-Path -LiteralPath $latest) {
            $previous = [IO.File]::ReadAllText($latest,$utf8).Trim()
            $heartbeat = Join-Path $previous 'heartbeat.json'
            if (Test-Path -LiteralPath $heartbeat) {
                $state = Get-Content -LiteralPath $heartbeat -Raw -Encoding UTF8 | ConvertFrom-Json
                if ($state.state -eq 'running' -and ([DateTimeOffset]::UtcNow - [DateTimeOffset]::Parse($state.utc)).TotalSeconds -lt 120) { throw '已有采集运行中，请先结束上一轮。' }
            }
        }
        $id = [DateTimeOffset]::Now.ToString('yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,6)
        $Session = Join-Path $sessions $id
        foreach ($name in @('events','attachments')) { New-Item -ItemType Directory -Path (Join-Path $Session $name) -Force | Out-Null }
        Write-Json (Join-Path $Session 'session.json') @{
            schema = 1; kit = 'field-20261105-serialfix'; id = $id; started = Clock-Value
            targetDir = $target; intervalSeconds = $IntervalSeconds
            scriptSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
            limits = 'No serial IO, device control, auto screenshots, document/log/license copy, or root-cause inference.'
        }
        [IO.File]::WriteAllText($latest,$Session,$utf8)
        $environment = @{ time = Clock-Value; pointerSize = [IntPtr]::Size; powershell = $PSVersionTable.PSVersion.ToString() }
        $os = Get-CimInstance Win32_OperatingSystem
        $environment.os = @{ caption = $os.Caption; version = $os.Version; build = $os.BuildNumber; architecture = $os.OSArchitecture }
        Add-Type -AssemblyName System.Windows.Forms
        $environment.displays = @([Windows.Forms.Screen]::AllScreens | ForEach-Object { @{ width = $_.Bounds.Width; height = $_.Bounds.Height; primary = $_.Primary } })
        $environment.displayDpi = @(Get-CimInstance Win32_DesktopMonitor | ForEach-Object { @{ x = $_.PixelsPerXLogicalInch; y = $_.PixelsPerYLogicalInch } })
        $environment.files = @()
        foreach ($name in @('core','奎享雕刻.exe','奎享雕刻.vmoptions','jre\release','jre\bin\java.exe')) {
            $file = Join-Path $target $name
            if (Test-Path -LiteralPath $file -PathType Leaf) { $environment.files += @{ relative = $name; bytes = (Get-Item -LiteralPath $file).Length; sha256 = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant() } }
        }
        $environment.fonts = @()
        $fontDir = Join-Path $target 'gcodeFonts'
        if (Test-Path -LiteralPath $fontDir) {
            $environment.fonts = @(Get-ChildItem -LiteralPath $fontDir -Filter '*.gfont' -File -Recurse | ForEach-Object {
                @{ relative = $_.FullName.Substring($fontDir.Length+1); bytes = $_.Length; sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
            })
        }
        Write-Json (Join-Path $Session 'environment.json') $environment
        [IO.File]::WriteAllText((Join-Path $Session 'attachments\说明.txt'),'仅手工放入本次测试文章副本、导出路径、截图/录像、脱敏错误日志。不要放许可、完整用户目录、全量配置或整个程序。',$utf8)
        $null = Event-Value 'start' (Snapshot-Value)
        $escapedScript = $PSCommandPath.Replace("'","''")
        $escapedSession = $Session.Replace("'","''")
        $command = "& '$escapedScript' -Action Monitor -Session '$escapedSession'"
        $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($command))
        Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -ArgumentList @('-NoProfile','-ExecutionPolicy','Bypass','-EncodedCommand',$encoded) -WindowStyle Hidden | Out-Null
        for ($i=0; $i -lt 150; $i++) {
            if ((Test-Path -LiteralPath (Join-Path $Session 'heartbeat.json')) -or (Test-Path -LiteralPath (Join-Path $Session 'collector-error.txt'))) { break }
            Start-Sleep -Milliseconds 100
        }
        if (-not (Test-Path -LiteralPath (Join-Path $Session 'heartbeat.json'))) {
            [IO.File]::WriteAllText((Join-Path $Session 'stop.request'),'startup did not confirm',$utf8)
            throw "采集进程未确认启动；查看 $Session\collector-error.txt，勿把本轮当作已采集。"
        }
        Write-Host "采集已开始：$Session`n每 $IntervalSeconds 秒采集一次；程序尚未启动时进程列表为空，属正常。"
        exit 0
    }
    $config = Resolve-Session
    if ($Action -eq 'Monitor') {
        $deadline = [DateTimeOffset]::UtcNow.AddHours(12)
        try {
            while (-not (Test-Path -LiteralPath (Join-Path $Session 'stop.request')) -and [DateTimeOffset]::UtcNow -lt $deadline) {
                $value = @{ time = Clock-Value; processes = @(Sample-Processes $target) }
                [IO.File]::AppendAllText((Join-Path $Session 'metrics.jsonl'),(ConvertTo-Json -InputObject $value -Depth 8 -Compress)+[Environment]::NewLine,$utf8)
                Write-Json (Join-Path $Session 'heartbeat.json') @{ state = 'running'; pid = $PID; utc = [DateTimeOffset]::UtcNow.ToString('o') }
                for ($i=0; $i -lt $config.intervalSeconds; $i++) {
                    if (Test-Path -LiteralPath (Join-Path $Session 'stop.request')) { break }
                    Start-Sleep -Seconds 1
                }
            }
        } finally {
            Write-Json (Join-Path $Session 'heartbeat.json') @{ state = 'stopped'; pid = $PID; utc = [DateTimeOffset]::UtcNow.ToString('o') }
        }
        exit 0
    }
    if ($Action -eq 'Status') {
        Write-Host "本轮目录：$Session"
        if (Test-Path -LiteralPath (Join-Path $Session 'heartbeat.json')) { Get-Content -LiteralPath (Join-Path $Session 'heartbeat.json') -Raw -Encoding UTF8 }
    } elseif ($Action -eq 'Mark') {
        if (-not $Case) { $Case = Read-Host '测试用例编号，例如 M02' }
        if (-not $Text) { $Text = Read-Host '刚才执行的操作或观察结果' }
        $eventId = Event-Value 'mark' @{ text = $Text; snapshot = Snapshot-Value }
        Write-Host "已记时间标记：$eventId"
    } elseif ($Action -eq 'Bug') {
        if (-not $Case) { $Case = Read-Host '测试用例编号' }
        if (-not $Text) { $Text = Read-Host 'Bug 标题' }
        $details = @{ title = $Text; steps = (Read-Host '复现步骤'); expected = (Read-Host '预期'); actual = (Read-Host '实际'); physicalState = (Read-Host '机器当时是否运动/是否已现场确认停机'); repeatability = (Read-Host '复现次数/尝试次数'); snapshot = Snapshot-Value }
        $eventId = Event-Value 'bug' $details
        New-Item -ItemType Directory -Path (Join-Path $Session "attachments\$eventId") | Out-Null
        Write-Host "Bug 已记：$eventId。请把相应截图/文章/路径放入 attachments\$eventId。"
    } elseif ($Action -eq 'Snapshot') {
        $eventId = Event-Value 'snapshot' (Snapshot-Value)
        Write-Host "快照已记：$eventId"
    } elseif ($Action -eq 'Stop') {
        [IO.File]::WriteAllText((Join-Path $Session 'stop.request'),[DateTimeOffset]::UtcNow.ToString('o'),$utf8)
        $state = $null
        for ($i=0; $i -lt 40; $i++) {
            $heartbeat = Join-Path $Session 'heartbeat.json'
            if (Test-Path -LiteralPath $heartbeat) { $state = Get-Content -LiteralPath $heartbeat -Raw -Encoding UTF8 | ConvertFrom-Json }
            if ($state -and $state.state -eq 'stopped') { break }
            Start-Sleep -Milliseconds 250
        }
        if (-not $state -or $state.state -ne 'stopped') { throw '采集器尚未确认停止，未打包；查看心跳和 collector-error.txt。不会结束软件进程。' }
        $null = Event-Value 'stop' (Snapshot-Value)
        $archive = "$Session.zip"
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $temporaryArchive = $archive + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        [IO.Compression.ZipFile]::CreateFromDirectory($Session,$temporaryArchive)
        if ([IO.File]::Exists($archive)) { [IO.File]::Replace($temporaryArchive,$archive,[NullString]::Value) }
        else { [IO.File]::Move($temporaryArchive,$archive) }
        Write-Host "采集已停止，回传包：$archive`n请在回传前检查手工附件中的个人内容。软件和机器状态未被改变。"
    }
} catch {
    if ($Action -eq 'Monitor' -and $Session -and (Test-Path -LiteralPath $Session)) {
        [IO.File]::WriteAllText((Join-Path $Session 'collector-error.txt'),($_ | Out-String),$utf8)
    }
    Write-Error (($_ | Out-String) + $_.ScriptStackTrace)
    exit 1
}
