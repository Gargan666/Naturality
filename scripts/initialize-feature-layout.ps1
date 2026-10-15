# Recreate the planned Java folder scaffold. Existing source files are never moved or changed.
# Empty directories are not stored by Git; this list preserves them without per-folder placeholders.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent

# Matching ownership paths for features with common and client code.
$shared = @(
    'api/worlds', 'api/geometry', 'bootstrap', 'config',
    'blocks/chorus', 'blocks/fire', 'blocks/snow',
    'effects/confusion', 'effects/distortion', 'effects/insanity',
    'entities/maddened', 'entities/ghasts', 'entities/fish', 'entities/arrows', 'entities/fireballs',
    'particles/sand',
    'portals/nether', 'portals/end',
    'sky/shared/aurora', 'sky/overworld/bloodmoon', 'sky/overworld/proximity', 'sky/overworld/rainbow',
    'systems/weather/framework', 'systems/weather/overworld/rain', 'systems/weather/overworld/snow',
    'systems/weather/overworld/lightning', 'systems/weather/end/framework',
    'systems/weather/end/rise', 'systems/weather/end/starfall', 'systems/weather/wind',
    'systems/skyevents/framework', 'systems/gravity', 'systems/villagerwork/framework',
    'systems/villagerwork/fishing', 'systems/reputation', 'systems/sleep', 'systems/navigation',
    'mixins/blocks', 'mixins/entities', 'mixins/items', 'mixins/particles', 'mixins/world'
)

$common = @(
    'api/random', 'blocks/lyrenheath', 'entities/creepers', 'entities/crowdpanic',
    'systems/villagerwork/professions', 'systems/villagerwork/bread',
    'systems/villagerwork/gates', 'systems/villagerwork/shelter',
    'worldgen/overworld/clover', 'worldgen/overworld/moss', 'worldgen/overworld/waterfalls',
    'worldgen/end/terrain', 'worldgen/end/materials', 'worldgen/end/lirestonecrags', 'worldgen/end/endwilds'
)

$client = @(
    'api/rendering', 'audio/ambience/caves', 'audio/ambience/end', 'audio/underwater', 'effects/ambienthorror',
    'entities/zombies', 'entities/floatingmobtilt', 'entities/endcrystals',
    'fluids/shared', 'fluids/water', 'fluids/lava',
    'particles/smoke', 'particles/flame', 'particles/leaves', 'particles/explosions',
    'particles/potionclouds', 'particles/deathsmoke',
    'rendering/lighting', 'rendering/shadows', 'rendering/fog', 'rendering/culling',
    'rendering/distantdetail', 'rendering/glint', 'rendering/breaking',
    'sky/shared/atmosphere', 'sky/overworld/sunbeams', 'sky/overworld/sunset',
    'sky/shared/clouds', 'sky/overworld/meteors', 'sky/end/atmosphere', 'sky/end/flashes',
    'ui/menus/previews', 'ui/menus/title', 'ui/settings', 'ui/camera', 'ui/hud',
    'mixins/menus', 'mixins/rendering'
)

# Only known hook owners get a mixins folder now; create further ones when their hooks are assigned.
$sharedHookOwners = @(
    'api/worlds', 'blocks/chorus', 'blocks/fire', 'blocks/snow',
    'effects/confusion', 'effects/distortion', 'effects/insanity',
    'entities/maddened', 'entities/ghasts', 'entities/fish',
    'portals/nether', 'portals/end', 'sky/overworld/bloodmoon',
    'systems/weather/framework', 'systems/weather/wind', 'systems/gravity',
    'systems/villagerwork/framework', 'systems/reputation', 'systems/sleep', 'systems/navigation'
)
$commonHookOwners = @('worldgen/end/terrain', 'worldgen/end/materials')
$clientHookOwners = @(
    'sky/overworld/sunbeams', 'sky/shared/clouds', 'sky/end/atmosphere',
    'fluids/water', 'fluids/lava', 'rendering/lighting', 'rendering/shadows',
    'rendering/fog', 'rendering/culling', 'rendering/glint', 'rendering/breaking',
    'ui/menus/previews', 'ui/camera', 'ui/hud'
)

$layouts = @{
    'src/main/java/naturality' = $shared + $common + @($sharedHookOwners + $commonHookOwners | ForEach-Object { "$_/mixins" })
    'src/client/java/naturality/client' = $shared + $client + @($sharedHookOwners + $clientHookOwners | ForEach-Object { "$_/mixins" })
}
foreach ($relativeRoot in ($layouts.Keys | Sort-Object)) {
    $sourceRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot $relativeRoot))
    $directories = @($layouts[$relativeRoot] | Sort-Object -Unique)
    foreach ($relativePath in $directories) {
        $destination = [IO.Path]::GetFullPath((Join-Path $sourceRoot $relativePath))
        if (-not $destination.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw "Layout path escapes its source root: $relativePath"
        }
        [IO.Directory]::CreateDirectory($destination) | Out-Null
    }
    Write-Output ("Prepared {0} destination folders under {1}" -f $directories.Count, $relativeRoot)
}
