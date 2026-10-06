package naturality.weather;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
public final class DistantLightning {
    private DistantLightning() {}
    public static void tick(ServerLevel level) {
        var weather=WeatherSystem.state(level);
        if(weather==null || weather.rain()<=70 || !level.canHaveWeather())return;
        int near=(level.getServer().getPlayerList().getViewDistance()+2)*16;
        int far=Math.max(2048,near+512);
        for(var player:level.players()) {
            if(level.getRandom().nextInt(600)!=0)continue;
            if (WeatherShelter.underground(level, player.getEyePosition(), player)) continue;
            double angle=level.getRandom().nextDouble()*Math.PI*2;
            double distance=near+level.getRandom().nextDouble()*(far-near);
            var packet=new DistantLightningPayload(level.dimension().identifier(),player.getX()+Math.cos(angle)*distance,
                player.getZ()+Math.sin(angle)*distance,level.getSeaLevel(),level.getRandom().nextLong());
            for(var viewer:level.players())if(ServerPlayNetworking.canSend(viewer,DistantLightningPayload.TYPE))ServerPlayNetworking.send(viewer,packet);
        }
    }
}
