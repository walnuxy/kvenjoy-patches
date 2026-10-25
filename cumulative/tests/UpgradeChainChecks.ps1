param([Parameter(Mandatory=$true)][string]$Java, [Parameter(Mandatory=$true)][string]$Work)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).ProviderPath
$old=Join-Path $root 'releases/kvenjoy-update-20261025-0.023/update-patcher.jar'
$new=Join-Path $root 'cumulative-update/build/update-patcher.jar'
$classes=Join-Path $root 'cumulative-update/build/classes'
$original=Join-Path $root 'cumulative-update/build/fixtures/original.jar'
if(Test-Path -LiteralPath $Work){throw "Fixture already exists: $Work"}
function Step([string]$jar,[string]$action){
 & $Java -Xmx768m -cp ($jar+';'+$classes) UpgradeChainChecks $action $Work $original | Out-Null
 if($LASTEXITCODE -ne 0){throw "Failed: $action $jar"}
}
function Hash([string]$path){(Get-FileHash -Algorithm SHA256 -LiteralPath $path).Hash}
Step $old fixture
$core=Join-Path $Work 'core';$receipt=Join-Path $Work '.kvenjoy-update/receipt.properties';$note=Join-Path $Work 'personal-note.kjnote'
$before=Hash $core;$noteBefore=Hash $note
Step $old apply
$prior=Hash $core;$priorReceipt=Hash $receipt
if($prior -eq $before){throw '0.023 did not update core'}
Step $new apply
if((Hash $core) -eq $prior){throw '0.024 did not update core'}
Step $new rollback
if((Hash $core) -ne $prior -or (Hash $receipt) -ne $priorReceipt){throw '0.024 rollback did not restore 0.023 core and receipt'}
Step $old rollback
if((Hash $core) -ne $before -or (Hash $note) -ne $noteBefore){throw '0.023 rollback did not restore original core and personal data'}
Write-Output 'PASS 0.023 -> 0.024 -> 0.023 -> original exact chain'
