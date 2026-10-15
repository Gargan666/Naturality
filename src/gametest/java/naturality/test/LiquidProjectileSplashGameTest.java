package naturality.test;

import naturality.client.fluid.LavaEntrySplash;
import naturality.client.fluid.WaterEntrySplash;
import naturality.config.NaturalityConfig;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Real item/projectile falls exercise contact thresholds, single impacts and sound suppression. */
public final class LiquidProjectileSplashGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        var saved=NaturalityConfig.get().liquids;
        context.runOnClient(_->NaturalityConfig.get().liquids=new NaturalityConfig.Liquids());
        try(var world=TestWorlds.disposable(context).create()) {
            var server=world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set 6000");
            server.runCommand("tp @a 0 104 -7 0 20");
            server.runCommand("fill -7 99 -4 7 99 4 stone");
            server.runCommand("fill -6 100 -3 -2 100 3 water");
            server.runCommand("fill 2 100 -3 6 100 3 lava");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            for(boolean lava:new boolean[]{false,true})for(String type:new String[]{"arrow","item"})for(boolean high:new boolean[]{false,true}) {
                int x=lava?4:-4,y=high?109:104;
                String item=type.equals("item")?"Item:{id:\"minecraft:stone\",count:1},":"";
                server.runCommand("summon "+type+" "+x+" "+y+" 0 {"+item+"Tags:[\"liquid_drop_test\"]}");
                world.getConnection().waitForClientboundPackets();
                int[] counts=new int[2];
                for(int tick=0;tick<60;tick++) {
                    context.waitTicks(1);
                    context.runOnClient(_->{counts[0]+=lava?LavaEntrySplash.lastSplashCount:WaterEntrySplash.lastSplashCount;counts[1]+=WaterEntrySplash.lastSoundCount;});
                }
                check(counts[0]==(high?1:0),type+" into "+(lava?"lava":"water")+" must follow the normal five-block threshold and splash once; got "+counts[0]);
                check(counts[1]==0,"Items and projectiles must not play the custom splash sound");
                server.runCommand("kill @e[tag=liquid_drop_test]");
                world.getConnection().waitForClientboundPackets();
                context.waitTicks(2);
            }
            server.runCommand("summon pig -4 109 0 {Tags:[\"liquid_drop_test\"]}");
            world.getConnection().waitForClientboundPackets();
            int[] normal=new int[2];
            for(int tick=0;tick<60;tick++) {
                context.waitTicks(1);
                context.runOnClient(_->{normal[0]+=WaterEntrySplash.lastSplashCount;normal[1]+=WaterEntrySplash.lastSoundCount;});
            }
            check(normal[0]==1 && normal[1]==1,"Living entities still produce one visual splash and sound");
        } finally {context.runOnClient(_->NaturalityConfig.get().liquids=saved);}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
