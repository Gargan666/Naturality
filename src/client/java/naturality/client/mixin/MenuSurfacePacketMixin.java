package naturality.client.mixin;

import naturality.client.menu.MenuPreview;
import naturality.client.menu.MenuSurface;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class MenuSurfacePacketMixin {
    @ModifyVariable(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At("HEAD"), argsOnly = true)
    private static LevelChunk naturality$surfacePayload(LevelChunk chunk) {
        if (chunk.getLevel().getServer() instanceof net.minecraft.client.server.IntegratedServer server && MenuPreview.owns(server)) {
            return MenuSurface.snapshot(chunk);
        }
        return chunk;
    }
}
