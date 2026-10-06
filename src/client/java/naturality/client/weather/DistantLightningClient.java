package naturality.client.weather;
import java.util.ArrayList;
import naturality.weather.DistantLightningPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.core.BlockPos;
public final class DistantLightningClient {
    private static final ArrayList<LightningBolt> BOLTS=new ArrayList<>();
    private static int nextId=-1000000;
    private static @org.jspecify.annotations.Nullable ClientLevel world;
    private DistantLightningClient() {}
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(DistantLightningPayload.TYPE,(p,c) -> c.client().execute(() -> receive(p)));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(world!=client.level) { BOLTS.clear();world=client.level; }
            if(client.isPaused())return;
            BOLTS.removeIf(bolt -> { bolt.commonTick();bolt.tick();return bolt.isRemoved(); });
        });
    }
    public static void receive(DistantLightningPayload p) {
        var client=Minecraft.getInstance();var level=client.level;
        if(level==null || !level.dimension().identifier().equals(p.dimension()))return;
        if(world!=level) { BOLTS.clear();world=level; }
        var bolt=EntityTypes.LIGHTNING_BOLT.create(level,EntitySpawnReason.EVENT);if(bolt==null)return;
        var camera=client.gameRenderer.mainCamera().position();
        var pos=BlockPos.containing(p.x(),p.seaLevel()+1,p.z());
        boolean distant=Math.hypot(p.x()-camera.x,p.z()-camera.z)>client.options.getEffectiveRenderDistance()*16;
        int y=distant || !naturality.util.LoadedChunks.has(level,pos)?p.seaLevel()+1:
            level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,pos.getX(),pos.getZ());
        bolt.setId(nextId--);bolt.snapTo(new net.minecraft.world.phys.Vec3(p.x(),y,p.z()));bolt.seed=p.seed();bolt.setVisualOnly(true);
        if(BOLTS.size()>=64)BOLTS.removeFirst();BOLTS.add(bolt);
    }
    public static void extract(LevelRenderState output,float partial) {
        var client=Minecraft.getInstance();if(world!=client.level)return;
        for(var bolt:BOLTS)output.entityRenderStates.add(client.getEntityRenderDispatcher().extractEntity(bolt,partial));
    }
}
