param(
    [Parameter(Position = 0)]
    [string]$TargetDir
)

$ErrorActionPreference = 'Stop'

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
    foreach ($relative in @('core', 'licene', 'info.txt', 'jre\bin\javaw.exe')) {
        if (-not (Test-Path -LiteralPath (Join-Path $resolved $relative) -PathType Leaf)) {
            throw "所选目录缺少 $relative；请选择正式版软件所在目录。"
        }
    }
    return $resolved
}

function New-InstallationPathForm([string]$InitialPath) {
    Add-Type -AssemblyName System.Windows.Forms
    Add-Type -AssemblyName System.Drawing
    $form = New-Object System.Windows.Forms.Form
    $form.Text = '字体与路径补丁 - 选择软件目录'
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
        $folder.Description = '选择已安装并授权的奎享雕刻目录'
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
    $core = Join-Path $target 'core'
    $licene = Join-Path $target 'licene'
    $info = Join-Path $target 'info.txt'
    $javaw = Join-Path $target 'jre\bin\javaw.exe'
    $jar = Join-Path $PSScriptRoot 'font-patcher.jar'

    foreach ($path in @($core, $licene, $info, $javaw, $jar)) {
        if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
            throw "Missing required local file: $path. Select the installed application folder, not the development repo."
        }
    }

    # Uses the target application's own Java 8. No external JDK, Python or license transfer.
    # Explicit quotes are required by Windows PowerShell 5.1 Start-Process for paths with spaces.
    $argsForJava = @('-jar', ('"' + $jar + '"'), '--gui', ('"' + $target + '"'))
    $proc = Start-Process -FilePath $javaw -ArgumentList $argsForJava -WorkingDirectory $target -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        throw "The patcher exited with code $($proc.ExitCode). No license was copied."
    }
} catch {
    [System.Windows.Forms.MessageBox]::Show(
        $_.Exception.Message,
        'Font UI patcher',
        [System.Windows.Forms.MessageBoxButtons]::OK,
        [System.Windows.Forms.MessageBoxIcon]::Error
    ) | Out-Null
    exit 1
}
