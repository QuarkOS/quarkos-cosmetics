<#
.SYNOPSIS
  Build and test Quark's Cosmetics on Emilio's Windows PC "Quark".
.DESCRIPTION
  Portable JDK + Gradle home on D: (no global installs, no admin). Steps (each skippable):
    1. JUnit tests      :<TestVersion>:test
    2. Compile checks   :<v>:compileJava for every -CompileVersions entry (Fabric + Forge)
    3. Mod jar          :<Version>:jar on 26.x, :<Version>:remapJar on 1.21.x
                        Release jar copied to -OutDir and %USERPROFILE%\Downloads
    4. UI harness       :<Version>:runClient -Pharness=true (screenshots into -OutDir\screens)
  On 26.x, -Version also selects the default JUnit target and adds it to the default compile checks.
  The client opens on the desktop (one-shot interactive scheduled task), muted, click-through,
  never focused. Only one Minecraft client runs on Quark at a time: D:\dev\mc-test.lock.
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\quark-test.ps1 -Only ui-wardrobe-hat,ui-gallery
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\quark-test.ps1 -Version 26.3
#>
param(
    [string]$Version = "1.21.11",
    [string]$TestVersion = "1.21.11",
    [string[]]$CompileVersions = @("1.21.11", "1.21.1", "forge:1.21.11", "forge:1.21.1"),
    [string]$Jdk = "D:\dev\jdks\jdk-25.0.5+8",
    [string]$GradleHome = "D:\dev\cosmetics-gradle",
    [string]$OutDir = "D:\dev\cos-test-out",
    [string]$Only = "",
    [string]$Lock = "D:\dev\mc-test.lock",
    [int]$LockWaitMinutes = 90,
    [int]$HarnessMinutes = 25,
    [switch]$SkipTests,
    [switch]$SkipCompile,
    [switch]$SkipJar,
    [switch]$SkipHarness,
    [switch]$NoDownloadsCopy,
    [switch]$Hidden,
    [switch]$InSession,
    [switch]$NoClickThrough
)

$ErrorActionPreference = "Stop"
# "-File" passes "a,b" as one string; split list parameters ourselves.
$CompileVersions = @($CompileVersions | ForEach-Object { $_ -split "," } | Where-Object { $_ })
$Repo = Split-Path -Parent $PSScriptRoot
Set-Location $Repo
New-Item -ItemType Directory -Force -Path $OutDir, "$OutDir\screens", "$OutDir\logs" | Out-Null

