package naturality.systems.weather.overworld.lightning;

import naturality.systems.weather.framework.WeatherSystem.WeatherShelter;
import naturality.systems.weather.framework.WeatherSystem;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import naturality.Naturality;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

@org.jspecify.annotations.NullMarked
public final class DistantLightning {
    private DistantLightning() {}
    public static void tick(ServerLevel level) {
        var weather=WeatherSystem.state(level);
        if(weather==null || weather.rain()<=70 || !level.canHaveWeather())return;
        int near=(java.util.Objects.requireNonNull(level.getServer()).getPlayerList().getViewDistance()+2)*16;
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

    public record DistantLightningPayload(Identifier dimension,double x,double z,int seaLevel,long seed) implements CustomPacketPayload {
        public static final Type<DistantLightningPayload> TYPE=new Type<>(Naturality.id("distant_lightning"));
        @SuppressWarnings("null") // StreamCodec predates null contracts; network buffers/payloads are required.
        public static final StreamCodec<RegistryFriendlyByteBuf,DistantLightningPayload> CODEC=new StreamCodec<>() {
            public DistantLightningPayload decode(RegistryFriendlyByteBuf b) { return new DistantLightningPayload(b.readIdentifier(),b.readDouble(),b.readDouble(),b.readInt(),b.readLong()); }
            public void encode(RegistryFriendlyByteBuf b,DistantLightningPayload p) { b.writeIdentifier(p.dimension);b.writeDouble(p.x);b.writeDouble(p.z);b.writeInt(p.seaLevel);b.writeLong(p.seed); }
        };
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
