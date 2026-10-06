package naturality.test;
import naturality.weather.*;
import naturality.client.weather.DistantLightningClient;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.renderer.state.level.LevelRenderState;
public final class LightningGameTest implements FabricClientGameTest {
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        check(naturality.client.weather.ThunderAudio.delayTicks(343) == 20,
            "Thunder travels 343 blocks per second");
        check(naturality.client.weather.ThunderAudio.pitchScale(1500)
            < naturality.client.weather.ThunderAudio.pitchScale(100), "Distant thunder has lower pitch");
        boolean[] original={false};context.runOnClient(c -> original[0]=c.options.improvedTransparency().get());
        try(var world=context.worldBuilder().create()) {
            var profile=new WeatherProfile(true);profile.overrideRain=profile.overrideWind=profile.overrideTemperature=true;
            profile.rain=70;profile.wind=0;profile.temperature=50;
            world.getServer().runOnServer(s -> WeatherWorldData.get(s).setProfile("minecraft:overworld",profile));
            world.getServer().runCommand("gamemode spectator @a");
            world.getServer().runCommand("tp @a 0 100 0 0 0");
            context.waitTicks(145);
            world.getServer().runOnServer(s -> check(!s.overworld().isThundering(),"Rain 70 does not allow natural lightning"));
            profile.rain=100;context.waitTicks(65);
            world.getServer().runOnServer(s -> check(s.overworld().isThundering(),"Rain above 70 permits natural lightning"));
            for(boolean oit:new boolean[]{false,true}) {
                context.runOnClient(c -> {
                    c.options.improvedTransparency().set(oit);
                    int loaded=c.level.getChunkSource().getLoadedChunksCount();
                    DistantLightningClient.receive(new DistantLightningPayload(c.level.dimension().identifier(),0,1500,c.level.getSeaLevel(),12345));
                    var render=new LevelRenderState();DistantLightningClient.extract(render,1);
                    check(!render.entityRenderStates.isEmpty(),"Distant lightning is extracted outside chunk/render distance");
                    var state=render.entityRenderStates.getLast();
                    check(state.y==c.level.getSeaLevel()+1,"Unloaded strikes start one block above sea level");
                    check(c.level.getChunkSource().getLoadedChunksCount()==loaded,"Distant visual strikes never load terrain");
                });
                context.waitTicks(1);context.takeScreenshot("distant-lightning-fog-"+oit);
            }
            context.waitTicks(100);
            context.runOnClient(c -> {var render=new LevelRenderState();DistantLightningClient.extract(render,1);check(render.entityRenderStates.isEmpty(),"Distant bolts expire normally");});
            world.getServer().runCommand("fill -12 110 -12 12 122 12 stone");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            world.getServer().runOnServer(s -> {
                var player = s.overworld().players().getFirst();
                check(WeatherShelter.underground(s.overworld(),player.getEyePosition(),player),
                    "Deep roof with no immediate surface exit is underground on the server");
                check(!WeatherShelter.hasSurfacePlayer(s.overworld()),
                    "Natural lightning stops when every player is underground");
            });
            context.runOnClient(c -> {
                naturality.client.weather.WeatherSoundEnvironment.tick(c);
                check(naturality.client.weather.WeatherSoundEnvironment.windExposure() == 0,
                    "Underground wind is silent");
                check(!naturality.client.weather.ThunderAudio.audible(),"Underground listener cannot hear thunder");
            });
            world.getServer().runCommand("fill 3 110 -12 12 122 12 air");
            world.getConnection().waitForClientboundPackets();
            context.waitTicks(20);
            context.runOnClient(c -> check(naturality.client.weather.ThunderAudio.audible(),
                "Nearby unobstructed surface exit restores thunder under an overhang"));
        } finally { context.runOnClient(c -> c.options.improvedTransparency().set(original[0])); }
    }
}
