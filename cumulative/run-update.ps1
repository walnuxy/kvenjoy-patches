param(
    [Parameter(Position = 0)]
    [string]$TargetDir
)

$ErrorActionPreference = 'Stop'

function Resolve-PatcherJar([string]$BundleDir) {
    $jar = Join-Path $BundleDir 'update-patcher.jar'
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
        throw "补丁包不完整，缺少 update-patcher.jar。请完整解压最新补丁包或 GitHub 下载的 ZIP，再运行一键更新.cmd。缺失位置：$jar"
    }
    return (Resolve-Path -LiteralPath $jar).ProviderPath
}

function Resolve-InstallationPath([string]$Value) {
    $value = [Environment]::ExpandEnvironmentVariables($Value.Trim().Trim('"').Trim("'"))
    if ([string]::IsNullOrWhiteSpace($value)) { throw '请粘贴或输入软件安装目录。' }
    $resolved = (Resolve-Path -LiteralPath $value -ErrorAction Stop).ProviderPath
    if (Test-Path -LiteralPath $resolved -PathType Leaf) {
        if ([IO.Path]::GetExtension($resolved) -ne '.exe' -and [IO.Path]::GetFileName($resolved) -ne 'core') {
            throw '请选择软件安装文件夹，或软件主程序 EXE。'
        }
        $resolved = [IO.Path]::GetDirectoryName($resolved)
    }
    if (Test-Path -LiteralPath (Join-Path $resolved 'app\app.jar') -PathType Leaf) {
        $required = @('app\app.jar','runtime\bin\javaw.exe')
    } else { $required = @('core','licene','info.txt','jre\bin\javaw.exe') }
    foreach ($relative in $required) {
        if (-not (Test-Path -LiteralPath (Join-Path $resolved $relative) -PathType Leaf)) { throw "所选目录缺少 $relative。" }
    }
    return $resolved
}

function Get-InstallationProcess([string]$Target, [object[]]$Processes) {
    if (-not $PSBoundParameters.ContainsKey('Processes')) { $Processes = @(Get-CimInstance Win32_Process -ErrorAction Stop) }
    $Processes | Where-Object {
        ($_.ExecutablePath -and $_.ExecutablePath -eq (Join-Path $Target '奎享雕刻.exe')) -or
        ($_.Name -match '^javaw?\.exe$' -and $_.CommandLine -and ([string]$_.CommandLine).IndexOf($Target.TrimEnd('\') + '\', [StringComparison]::OrdinalIgnoreCase) -ge 0)
    }
}

function New-InstallationPathForm([string]$InitialPath) {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '累计更新补丁 - 选择软件目录'
    $form.ClientSize = New-Object System.Drawing.Size(660, 185)
    $form.StartPosition = 'CenterScreen'
    $form.FormBorderStyle = 'FixedDialog'
    $form.MaximizeBox = $false
    $form.MinimizeBox = $false
    $label = New-Object System.Windows.Forms.Label
    $label.Text = '粘贴安装目录或主程序 EXE 路径（支持 Ctrl+V），也可点击浏览：'
    $label.SetBounds(16, 16, 625, 25)
    $form.Controls.Add($label)
    $pathBox = New-Object System.Windows.Forms.TextBox
    $pathBox.Name = 'InstallationPath'
    $pathBox.SetBounds(16, 48, 535, 26)
    $pathBox.Text = $InitialPath
    $form.Controls.Add($pathBox)
    $browse = New-Object System.Windows.Forms.Button
    $browse.Text = '浏览…'
    $browse.SetBounds(564, 46, 80, 30)
    $form.Controls.Add($browse)
    $errorLabel = New-Object System.Windows.Forms.Label
    $errorLabel.Name = 'PathError'
    $errorLabel.ForeColor = [System.Drawing.Color]::Firebrick
    $errorLabel.SetBounds(16, 86, 628, 42)
    $form.Controls.Add($errorLabel)
    $ok = New-Object System.Windows.Forms.Button
    $ok.Text = '继续'
    $ok.SetBounds(462, 140, 85, 30)
    $cancel = New-Object System.Windows.Forms.Button
    $cancel.Text = '取消'
    $cancel.SetBounds(559, 140, 85, 30)
    $cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $form.Controls.AddRange(@($ok, $cancel))
    $form.AcceptButton = $ok
    $form.CancelButton = $cancel
    $browse.Add_Click({
        $folder = New-Object System.Windows.Forms.FolderBrowserDialog
        $folder.Description = '选择原安装版或独立版目录'
        $folder.ShowNewFolderButton = $false
        if (Test-Path -LiteralPath $pathBox.Text -PathType Container) { $folder.SelectedPath = $pathBox.Text }
        try {
            if ($folder.ShowDialog($form) -eq [System.Windows.Forms.DialogResult]::OK) { $pathBox.Text = $folder.SelectedPath }
        } finally { $folder.Dispose() }
    }.GetNewClosure())
    $ok.Add_Click({
        try {
            $form.Tag = Resolve-InstallationPath $pathBox.Text
            $form.DialogResult = [System.Windows.Forms.DialogResult]::OK
        } catch { $errorLabel.Text = $_.Exception.Message; $pathBox.Focus() }
    }.GetNewClosure())
    $form.Add_Shown({ $pathBox.Focus(); $pathBox.SelectAll() }.GetNewClosure())
    return $form
}

# Dot-sourcing exposes path/UI functions for application-level checks, without launching.
if ($MyInvocation.InvocationName -eq '.') { return }
try {
    Add-Type -AssemblyName System.Windows.Forms
    $jar = Resolve-PatcherJar $PSScriptRoot
    if ([string]::IsNullOrWhiteSpace($TargetDir)) {
        $initial = ''
        if (Test-Path -LiteralPath (Join-Path (Split-Path $PSScriptRoot -Parent) 'core')) { $initial = Split-Path $PSScriptRoot -Parent }
        $form = New-InstallationPathForm $initial
        try {
            if ($form.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) { exit 0 }
            $TargetDir = [string]$form.Tag
        } finally { $form.Dispose() }
    }
    $target = Resolve-InstallationPath $TargetDir
    $javaw = Join-Path $target 'jre\bin\javaw.exe'
    if (Test-Path -LiteralPath (Join-Path $target 'app\app.jar')) { $javaw = Join-Path $target 'runtime\bin\javaw.exe' }
    $active = Get-InstallationProcess $target
    if ($active) { throw '所选软件仍在运行，请先保存内容并退出，再更新。' }

    # Uses the target application's own Java 8. No external JDK, Python or license transfer.
    # Explicit quotes are required by Windows PowerShell 5.1 Start-Process for paths with spaces.
    $argsForJava = @('-Dsun.java2d.dpiaware=true', '-jar', ('"' + $jar + '"'), '--gui', ('"' + $target + '"'))
    $proc = Start-Process -FilePath $javaw -ArgumentList $argsForJava -WorkingDirectory $target -WindowStyle Hidden -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "The patcher exited with code $($proc.ExitCode). Please review the error dialog."
    }
} catch {
    [System.Windows.Forms.MessageBox]::Show(
        $_.Exception.Message,
        'Cumulative update',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
    exit 1
}
