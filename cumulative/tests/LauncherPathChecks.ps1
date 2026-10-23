param([string]$FixtureRoot)
$ErrorActionPreference='Stop'
. (Join-Path (Split-Path $PSScriptRoot -Parent) 'run-update.ps1')
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
$bad=$false;try {Resolve-PatcherJar $fixture | Out-Null}catch{$bad=$_.Exception.Message -like '*update-patcher.jar*' -and $_.Exception.Message -like '*补丁包不完整*'};Check $bad 'missing bundle explains package rather than installation path'
[IO.File]::WriteAllText((Join-Path $fixture 'update-patcher.jar'),'synthetic')
Check ((Resolve-PatcherJar $fixture) -eq (Join-Path $fixture 'update-patcher.jar')) 'source ZIP root launcher resolves adjacent JAR'
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
$standalone=Join-Path $FixtureRoot '独立版 中文 [test]'
New-Item -ItemType Directory -Force -Path (Join-Path $standalone 'app'),(Join-Path $standalone 'runtime/bin') | Out-Null
foreach($name in @('app/app.jar','runtime/bin/javaw.exe','奎享雕刻.exe')) { [IO.File]::WriteAllText((Join-Path $standalone $name),'synthetic') }
Check ((Resolve-InstallationPath $standalone) -eq $standalone) 'standalone folder accepted without license'
Check ((Resolve-InstallationPath (Join-Path $standalone '奎享雕刻.exe')) -eq $standalone) 'standalone EXE accepted'
[IO.File]::Delete((Join-Path $standalone 'runtime/bin/javaw.exe'))
$bad=$false;try {Resolve-InstallationPath $standalone | Out-Null}catch{$bad=$true};Check $bad 'incomplete standalone runtime rejected'
$native=[pscustomobject]@{ExecutablePath=(Join-Path $fixture '奎享雕刻.exe');Name='奎享雕刻.exe';CommandLine=''}
$java=[pscustomobject]@{ExecutablePath='';Name='javaw.exe';CommandLine=('javaw -cp "'+$fixture.ToUpper()+'\app\app.jar" main')}
$other=[pscustomobject]@{ExecutablePath='';Name='javaw.exe';CommandLine='javaw -cp C:\AnotherApp\app.jar main'}
Check (@(Get-InstallationProcess $fixture @($native)).Count -eq 1) 'running native EXE detected'
Check (@(Get-InstallationProcess $fixture @($java)).Count -eq 1) 'running Java target detected regardless of path case'
Check (@(Get-InstallationProcess $fixture @($other)).Count -eq 0) 'unrelated Java process ignored'
Write-Output "Launcher path checks passed: $script:checks"
