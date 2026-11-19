# Run the Fabric release capture on Quark's private Windows desktop.
[CmdletBinding()]
param(
    [ValidateSet('Launch', 'Doctor', 'Status', 'Wait', 'Cleanup', 'Worker')]
    [string]$Command = 'Launch',
    [ValidateSet('26.3', '1.21.11')]
    [string]$Version = '26.3',
    [string]$OutputRoot = 'D:\dev\cos-verify-out',
    [string]$RunDir,
    [string]$Only = '',
    [string]$Label = '',
    [int]$LockWaitMinutes = 90,
    [int]$ClientMinutes = 35,
    [switch]$BuildChecks,
    [switch]$BuildOnly
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$tools = Join-Path $PSScriptRoot 'verify-models'
$jdk = 'D:\dev\jdks\jdk-25.0.5+8'
$gradleHome = 'D:\dev\cosmetics-gradle'
$lock = 'D:\dev\mc-test.lock'
if ($env:OS -ne 'Windows_NT' -or $env:COMPUTERNAME -notmatch '^QUARK(?:[-.].*)?$') {
    throw 'Run verify-models.ps1 on Quark.'
}

function Write-JsonAtomic($value, [string]$path) {
    $temporary = "$path.tmp"
    $value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $temporary -Encoding UTF8
    Move-Item -LiteralPath $temporary -Destination $path -Force
}

function Read-RunFile([string]$name) {
    Get-Content -LiteralPath (Join-Path $RunDir $name) -Raw | ConvertFrom-Json
}

function Remove-RunTask($request) {
    $taskName = "CosmeticsModels-$($request.token)"
    if ($request.token -notmatch '^[0-9a-f]{12}$' -or $request.task -ne $taskName) {
        throw 'The request does not name a Cosmetics Models task.'
    }
    $directory = (Resolve-Path -LiteralPath $RunDir).Path
    $suffix = ' -Command Worker -RunDir "' + $directory + '"'
    $deadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        $task = Get-ScheduledTask -TaskName $taskName -TaskPath '\' -ErrorAction SilentlyContinue
        if (-not $task) { return }
        $action = $task.Actions
        if ([IO.Path]::GetFileName($action.Execute) -ne 'conhost.exe' -or
            -not $action.Arguments.EndsWith($suffix, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Task $taskName does not belong to $directory."
        }
        if ($task.State -eq 'Ready') {
            Unregister-ScheduledTask -InputObject $task -Confirm:$false
            return
        }
        Start-Sleep -Seconds 1
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Task $taskName has not stopped."
}

function Doctor {
    foreach ($entry in @("$jdk\bin\java.exe", "$repo\gradlew.bat", $gradleHome)) {
        if (-not (Test-Path -LiteralPath $entry)) { throw "Missing $entry" }
    }
    $python = (Get-Command python.exe -CommandType Application | Select-Object -First 1).Source
    & $python -B -c 'from PIL import Image; import numpy'
    if ($LASTEXITCODE -ne 0) { throw 'Quark Python requires Pillow and NumPy.' }
    $branch = & git -C $repo branch --show-current
    $revision = & git -C $repo rev-parse HEAD
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the Quark checkout.' }
    if (-not (Get-CimInstance Win32_ComputerSystem).UserName) { throw 'Quark needs a signed-in desktop session.' }
    $owner = Get-Item -LiteralPath $lock -ErrorAction SilentlyContinue
    $lockAge = if ($owner) { [Math]::Floor(([DateTime]::UtcNow - $owner.LastWriteTimeUtc).TotalMinutes) } else { $null }
    [ordered]@{ host = $env:COMPUTERNAME; branch = $branch; commit = $revision; python = $python; lockAgeMinutes = $lockAge }
}

if ($Command -eq 'Doctor') {
    Doctor | ConvertTo-Json
    if ($RunDir) {
        $request = Read-RunFile 'request.json'
        $task = Get-ScheduledTask -TaskName $request.task -ErrorAction SilentlyContinue
        $state = if ($task) { [string]$task.State } else { 'removed' }
        $raw = Join-Path $RunDir 'raw'
        $log = Join-Path $RunDir 'logs\client.log'
        $ready = if (Test-Path $log) { Select-String -Path $log -Pattern 'studio ready' -Quiet } else { $false }
        [ordered]@{ task = $request.task; state = $state; ready = $ready; raw = $raw; expectedCommit = $request.commit } | ConvertTo-Json
    }
    exit 0
}

if ($Command -eq 'Cleanup') {
    if (-not $RunDir) { throw 'Pass -RunDir from the launch output.' }
    Remove-RunTask (Read-RunFile 'request.json')
    exit 0
}

if ($Command -in 'Status', 'Wait') {
    if (-not $RunDir) { throw 'Pass -RunDir from the launch output.' }
    $request = Read-RunFile 'request.json'
    do {
        if (Test-Path -LiteralPath (Join-Path $RunDir 'done.json')) {
            $done = Read-RunFile 'done.json'
            $done | ConvertTo-Json -Depth 12
            if ($Command -eq 'Wait') {
                Remove-RunTask $request
                exit $done.exitCode
            }
            exit 0
        }
        $task = Get-ScheduledTask -TaskName $request.task -ErrorAction SilentlyContinue
        if (-not $task -or $task.State -eq 'Ready') {
            # The scheduler can report Ready until the action starts.
            $age = ([DateTime]::UtcNow - [DateTime]$request.createdUtc).TotalSeconds
            if (-not $task -or $age -gt 45) {
                if (Test-Path -LiteralPath (Join-Path $RunDir 'done.json')) { continue }
                throw "Task $($request.task) ended before writing done.json."
            }
        }
        if ($Command -eq 'Status') {
            Get-Content -LiteralPath (Join-Path $RunDir 'logs\run.log') -Tail 6 -ErrorAction SilentlyContinue
            exit 0
        }
        Start-Sleep -Seconds 5
    } while ($true)
}

if ($Command -eq 'Launch') {
    $health = Doctor
    if ($Only -and $Only -notmatch '^[a-z0-9_]+(,[a-z0-9_]+)*$') { throw 'Use comma-separated catalog IDs without the namespace.' }
    if ($RunDir) { throw 'Set -OutputRoot to choose the parent for a fresh run.' }
    if ($OutputRoot -match '["%\r\n]') { throw 'Invalid output root.' }
    $null = New-Item -ItemType Directory -Path $OutputRoot -Force
    $stamp = Get-Date -Format yyyyMMdd-HHmm
    $token = [Guid]::NewGuid().ToString('N').Substring(0, 12)
    $RunDir = Join-Path $OutputRoot $stamp
    try {
        $null = New-Item -ItemType Directory -Path $RunDir
    } catch [System.IO.IOException] {
        $RunDir = Join-Path $OutputRoot "$stamp-$token"
        $null = New-Item -ItemType Directory -Path $RunDir
    }
    $RunDir = (Resolve-Path -LiteralPath $RunDir).Path
    foreach ($dir in @('raw', 'evidence', 'logs', 'game')) {
        $null = New-Item -ItemType Directory -Path (Join-Path $RunDir $dir)
    }
    $taskName = "CosmeticsModels-$token"
    $request = [ordered]@{
        task = $taskName; token = $token; commit = $health.commit; branch = $health.branch
        createdUtc = [DateTime]::UtcNow.ToString('o'); version = $Version; only = $Only; label = $Label
        lockWaitMinutes = $LockWaitMinutes; clientMinutes = $ClientMinutes
        buildChecks = [bool]$BuildChecks; buildOnly = [bool]$BuildOnly
    }
    Write-JsonAtomic $request (Join-Path $RunDir 'request.json')
    $shell = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"
    $argument = "--headless `"$shell`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$PSCommandPath`" -Command Worker -RunDir `"$RunDir`""
    $action = New-ScheduledTaskAction -Execute "$env:SystemRoot\System32\conhost.exe" -Argument $argument -WorkingDirectory $repo
    $principal = New-ScheduledTaskPrincipal -UserId ([Security.Principal.WindowsIdentity]::GetCurrent().Name) -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit (New-TimeSpan -Hours 4)
    $owned = $false
    try {
        Register-ScheduledTask -TaskName $taskName -Action $action -Principal $principal -Settings $settings | Out-Null
        $owned = $true
        Start-ScheduledTask -TaskName $taskName
    } catch {
        if ($owned) { Unregister-ScheduledTask -TaskName $taskName -Confirm:$false }
        Write-JsonAtomic @{ exitCode = 1; error = $_.Exception.Message } (Join-Path $RunDir 'done.json')
        throw
    }
    [ordered]@{ runDir = $RunDir; task = $taskName; commit = $health.commit } | ConvertTo-Json
    exit 0
}

$request = Read-RunFile 'request.json'
$runLog = Join-Path $RunDir 'logs\run.log'
$raw = Join-Path $RunDir 'raw'
$game = Join-Path $RunDir 'game'
$plan = Join-Path $RunDir 'plan.json'
$lockOwner = "cosmetics verify-models $($request.createdUtc) pid $PID"
$lockHandle = $null
$lastTouch = [DateTime]::MinValue

function Log([string]$message) {
    Add-Content -LiteralPath $runLog -Value "$([DateTime]::UtcNow.ToString('s'))Z $message"
}

function Invoke-PythonStep([string]$name, [string[]]$parameters) {
    $log = Join-Path $RunDir "logs\$name.log"
    $oldPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $health.python -B (Join-Path $tools "$name.py") @parameters *> $log
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $oldPreference }
    if ($code -ne 0) { throw "$name failed ($code). See $log" }
    Log (Get-Content -LiteralPath $log -Tail 1)
}

function Enter-CaptureLock {
    $deadline = [DateTime]::UtcNow.AddMinutes($request.lockWaitMinutes)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $handle = [IO.File]::Open($lock, 'CreateNew', 'ReadWrite', 'ReadWrite')
        } catch [System.IO.IOException] {
            $entry = Get-Item -LiteralPath $lock -ErrorAction SilentlyContinue
            if ($entry -and (([DateTime]::UtcNow - $entry.LastWriteTimeUtc).TotalMinutes -ge 30)) {
                try {
                    $stale = [IO.File]::Open($lock, 'Open', 'ReadWrite', 'Delete')
                    try {
                        if ((([DateTime]::UtcNow - [IO.File]::GetLastWriteTimeUtc($lock)).TotalMinutes -ge 30)) {
                            $archive = Join-Path $RunDir ("stale-lock-" + [Guid]::NewGuid().ToString('N'))
                            [IO.File]::Move($lock, $archive)
                            Log 'Archived stale lock older than 30 minutes.'
                        }
                    } finally { $stale.Dispose() }
                } catch [System.IO.IOException] {
                    # A holder changed or still has the file open.
                }
            }
            Log 'Waiting for the Minecraft test lock.'
            Start-Sleep -Seconds 15
            continue
        }
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($lockOwner)
            $handle.Write($bytes, 0, $bytes.Length)
            $handle.Flush()
            Log 'Acquired the Minecraft test lock.'
            return $handle
        } catch {
            $handle.Dispose()
            throw
        }
    }
    throw 'Timed out waiting for the Minecraft test lock.'
}

function Update-CaptureLock {
    if ($lockHandle -and ([DateTime]::UtcNow - $script:lastTouch).TotalSeconds -ge 30) {
        $lockHandle.Position = 0
        $reader = [IO.StreamReader]::new($lockHandle, [Text.Encoding]::UTF8, $false, 1024, $true)
        try {
            if ($reader.ReadToEnd() -ne $lockOwner) { throw 'Minecraft test lock ownership changed.' }
        } finally { $reader.Dispose() }
        [IO.File]::SetLastWriteTimeUtc($lock, [DateTime]::UtcNow)
        $script:lastTouch = [DateTime]::UtcNow
    }
}

function Exit-CaptureLock {
    if (-not $lockHandle) { return }
    $script:lockHandle.Dispose()
    $script:lockHandle = $null
    try {
        if ((Test-Path -LiteralPath $lock) -and [IO.File]::ReadAllText($lock) -eq $lockOwner) {
            Remove-Item -LiteralPath $lock
            Log 'Released the Minecraft test lock.'
        }
    } catch [IO.IOException] {
        Log 'The lock is held by another process during cleanup.'
    }
}

function Invoke-GradleJob([string]$name, [string]$targets, [int]$minutes) {
    $batch = Join-Path $RunDir "$name.cmd"
    $log = Join-Path $RunDir "logs\$name.log"
    @(
        '@echo off'
        "call `"$repo\gradlew.bat`" --no-daemon --offline --console=plain --configure-on-demand --no-configuration-cache $targets >`"$log`" 2>&1"
        'exit /b %errorlevel%'
    ) | Set-Content -LiteralPath $batch -Encoding Ascii
    $desktop = "CosModels-$($request.token)"
    $line = "`"$env:ComSpec`" /d /c call `"$batch`""
    $job = [CosmeticsHiddenJob]::new($desktop, $line, $repo)
    Log "$name pid=$($job.Pid) desktop=$($job.Desktop)"
    $deadline = [DateTime]::UtcNow.AddMinutes($minutes)
    $windowPid = 0
    try {
        while (-not $job.Wait(5000)) {
            Update-CaptureLock
            if ($name -eq 'client' -and -not $windowPid) {
                $windowPid = $job.MinecraftWindowProcess()
                if ($windowPid) { Log "Minecraft window pid=$windowPid desktop=$($job.Desktop)" }
            }
            if ([DateTime]::UtcNow -ge $deadline) { throw "$name timed out. See $log" }
        }
        $code = $job.ExitCode()
        if ($name -eq 'client' -and $code -eq 0 -and -not $windowPid) { throw 'Minecraft window was not observed on the private desktop.' }
        $code
    } finally {
        $job.Dispose()
        Log "Closed $name job."
    }
}

$result = [ordered]@{ exitCode = 1; commit = $request.commit; version = $request.version; label = $request.label }
try {
    $health = Doctor
    if ($health.commit -ne $request.commit) { throw 'Quark HEAD changed after launch.' }
    $env:JAVA_HOME = $jdk
    $env:GRADLE_USER_HOME = $gradleHome
    $env:Path = "$jdk\bin;$env:Path"
    . (Join-Path $tools 'HiddenDesktop.ps1')
    Initialize-CosmeticsHiddenDesktop
    if (-not $request.buildOnly) {
        $select = if ($request.only) { @('--only', $request.only) } else { @() }
        Invoke-PythonStep 'plan' (@($plan) + $select)
        @('soundCategory_master:0.0', 'pauseOnLostFocus:false', 'fullscreen:false', 'tutorialStep:none', 'realmsNotifications:false') |
            Set-Content -LiteralPath (Join-Path $game 'options.txt') -Encoding Ascii
        $lockHandle = Enter-CaptureLock
        try {
            $targets = ":$($request.version):runClient -Pharness=true -Pwide=true `"-Pshots=$raw`" `"-PreleaseGameDir=$game`" `"-PreleasePlan=$plan`""
            $result.clientExitCode = Invoke-GradleJob 'client' $targets $request.clientMinutes
        } finally { Exit-CaptureLock }
        $clientLog = Join-Path $RunDir 'logs\client.log'
        if ($result.clientExitCode -ne 0 -or -not (Select-String -Path $clientLog -Pattern 'RELEASE_CAPTURE_DONE' -Quiet)) {
            throw "The client failed to complete. See $clientLog"
        }
        Invoke-PythonStep 'report' @($RunDir)
        $result.evidence = Join-Path $RunDir 'evidence'
    }
    if ($request.buildOnly -or $request.buildChecks) {
        $result.build = [ordered]@{}
        foreach ($target in @('1.21.11:test', '26.3:compileJava', '1.21.11:compileJava', '1.21.1:compileJava', 'forge:1.21.11:compileJava', 'forge:1.21.1:compileJava')) {
            $name = $target -replace ':', '-'
            $result.build[$target] = Invoke-GradleJob $name ":$target" 25
        }
        if (@($result.build.Values | Where-Object { $_ -ne 0 }).Count -gt 0) { throw 'A Gradle check failed. See logs.' }
    }
    $result.exitCode = 0
} catch {
    $result.error = $_.Exception.Message
    Log "Failed. $($result.error)"
} finally {
    Exit-CaptureLock
    Write-JsonAtomic $result (Join-Path $RunDir 'done.json')
    try {
        Unregister-ScheduledTask -TaskName $request.task -Confirm:$false
    } catch {
        Log "Task unregister needs the launching session. $($_.Exception.Message)"
    }
}
exit $result.exitCode
