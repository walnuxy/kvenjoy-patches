param([string]$FixtureRoot)
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'run-font-patcher.ps1')
$fixture = Join-Path $FixtureRoot '中文 空格 [path]'
New-Item -ItemType Directory -Force -Path (Join-Path $fixture 'jre/bin') | Out-Null
foreach($name in @('core','licene','info.txt','jre/bin/javaw.exe','奎享雕刻.exe')) {
    [IO.File]::WriteAllText((Join-Path $fixture $name),'synthetic')
}
$script:checks=0
function Check([bool]$ok,[string]$name) {if(-not $ok){throw $name};$script:checks++;Write-Output "PASS $name"}
Check ((Resolve-InstallationPath $fixture) -eq $fixture) 'literal unicode space bracket directory'
Check ((Resolve-InstallationPath ('"'+$fixture+'"')) -eq $fixture) 'quoted pasted directory'
Check ((Resolve-InstallationPath (Join-Path $fixture '奎享雕刻.exe')) -eq $fixture) 'pasted executable path'
$env:KVENJOY_TEST_ROOT=$fixture
Check ((Resolve-InstallationPath '%KVENJOY_TEST_ROOT%') -eq $fixture) 'environment variable path'
$bad=$false;try {Resolve-PatcherJar $fixture | Out-Null}catch{$bad=$_.Exception.Message -like '*font-patcher.jar*' -and $_.Exception.Message -like '*补丁包不完整*'};Check $bad 'missing bundle explains package rather than installation path'
[IO.File]::WriteAllText((Join-Path $fixture 'font-patcher.jar'),'synthetic')
Check ((Resolve-PatcherJar $fixture) -eq (Join-Path $fixture 'font-patcher.jar')) 'source ZIP root launcher resolves adjacent JAR'
$bad=$false;try {Resolve-InstallationPath '' | Out-Null}catch{$bad=$true};Check $bad 'empty path rejected'
$bad=$false;try {Resolve-InstallationPath $FixtureRoot | Out-Null}catch{$bad=$true};Check $bad 'incomplete folder rejected'
$form=New-InstallationPathForm $fixture
try {
 $box=$form.Controls.Find('InstallationPath',$true)[0]
 Check ($box -is [System.Windows.Forms.TextBox] -and -not $box.ReadOnly) 'editable pasteable path box'
 Check ($form.AcceptButton.Text -eq '继续') 'enter submits path'
 Check ($form.CancelButton.DialogResult -eq [System.Windows.Forms.DialogResult]::Cancel) 'escape cancels'
 $form.Show();[System.Windows.Forms.Application]::DoEvents();$box.Text='';$form.AcceptButton.PerformClick()
 Check ($form.DialogResult -ne [System.Windows.Forms.DialogResult]::OK -and $form.Controls.Find('PathError',$true)[0].Text.Length -gt 0) 'invalid path keeps dialog open with feedback'
 $box.Text='"'+$fixture+'"';[System.Windows.Forms.Application]::DoEvents();$form.AcceptButton.PerformClick()
 Check ($form.Tag -eq $fixture -and $form.DialogResult -eq [System.Windows.Forms.DialogResult]::OK) 'pasted path confirmed through actual form action'
} finally {$form.Dispose()}
Write-Output "Launcher path checks passed: $script:checks"
