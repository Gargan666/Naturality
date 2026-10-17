param(
    [string]$CompilerJar,
    [switch]$Offline,
    [switch]$SkipResourceProcessing,
    [ValidateSet('All', 'Production')]
    [string]$Scope = 'All'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    $reportDir = Join-Path 'build/java-audit' $Scope.ToLowerInvariant()
    New-Item -ItemType Directory -Path $reportDir -Force | Out-Null
    $summaryPath = Join-Path $reportDir 'summary.json'
    @{ scope = $Scope; completed = $false; startedAt = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath $summaryPath
    if (-not $CompilerJar) {
        $extensions = Join-Path $env:USERPROFILE '.vscode/extensions'
        $CompilerJar = Get-ChildItem -Path "$extensions/redhat.java-*/server/plugins/org.eclipse.jdt.core.compiler.batch_*.jar" |
            Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $CompilerJar -or -not (Test-Path -LiteralPath $CompilerJar)) {
        throw 'Pass -CompilerJar with an Eclipse Java compiler supporting Java 25, or use the installed VS Code Java extension.'
    }
    if (Test-Path '.tools/jdk-25/bin/java.exe') {
        $env:JAVA_HOME = (Resolve-Path '.tools/jdk-25').Path
    }
    $java = Join-Path $env:JAVA_HOME 'bin/java.exe'
    $gradleArgs = @('writeJavaAuditClasspath', '--console=plain')
    if ($Offline) { $gradleArgs += '--offline' }
    # Static Java analysis does not need to rewrite assets used by a live client.
    # Source generators and Java compilation still run for every source set.
    if ($SkipResourceProcessing) { $gradleArgs += @('-x', 'processResources', '-x', 'processClientResources') }
    & ./gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the Java audit classpath.' }
    $sourceSets = Get-Content -Raw 'build/java-audit/source-sets.json' | ConvertFrom-Json
    if ($Scope -eq 'Production') { $sourceSets = @($sourceSets | Where-Object { $_.name -in 'main', 'client' }) }
    $problems = [System.Collections.Generic.List[object]]::new()
    $results = [System.Collections.Generic.List[object]]::new()
    foreach ($sourceSet in $sourceSets) {
        $roots = @($sourceSet.sourceDirs | Where-Object { Test-Path -LiteralPath $_ -PathType Container })
        if (-not $roots.Count -or -not @($roots | ForEach-Object { Get-ChildItem -LiteralPath $_ -Filter '*.java' -Recurse -File }).Count) { continue }
        $xmlPath = Join-Path $reportDir ($sourceSet.name + '.xml')
        $logPath = Join-Path $reportDir ($sourceSet.name + '.log')
        if (Test-Path -LiteralPath $xmlPath) { Remove-Item -LiteralPath $xmlPath }
        # Let native stderr finish before reading XML, including on Windows PowerShell.
        $ErrorActionPreference = 'Continue'
        & $java -jar $CompilerJar -25 -proc:none -d none -classpath $sourceSet.classpath `
            -properties '.settings/org.eclipse.jdt.core.prefs' -log $xmlPath @roots *> $logPath
        $compilerExit = $LASTEXITCODE
        $ErrorActionPreference = 'Stop'
        if (-not (Test-Path -LiteralPath $xmlPath)) { throw "Compiler report missing; see $logPath" }
        [xml]$report = Get-Content -Raw -LiteralPath $xmlPath
        $before = $problems.Count
        foreach ($source in $report.compiler.sources.source) {
            foreach ($problem in $source.problems.problem) {
                if (-not $problem -or $problem.severity -notin 'ERROR', 'WARNING') { continue }
                $path = [string]$source.path
                if ($path.StartsWith($projectRoot + '\', [StringComparison]::OrdinalIgnoreCase)) {
                    $path = $path.Substring($projectRoot.Length + 1)
                }
                $problems.Add([pscustomobject]@{
                    sourceSet = $sourceSet.name; path = $path.Replace('\', '/'); line = [int]$problem.line
                    severity = [string]$problem.severity; id = [string]$problem.id
                    message = [string]$problem.message.value; context = [string]$problem.source_context.value
                })
            }
        }
        $results.Add([pscustomobject]@{ sourceSet = $sourceSet.name; exitCode = $compilerExit; problems = $problems.Count - $before })
    }
    if (-not $results.Count) { throw 'No Java source sets were audited.' }
    $problemPath = Join-Path $reportDir 'problems.json'
    ConvertTo-Json -InputObject @($problems.ToArray()) -Depth 4 | Set-Content -Encoding UTF8 -LiteralPath $problemPath
    $errors = @($problems | Where-Object { $_.severity -eq 'ERROR' }).Count
    $warnings = @($problems | Where-Object { $_.severity -eq 'WARNING' }).Count
    $failed = @($results | Where-Object { $_.exitCode -ne 0 }).Count -gt 0 -or $problems.Count -gt 0
    $summary = [ordered]@{
        scope = $Scope; completed = $true; passed = -not $failed; finishedAt = (Get-Date).ToString('o')
        errors = $errors; warnings = $warnings; sourceSets = @($results.ToArray())
        categories = @($problems | Group-Object id | Sort-Object Count -Descending | ForEach-Object {
            [pscustomobject]@{ id = $_.Name; count = $_.Count }
        })
    }
    ConvertTo-Json -InputObject $summary -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath $summaryPath
    $results | Format-Table -AutoSize | Out-String | Write-Output
    Write-Output "Reports: $summaryPath and $problemPath"
    if ($failed) { throw "Java audit ($Scope) failed: $errors errors, $warnings warnings. See the saved reports." }
    Write-Output "Java audit ($Scope) passed: zero errors or warnings across $($results.Count) source sets."
} finally {
    Pop-Location
}
