# Quiet Signal release verification build.
# Runs the checks required for the v1.0.0 open-source release and writes
# everything to release_build_out.txt (gitignored).
#
# Usage: powershell -File tools/release_build.ps1

$env:JAVA_HOME = "E:\Android\jbr"
$env:GRADLE_USER_HOME = "X:\Gradle"
$project = "E:\dev\tailscale-companion"
$log = Join-Path $project "release_build_out.txt"

$gradle = "X:\Gradle\wrapper\dists\gradle-8.11.1-bin\bpt9gzteqjrbo1mjrsomdt32c\gradle-8.11.1\bin\gradle.bat"
if (-not (Test-Path $gradle)) { $gradle = Join-Path $project "gradlew.bat" }

"=== Quiet Signal release build $(Get-Date -Format s) ===" | Set-Content $log
"gradle: $gradle" | Add-Content $log
"java:   $(& "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Select-Object -First 1)" | Add-Content $log

$tasks = @(
    "compileDebugKotlin",
    "compileReleaseKotlin",
    "testDebugUnitTest",
    "testReleaseUnitTest",
    "lint",
    "assembleDebug",
    "assembleRelease"
)

foreach ($task in $tasks) {
    "`n===== TASK $task =====" | Add-Content $log
    & $gradle -p $project $task --console=plain --stacktrace 2>&1 | Add-Content $log
    $code = $LASTEXITCODE
    "===== EXIT $code ($task) =====" | Add-Content $log
    if ($code -ne 0) {
        # 'clean' can legitimately fail while Android Studio holds file locks on
        # build outputs; the remaining tasks still prove the release build works.
        if ($task -eq "clean") {
            "NOTE: clean failed (exit $code) - continuing, likely a file lock held by Android Studio" | Add-Content $log
        } else {
            "BUILD STOPPED: task '$task' failed with exit code $code" | Add-Content $log
            break
        }
    }
}
"`n=== finished $(Get-Date -Format s) ===" | Add-Content $log
