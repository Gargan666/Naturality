package naturality.test;

import naturality.weather.WeatherProfile;
import naturality.weather.WeatherWorldData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;

/** Focused coverage of the irregular-support precipitation path. */
public final class SnowAccumulationGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world=context.worldBuilder().create()) {
            var server=world.getServer();
            var profile=new WeatherProfile(true);
            profile.overrideRain=profile.overrideTemperature=profile.overrideWind=true;
            profile.rain=50;profile.temperature=0;profile.wind=0;
            server.runOnServer(s -> WeatherWorldData.get(s).setProfile("minecraft:overworld",profile));
            server.runCommand("fill -4 100 -4 4 100 4 stone");
            server.runCommand("fillbiome -16 96 -16 15 127 15 plains");
            server.runCommand("gamerule minecraft:random_tick_speed 0");
            server.runCommand("setblock 0 101 0 oak_sign");
            context.waitTicks(410);
            server.runOnServer(s -> {
                var level=s.overworld();var support=new BlockPos(0,101,0);
                for(int i=0;i<12;i++)level.tickPrecipitation(support);
                var snow=level.getBlockState(support.above());
                if(!snow.is(Blocks.SNOW) || snow.getValue(SnowLayerBlock.LAYERS)!=2)
                    throw new AssertionError("Rain 50 still accumulates two layers above a sign");
            });
            server.runCommand("weather minecraft:overworld rain 100");
            context.waitTicks(210);
            server.runOnServer(s -> {
                var level=s.overworld();var support=new BlockPos(0,101,0);
                for(int i=0;i<20;i++)level.tickPrecipitation(support);
                var snow=level.getBlockState(support.above());
                if(!snow.is(Blocks.SNOW) || snow.getValue(SnowLayerBlock.LAYERS)!=7)
                    throw new AssertionError("Extreme rain retains the fitted seven-layer limit");
            });
        }
    }
}
