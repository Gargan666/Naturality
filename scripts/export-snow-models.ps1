param([Parameter(Mandatory=$true)][string]$MinecraftClientJar)
$ErrorActionPreference='Stop'
$archive=[IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $MinecraftClientJar))
$models=@{}
$states=@{}
try {
    foreach($entry in $archive.Entries) {
        if($entry.FullName -match '^assets/([^/]+)/blockstates/(.+)\.json$') {
            $key=$Matches[1]+':'+$Matches[2]
            $reader=[IO.StreamReader]::new($entry.Open())
            try { $states[$key]=ConvertFrom-Json -InputObject $reader.ReadToEnd() -AsHashtable }
            finally { $reader.Dispose() }
        } elseif($entry.FullName -match '^assets/([^/]+)/models/(block/.+)\.json$') {
            $key=$Matches[1]+':'+$Matches[2]
            $reader=[IO.StreamReader]::new($entry.Open())
            try { $model=ConvertFrom-Json -InputObject $reader.ReadToEnd() -AsHashtable }
            finally { $reader.Dispose() }
            foreach($unused in @('textures','display','ambientocclusion')) { $model.Remove($unused) }
            $models[$key]=$model
        }
    }
} finally { $archive.Dispose() }
$output=Join-Path $PSScriptRoot '../src/main/resources/data/naturality/snow_models.json'
[IO.File]::WriteAllText($output,(@{blockstates=$states;models=$models}|ConvertTo-Json -Depth 80 -Compress))
Write-Output "Exported $($states.Count) block states and $($models.Count) model definitions."