# SSH sessions have no desktop. Unless -Hidden (or nothing needs a window), re-run inside the
# logged-in user's session as a one-shot scheduled task and wait for it.
if (-not $InSession -and -not $Hidden -and -not $SkipHarness) {
    $task = "QuarkosCosmeticsTest"
    $done = Join-Path $OutDir "done.flag"
    Remove-Item $done, (Join-Path $OutDir "summary.json") -ErrorAction SilentlyContinue
    $pass = @()
    foreach ($kv in $PSBoundParameters.GetEnumerator()) {
        if ($kv.Value -is [System.Management.Automation.SwitchParameter]) {
            if ($kv.Value.IsPresent) { $pass += "-$($kv.Key)" }
        } elseif ($kv.Value -is [array]) {
            $pass += "-$($kv.Key)"; $pass += ($kv.Value -join ",")
        } else {
            $pass += "-$($kv.Key)"; $pass += "`"$($kv.Value)`""
        }
    }
    $argLine = "-NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File `"$PSCommandPath`" -InSession " + ($pass -join " ")
    $action = New-ScheduledTaskAction -Execute "powershell.exe" -Argument $argLine -WorkingDirectory $Repo
    $principal = New-ScheduledTaskPrincipal -UserId ([System.Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 3)
    Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
    Register-ScheduledTask -TaskName $task -Action $action -Principal $principal -Settings $settings | Out-Null
    Start-ScheduledTask -TaskName $task
    Write-Host "started on the desktop as scheduled task $task; logs in $OutDir\logs"
    try {
        while (-not (Test-Path $done)) {
            Start-Sleep -Seconds 10
            $state = (Get-ScheduledTask -TaskName $task).State
            if ($state -ne "Running" -and -not (Test-Path $done)) {
                Start-Sleep -Seconds 5
                if (-not (Test-Path $done)) { Write-Host "task stopped without finishing ($state)"; break }
            }
        }
    } finally {
        Unregister-ScheduledTask -TaskName $task -Confirm:$false -ErrorAction SilentlyContinue
    }
    if (Test-Path (Join-Path $OutDir "summary.json")) { Get-Content (Join-Path $OutDir "summary.json") }
    return
}
if ($InSession) { Start-Transcript -Path (Join-Path $OutDir "logs\run.log") -Force | Out-Null }

$env:JAVA_HOME = $Jdk
$env:GRADLE_USER_HOME = $GradleHome
$env:Path = "$Jdk\bin;$env:Path"
$common = @("--console=plain", "--configure-on-demand", "--no-configuration-cache")
$results = [ordered]@{}
$script:haveLock = $false

$unobfuscated = [version]$Version -ge [version]"26.1"
if ($unobfuscated) {
    if (-not $PSBoundParameters.ContainsKey("TestVersion")) { $TestVersion = $Version }
    if (-not $PSBoundParameters.ContainsKey("CompileVersions")) { $CompileVersions = @($Version) + $CompileVersions }
}
$jarTask = if ($unobfuscated) { "jar" } else { "remapJar" }
$jarDir = Join-Path $Repo "versions\$Version\build\libs"
$runDir = if ($Version -eq "1.21.11") { "$Repo\run" } else { "$Repo\run\$Version" }

function Stop-RepoJava {
    $needles = @(($Repo.ToLower() + "\"), ($GradleHome.ToLower() + "\"))
    Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" | Where-Object {
        $cl = "$($_.CommandLine)".ToLower()
        ($needles | Where-Object { $cl.Contains($_) }).Count -gt 0
    } | ForEach-Object {
        Write-Host "    stopping java pid $($_.ProcessId)"
        Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
    }
}

function Step([string]$name, [string[]]$tasks, [int]$minutes = 30) {
    $log = Join-Path $OutDir "logs\$name.log"
    Write-Host "==> $name : $($tasks -join ' ')"
    $p = Start-Process -FilePath "$Repo\gradlew.bat" -ArgumentList ($common + $tasks) -NoNewWindow -PassThru `
        -RedirectStandardOutput $log -RedirectStandardError "$log.err"
    $null = $p.Handle  # PowerShell 5.1 only reports ExitCode if the handle was opened
    if (-not $p.WaitForExit($minutes * 60 * 1000)) {
        Write-Host "    timeout after $minutes min, stopping"
        Stop-RepoJava
        $results[$name] = "TIMEOUT"
        return
    }
    $p.WaitForExit()
    $ok = ($p.ExitCode -eq 0) -and -not (Select-String -Path $log, "$log.err" -Pattern "BUILD FAILED" -Quiet)
    $results[$name] = if ($ok) { "PASS" } else { "FAIL ($($p.ExitCode))" }
    Write-Host "    $($results[$name])  log: $log"
}

function Set-Muted {
    New-Item -ItemType Directory -Force -Path $runDir | Out-Null
    $opts = Join-Path $runDir "options.txt"
    $lines = @()
    if (Test-Path $opts) { $lines = @(Get-Content $opts | Where-Object { $_ -notmatch '^(soundCategory_master|pauseOnLostFocus):' }) }
    $lines += "soundCategory_master:0.0"
    $lines += "pauseOnLostFocus:false"
    Set-Content -Path $opts -Value $lines
}

function Take-McLock {
    $deadline = (Get-Date).AddMinutes($LockWaitMinutes)
    while ($true) {
        if (Test-Path $Lock) {
            $age = ((Get-Date) - (Get-Item $Lock).LastWriteTime).TotalMinutes
            if ($age -ge 30) {
                Write-Host "    stale lock ($([int]$age) min): $(Get-Content $Lock -Raw -ErrorAction SilentlyContinue)"
                Remove-Item $Lock -Force -ErrorAction SilentlyContinue
            }
        }
        try {
            $fs = [System.IO.File]::Open($Lock, [System.IO.FileMode]::CreateNew, [System.IO.FileAccess]::Write)
            $b = [System.Text.Encoding]::UTF8.GetBytes("cosmetics $(Get-Date -Format o) pid $PID")
            $fs.Write($b, 0, $b.Length); $fs.Close()
            $script:haveLock = $true
            Write-Host "    took $Lock"
            return $true
        } catch {
            if ((Get-Date) -gt $deadline) {
                Write-Host "    lock wait timed out, held by: $(Get-Content $Lock -Raw -ErrorAction SilentlyContinue)"
                return $false
            }
            Start-Sleep -Seconds 20
        }
    }
}

function Drop-McLock {
    if ($script:haveLock -and (Test-Path $Lock)) {
        $c = Get-Content $Lock -Raw -ErrorAction SilentlyContinue
        if ($c -match "^cosmetics .* pid $PID") { Remove-Item $Lock -Force -ErrorAction SilentlyContinue; Write-Host "    released $Lock" }
    }
    $script:haveLock = $false
}

$stopLock = Join-Path $OutDir "lock.stop"
Remove-Item $stopLock -ErrorAction SilentlyContinue
try {
    $results["commit"] = (git rev-parse --short HEAD)
    if (-not $SkipTests) { Step "junit" @(":${TestVersion}:test") }
    # One Gradle run per version: Stonecutter's branch sync tasks trip Gradle's implicit-dependency
    # check when two Forge versions compile in the same invocation.
    if (-not $SkipCompile) { foreach ($v in $CompileVersions) { Step ("compile-" + ($v -replace ':', '-')) @(":${v}:compileJava") } }
    if (-not $SkipJar) {
        Step "jar" @(":${Version}:$jarTask")
        if ($results["jar"] -eq "PASS") {
            $jar = Get-ChildItem -LiteralPath $jarDir -Filter "quarkos_cosmetics-fabric-*+$Version.jar" -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending | Select-Object -First 1
            if ($jar) {
                Copy-Item $jar.FullName (Join-Path $OutDir "quarkos-cosmetics-fabric-ui.jar") -Force
                if (-not $NoDownloadsCopy) { Copy-Item $jar.FullName "$env:USERPROFILE\Downloads\quarkos-cosmetics-fabric-ui-$Version.jar" -Force }
                $results["jarFile"] = $jar.Name
            } else {
                $results["jar"] = "FAIL (release jar missing in $jarDir)"
            }
        }
    }
    if (-not $SkipHarness) {
        if (Take-McLock) {
            Set-Muted
            Remove-Item "$OutDir\screens\*" -Recurse -Force -ErrorAction SilentlyContinue
            if ($InSession) {
                $lockArgs = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File",
                    "`"$PSScriptRoot\quark-window-lock.ps1`"", "-Repo", "`"$Repo`"", "-StopFile", "`"$stopLock`"", "-ParentPid", $PID)
                if ($NoClickThrough) { $lockArgs += "-NoClickThrough" }
                Start-Process powershell.exe -ArgumentList $lockArgs -WindowStyle Hidden | Out-Null
            }
            $touch = Start-Job -ScriptBlock { param($l) while ($true) { Start-Sleep 60; if (Test-Path $l) { (Get-Item $l).LastWriteTime = Get-Date } } } -ArgumentList $Lock
            $hargs = @(":${Version}:runClient", "-Pharness=true", "-Pwide=true", "-Pshots=$OutDir\screens")
            if ($Only) { $hargs += "-Ponly=$Only" }
            Step "harness" $hargs $HarnessMinutes
            Stop-Job $touch -ErrorAction SilentlyContinue; Remove-Job $touch -Force -ErrorAction SilentlyContinue
            Stop-RepoJava
            Drop-McLock
            $results["screens"] = @(Get-ChildItem "$OutDir\screens" -Recurse -Filter *.png -ErrorAction SilentlyContinue).Count
        } else { $results["harness"] = "SKIPPED (lock busy)" }
    }
} finally {
    Stop-RepoJava
    Drop-McLock
    New-Item -ItemType File -Force -Path $stopLock | Out-Null
    $results | ConvertTo-Json | Set-Content (Join-Path $OutDir "summary.json")
    if ($InSession) { New-Item -ItemType File -Force -Path (Join-Path $OutDir "done.flag") | Out-Null; Stop-Transcript | Out-Null }
    Write-Host ""
    $results.GetEnumerator() | ForEach-Object { Write-Host ("{0,-10} {1}" -f $_.Key, $_.Value) }
}
