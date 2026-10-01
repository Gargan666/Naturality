param(
    [string]$CompilerJar,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
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
    & ./gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not prepare the Java audit classpath.' }
    $classpath = Get-Content -Raw 'build/java-audit/classpath.txt'
    & $java -jar $CompilerJar -25 -proc:none -d none -classpath $classpath `
        -properties '.settings/org.eclipse.jdt.core.prefs' -log 'build/java-audit/diagnostics.xml' `
        src/main/java src/client/java *> 'build/java-audit/diagnostics.log'
    $compilerExit = $LASTEXITCODE
    [xml]$report = Get-Content -Raw 'build/java-audit/diagnostics.xml'
    $problems = @($report.compiler.sources.source.problems.problem | Where-Object { $_ -and $_.severity -in 'ERROR', 'WARNING' })
    if ($compilerExit -ne 0 -or $problems.Count -gt 0) {
        Get-Content 'build/java-audit/diagnostics.log'
        throw "Java audit failed: $($problems.Count) errors/warnings."
    }
    Write-Output 'Java audit passed: zero production errors or warnings.'
} finally {
    Pop-Location
}
