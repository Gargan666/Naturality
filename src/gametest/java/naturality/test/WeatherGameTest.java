package naturality.test;

import naturality.weather.*;
import naturality.config.NaturalityServerConfig;
import naturality.client.weather.WindRendering;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

public final class WeatherGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var config = NaturalityServerConfig.get();
        boolean[] oldTransparency = new boolean[1];
        context.runOnClient(client -> oldTransparency[0] = client.options.improvedTransparency().get());
        var p = new WeatherProfile(true);
        p.overrideRain = p.overrideWind = p.overrideTemperature = true;
        p.rain = 50; p.wind = 100; p.temperature = 0;
        try {
            checkMath();
            try (var world = context.worldBuilder().create()) {
                var server = world.getServer();
                server.runOnServer(s -> WeatherWorldData.get(s).setProfile("minecraft:overworld", p));
                server.runCommand("gamemode spectator @a");
                server.runCommand("time set 6000");
                server.runCommand("fill -12 100 -12 12 100 12 grass_block");
                server.runCommand("fillbiome -16 96 -16 15 127 15 plains");
                server.runCommand("fill -4 101 0 4 101 4 short_grass");
                server.runCommand("fill -4 104 6 4 105 8 oak_leaves[persistent=true]");
                server.runCommand("fill -4 106 6 4 106 8 snow");
                server.runCommand("fill -4 102 0 4 102 4 snow");
                server.runCommand("setblock -9 100 3 stone_slab[waterlogged=true]");
                server.runCommand("fill -8 101 3 -8 104 3 sugar_cane");
                server.runCommand("fill 6 101 3 8 104 3 stone");
                server.runCommand("fill 6 101 2 8 104 2 vine[south=true]");
                server.runCommand("tp @a 0 103 -7 0 8");
                // The world already has automatic weather when its profile is replaced.
                // Manual temperature converges at 0.25 per tick (up to 400 ticks).
                context.waitTicks(410);
                server.runOnServer(s -> {
                    var level = s.overworld();
                    var state = WeatherSystem.state(level);
                    check(state != null && state.rain() == 50 && state.wind() == 100 && state.temperature() == 0, "Independent server overrides");
                    var frozenPos = new BlockPos(10,101,10);
                    level.setBlockAndUpdate(frozenPos, Blocks.SNOW.defaultBlockState());
                    check(!WeatherThaw.thawAt(level,frozenPos), "Cold weather does not thaw snow" );
                    check(level.isRaining() && level.isThundering(), "50 must be a vanilla thunderstorm");
                    check(level.getRainLevel(1) == 1 && level.getThunderLevel(1) == 1, "Vanilla lighting levels remain bounded");
                    var pos = new BlockPos(0, 101, 0);
                    var registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
                    var plains = registry.getOrThrow(Biomes.PLAINS).value();
                    check(WeatherSystem.precipitation(level, plains, pos) == Biome.Precipitation.SNOW, "Cold plains snow");
                    check(WeatherSystem.precipitation(level, registry.getOrThrow(Biomes.DESERT).value(), pos) == Biome.Precipitation.NONE, "Cold desert stays dry");
                    check(WeatherSystem.precipitation(level, registry.getOrThrow(Biomes.JUNGLE).value(), pos) == Biome.Precipitation.RAIN, "Tropics stay rainy");
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    level.tickPrecipitation(pos);
                    check(level.getBlockState(pos).is(Blocks.SNOW), "Temperature produces actual snow accumulation");
                    var firePos=pos;
                    level.setBlockAndUpdate(firePos,Blocks.FIRE.defaultBlockState());
                    level.tickPrecipitation(firePos);
                    check(level.getBlockState(firePos).is(Blocks.SNOW)
                            && level.getBlockState(firePos.above()).isAir(),
                        "Weather replaces fire with snow at the same position");
                    var sign = new BlockPos(7,101,7);
                    level.setBlockAndUpdate(sign,Blocks.OAK_SIGN.defaultBlockState());
                    level.getGameRules().set(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT,3,s);
                    level.tickPrecipitation(sign);
                    check(level.getBlockState(sign.above()).is(Blocks.SNOW),"Snowfall coats signs without replacing them");
                    level.tickPrecipitation(sign);
                    check(level.getBlockState(sign.above()).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==2,
                        "Snowfall continues accumulating on non-motion-blocking supports");
                    for(int i=0;i<12;i++) level.tickPrecipitation(sign);
                    check(level.getBlockState(sign.above()).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==2,
                        "Rain 50 caps irregular-support accumulation at two layers");
                    for(int i=0;i<12;i++) level.tickPrecipitation(pos);
                    check(level.getBlockState(pos).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==2,
                        "Rain 50 caps ordinary accumulation at two layers");
                    check(WeatherSnow.accumulationLimit(level,0)==0,"Explicit snow accumulation disable is respected");
                    var water = new BlockPos(10, 100, 10);
                    level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                    check(plains.shouldFreeze(level, water), "Cold temperate water can freeze");
                    check(WeatherSystem.state(s.getLevel(Level.NETHER)) == null && WeatherSystem.state(s.getLevel(Level.END)) == null, "Other dimension pools disabled");
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> {
                    var state = WeatherSystem.state(client.level);
                    check(state != null && state.temperature() == 0 && state.wind() == 100, "Client receives independent channels");
                    check(client.level.getPrecipitationAt(new BlockPos(0, 104, 0)) == Biome.Precipitation.SNOW, "Renderer uses snow climate");
                    check(client.getSoundManager().getSoundEvent(naturality.NaturalitySounds.WIND_WEAK.location()) != null, "Weak wind registered");
                    check(client.getSoundManager().getSoundEvent(naturality.NaturalitySounds.WIND_STRONG.location()) != null, "Strong wind registered");
                    var cane=Blocks.SUGAR_CANE.defaultBlockState();
                    var root=new BlockPos(-8,101,3);
                    check(WindRendering.vertexTag(client.level,root,cane,.5F,0,.5F)==0,"Only cane root is pinned");
                    for(int h=0;h<3;h++) {
                        int top=WindRendering.vertexTag(client.level,root.above(h),cane,.5F,1,.5F);
                        int bottom=WindRendering.vertexTag(client.level,root.above(h+1),cane,.5F,0,.5F);
                        check(top==h+1 && top==bottom,"Cane joints share increasing height above the root");
                    }
                    check(WindRendering.tag(Blocks.VINE.defaultBlockState())==100
                        && WindRendering.tag(Blocks.WEEPING_VINES.defaultBlockState())==100
                        && WindRendering.tag(Blocks.WEEPING_VINES_PLANT.defaultBlockState())==100,
                        "Wall and growing vine segments share connected wind mode");
                    var wallVine=Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.SOUTH,true);
                    check(WindRendering.vertexTag(client.level,new BlockPos(6,102,2),wallVine,0,1,.9375F)==110,
                        "South support forbids positive Z wind displacement");
                    check(WindRendering.vineSupportMask(wallVine.setValue(net.minecraft.world.level.block.VineBlock.WEST,true))==9,
                        "Corner vines combine constraints from both support faces");
                    for(var direction : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.WEST,net.minecraft.core.Direction.EAST,
                            net.minecraft.core.Direction.NORTH,net.minecraft.core.Direction.SOUTH}) {
                        var side=Blocks.VINE.defaultBlockState().setValue(net.minecraft.world.level.block.VineBlock.getPropertyForFace(direction),true);
                        check(Integer.bitCount(WindRendering.vineSupportMask(side))==1,"Each horizontal support has a distinct constraint");
                    }
                    // Decode the shader anchor for leaf corners, overlay offsets and snow caps,
                    // including a cap that crosses a chunk boundary above its leaf block.
                    for(float x:new float[]{-.000976F,0,.5F,1,1.000976F})
                        for(float y:new float[]{0,1,1.125F,2}) {
                            int tag=WindRendering.leafTag(x,y,1)-160;
                            check(Math.abs(Math.floor(x+.002F)-(tag&1))<1e-5,"Leaf/snow X anchor agrees");
                            check(Math.abs(Math.floor(y+.002F)-tag/4)<1e-5,"Leaf/snow Y anchor agrees");
                            check(Math.abs(Math.floor(1+.002F)-((tag/2)&1))<1e-5,"Leaf/snow Z anchor agrees");
                        }
                    for(float x:new float[]{-.25F,0,.25F,.75F,1,1.25F})
                        for(float y:new float[]{-.25F,-.1F,0,.125F,.5F,.875F,1}) {
                            int tag=WindRendering.vertexTag(Blocks.SHORT_GRASS.defaultBlockState(),x,y,.25F)-64;
                            check(tag>=0 && tag<36,"Plant anchor marker range");
                            check(Math.abs(Math.floor(x+.002F)-(tag%3-1))<1e-5,"Original and overlay X anchors agree");
                            check(Math.abs(Math.floor(y+.002F)-(tag/9-1))<1e-5,"Original and overlay root anchors agree");
                        }
                    for(int px=-8;px<=8;px++)for(int pz=-8;pz<=8;pz++) {
                        var plant=Blocks.SHORT_GRASS.defaultBlockState();
                        var offset=plant.getOffset(new BlockPos(px,101,pz));
                        for(float f:new float[]{0,.125F,.5F,.875F,1}) {
                            float x=f+(float)offset.x,y=f+(float)offset.y,z=1-f+(float)offset.z;
                            int tag=WindRendering.vertexTag(plant,x,y,z)-64;
                            check(Math.floor(x+.002F)==tag%3-1 && Math.floor(y+.002F)==tag/9-1
                                    && Math.floor(z+.002F)==(tag/3)%3-1,
                                    "Random model offsets preserve the same wind anchor for original and subdivided foliage");
                        }
                    }
                    check(WindRendering.vertexTag(Blocks.SHORT_GRASS.defaultBlockState(), 0) == 201, "Plant root anchored");
                    check(WindRendering.vertexTag(Blocks.SHORT_GRASS.defaultBlockState(), 1) == 220, "Plant top moves");
                });
                world.getConnection().waitForChunksRender();
                context.takeScreenshot("weather-cold-windy-snow");
                for(int rain : new int[]{60,80}) {
                    p.rain=rain;
                    context.waitTicks(50);
                    server.runOnServer(s->{
                        var level=s.overworld();
                        for(var snowPos : new BlockPos[]{new BlockPos(0,101,0),new BlockPos(7,102,7)}) {
                            for(int i=0;i<20;i++)level.tickPrecipitation(snowPos);
                            check(level.getBlockState(snowPos).is(Blocks.SNOW)
                                && level.getBlockState(snowPos).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==(rain==60?4:7),
                                "Rain threshold caps natural accumulation without making full blocks");
                            check(!level.getBlockState(snowPos.above()).is(Blocks.SNOW),"Snow cannot stack beyond seven layers");
                        }
                    });
                }
                p.temperature = 100; p.rain = 100;
                context.waitTicks(410);
                server.runOnServer(s -> {
                    var level = s.overworld();
                    var biome = level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.SNOWY_PLAINS).value();
                    check(WeatherSystem.precipitation(level, biome, new BlockPos(0, 64, 0)) == Biome.Precipitation.SNOW, "Warm override preserves cold-biome snow");
                    var thaw = new BlockPos(10,101,10);
                    level.setBlockAndUpdate(thaw, Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2));
                    boolean previousThaw=config.weatherThaw, previousDepth=config.weatherSnowAccumulation;
                    try {
                        config.weatherThaw=false;
                        check(!WeatherThaw.thawAt(level,thaw) && level.getBlockState(thaw).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==2, "Disabled temperature thaw preserves snow even in hot weather");
                        config.weatherSnowAccumulation=false;
                        check(WeatherSnow.accumulationLimit(level,3)==3, "Disabled weather depth preserves a nonzero gamerule during extreme rain");
                    } finally { config.weatherThaw=previousThaw; config.weatherSnowAccumulation=previousDepth; }
                    check(WeatherThaw.thawAt(level,thaw) && level.getBlockState(thaw).getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS)==1, "Heat removes snow one layer at a time");
                    check(WeatherThaw.thawAt(level,thaw) && level.getBlockState(thaw).isAir(), "Final snow layer melts");
                    level.setBlockAndUpdate(thaw,Blocks.ICE.defaultBlockState());
                    check(WeatherThaw.thawAt(level,thaw) && level.getBlockState(thaw).is(Blocks.WATER), "Heat melts ordinary ice into water");
                    level.setBlockAndUpdate(thaw,Blocks.PACKED_ICE.defaultBlockState());
                    check(!WeatherThaw.thawAt(level,thaw), "Packed ice is preserved");
                    level.setBlockAndUpdate(thaw,Blocks.SNOW.defaultBlockState());
                    level.setBlockAndUpdate(thaw.above(2),Blocks.STONE.defaultBlockState());
                    check(!WeatherThaw.thawAt(level,thaw), "Sheltered snow is preserved");
                    level.removeBlock(thaw.above(2),false);
                    check(WeatherSystem.state(level).rain() == 100, "Extreme downpour reaches endpoint");
                });
                for (boolean oit : new boolean[]{false, true}) {
                    context.runOnClient(client -> client.options.improvedTransparency().set(oit));
                    context.waitTicks(5);
                    context.takeScreenshot("weather-heavy-wind-" + oit);
                }
                server.runCommand("execute in minecraft:the_nether run tp @a 0 100 0");
                context.waitTicks(30);
                context.runOnClient(client -> check(WeatherSystem.state(client.level) == null, "No Overworld state leaks into Nether"));
                server.runCommand("execute in minecraft:the_end run tp @a 0 100 0");
                context.waitTicks(30);
                context.runOnClient(client -> check(WeatherSystem.state(client.level) == null, "No Overworld state leaks into End"));
                server.runCommand("execute in minecraft:overworld run tp @a 0 104 -7");
                p.enabled = false;
                context.waitTicks(20);
                context.runOnClient(client -> check(WeatherSystem.state(client.level) == null, "Disable clears synchronized state"));
                p.enabled = true; p.overrideRain = p.overrideWind = p.overrideTemperature = false;
                server.runOnServer(s -> s.setWeatherParameters(12000, 0, false, false));
                context.waitTicks(20);
                server.runOnServer(s -> {
                    var state = WeatherSystem.state(s.overworld());
                    check(state != null && state.rain() == 0, "Automatic clear follows the vanilla weather clock");
                    check(state.wind() > 0, "Dry weather has independent wind");
                });
                server.runOnServer(s -> s.setWeatherParameters(0, 12000, true, false));
                context.waitTicks(30);
                server.runOnServer(s -> check(WeatherSystem.state(s.overworld()).rain() > 0, "Automatic wet period evolves rain"));
                server.runCommand("gamerule advance_weather false");
                // Let the transition finish; subsequent targets must stop evolving.
                context.waitTicks(110);
                WeatherState[] frozen = new WeatherState[1];
                server.runOnServer(s -> frozen[0] = WeatherSystem.state(s.overworld()));
                context.waitTicks(25);
                server.runOnServer(s -> check(frozen[0].equals(WeatherSystem.state(s.overworld())), "Weather gamerule freezes automatic channels"));
            }
        } finally {
            context.runOnClient(client -> client.options.improvedTransparency().set(oldTransparency[0]));
        }
    }
    private static void checkMath() {
        check(WeatherThaw.chance(50)==0 && WeatherThaw.chance(75)==.25F && WeatherThaw.chance(100)==1, "Thaw accelerates quadratically toward 100");
        check(WeatherSnow.layerLimit(49.99F)==1 && WeatherSnow.layerLimit(50)==2 && WeatherSnow.layerLimit(59.99F)==2
            && WeatherSnow.layerLimit(60)==4 && WeatherSnow.layerLimit(79.99F)==4 && WeatherSnow.layerLimit(80)==Integer.MAX_VALUE,
            "Snow depth follows rain thresholds with higher band at boundaries");
        var climate = new WeatherProfile(true);
        climate.minTemperature = climate.maxTemperature = 0;
        for (long seed : new long[]{0, 1, -1, 8193, Long.MAX_VALUE}) {
            check(WeatherSystem.automaticTemperature(climate, seed, 0) == 50, "Neutral initial temperature for every seed");
            check(WeatherSystem.automaticTemperature(climate, seed, 1200) == 50, "Neutral first minute");
            check(WeatherSystem.automaticTemperature(climate, seed, 1300) > 49, "No sudden freeze after the initial hold");
            float middle = WeatherSystem.automaticTemperature(climate, seed, 60000);
            check(middle > 0 && middle < 50, "Automatic cold weather develops gradually");
            check(WeatherSystem.automaticTemperature(climate, seed, 72000) == 0, "Startup protection eventually releases normal weather");
        }
        var seasons = new WeatherProfile(true);
        check(WeatherSystem.automaticTemperature(seasons, 7, 48000) == 50, "New world stays neutral for two days");
        float summer = WeatherSystem.automaticTemperature(seasons, 7, 72000);
        float winter = WeatherSystem.automaticTemperature(seasons, 7, 216000);
        check(summer >= 69 && winter <= 21, "Season cycle reaches warm and cold bands");
        check(WeatherSystem.automaticTemperature(seasons, 7, 110000) == summer, "Warm plateau lasts multiple days");
        check(WeatherSystem.automaticTemperature(seasons, 7, 250000) == winter, "Cold plateau lasts multiple days");
        for (long time = 0; time < 576000; time += 137) {
            float a = WeatherSystem.automaticTemperature(seasons, 7, time);
            float b = WeatherSystem.automaticTemperature(seasons, 7, time + 1);
            check(Math.abs(b-a) < .005F, "Season transitions remain continuous and slow across two years");
        }
        check(new WeatherState(0,0,50,0).rainLevel() == 0, "Clear endpoint");
        check(new WeatherState(10,0,50,0).rainLevel() == .4F, "Drizzle landmark");
        check(new WeatherState(25,0,50,0).thunderLevel() == 0, "Normal rain without thunder");
        check(new WeatherState(50,0,50,0).precipitationDensity() == 2, "Storm doubles normal rain density");
        check(new WeatherState(100,100,50,0).precipitationDensity() == 8, "Downpour has sharply increased density");
        check(new WeatherState(80,0,50,0).heavyRainFog() == 0, "Heavy rain fog starts smoothly at 80");
        check(new WeatherState(90,0,50,0).heavyRainFog() == .5F, "Heavy rain fog is halfway at 90");
        check(new WeatherState(100,0,50,0).heavyRainFog() == 1, "Maximum rain fully enables fog");
        check(new WeatherState(100,0,50,0).heavyRainFogEnd(1) == 15
            && new WeatherState(100,0,50,0).heavyRainFogEnd(0) == 100,
            "Outdoor fog ends at 15 blocks while indoor haze is deferred to 100");
        check(new WeatherState(100,0,50,0).heavyRainFogEnd(.5F) == 57.5F,
            "Shelter transitions blend the rain fog boundary smoothly");
        check(new WeatherState(100,0,50,0).heavyRainParticleChance(0) == 0
            && new WeatherState(100,0,50,0).heavyRainParticleChance(15) == 1,
            "Heavy rain cards increase toward the 15-block fog distance");
        check(new WeatherState(25,0,50,0).groundImpactChance() == .0625F, "Ordinary rain has sparse ground impacts");
        check(new WeatherState(100,0,50,0).groundImpactChance() == 1, "Downpour keeps full ground impacts");
        check(new WeatherState(0,0,50,0).weakWindGain() == 0, "Calm is silent");
        check(new WeatherState(0,100,50,0).strongWindGain() == 1, "Strong wind endpoint");
        check(WeatherSystem.automaticDirection(1234, 0) != WeatherSystem.automaticDirection(1234, 23000),
            "Wind direction wanders automatically without a wind activation event");
        var blended=WeatherSystem.interpolate(new WeatherState(0,0,50,350),new WeatherState(100,100,50,10),.5F);
        check(blended.rain()==50 && blended.wind()==50 && blended.direction()==0,
            "Frame-time weather interpolation eases sliders and wraps direction across north");
        var invalid = new WeatherProfile(); invalid.rain = -10; invalid.wind = 110; invalid.temperature = -100;
        invalid.validate(); check(invalid.rain == 0 && invalid.wind == 100 && invalid.temperature == 0, "Clamp persisted sliders");
    }
}














