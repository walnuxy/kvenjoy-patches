param(
    [Parameter(Position = 0)]
    [string]$TargetDir
)

$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Windows.Forms

    if ([string]::IsNullOrWhiteSpace($TargetDir)) {
        $dialog = New-Object System.Windows.Forms.FolderBrowserDialog
        $dialog.Description = 'Choose the folder of the installed and registered Kvenjoy application'
        $dialog.ShowNewFolderButton = $false
        if ($dialog.ShowDialog() -ne [System.Windows.Forms.DialogResult]::OK) {
            exit 0
        }
        $TargetDir = $dialog.SelectedPath
    }

    $target = (Resolve-Path -LiteralPath $TargetDir -ErrorAction Stop).ProviderPath
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
