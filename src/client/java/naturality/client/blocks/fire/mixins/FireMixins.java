package naturality.client.blocks.fire.mixins;

import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.List;
import naturality.client.blocks.fire.ProceduralFire;
import net.minecraft.client.renderer.feature.*;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.blocks.fire.SurfaceFireModel.EntityFireState;
import naturality.client.blocks.fire.SurfaceFireModel.SoulFireTracker;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import naturality.client.blocks.fire.SurfaceFireModel.EntityFireRenderer;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import naturality.client.blocks.fire.SurfaceFireModel.SoulFirePlayerState;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class FireMixins {
    private FireMixins() {}

    @Mixin(RenderTypeFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityFireBindingsMixin {
        @Inject(method = "executeGroup", at = @At("HEAD"))
        private void naturality$bind(FeatureFrameContext context, OitStage stage, RenderPass pass, int index,
                List<?> submits, boolean ordered, CallbackInfo ci) {
            if ((Object) this instanceof FlameFeatureRenderer) ProceduralFire.bind(pass);
        }
    }

    @Mixin(EntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityFireExtractMixin {
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void naturality$bounds(Entity entity, EntityRenderState state, float delta, CallbackInfo ci) {
            // Rain can clear vanilla's synchronized fire flag between damage ticks.
            // Contact with lava is still an active fire source and must remain visible.
            if (!entity.fireImmune() && entity.isInLava()) state.displayFireAnimation = true;
            var box = entity.getInterpolatedBoundingBox(delta).move(-state.x, -state.y, -state.z);
            ((EntityFireState) state).naturality$fireData(box,
                0xFF000000 | (entity.getUUID().hashCode() & 0x0FFFFF), SoulFireTracker.isSoulBurning(entity));
        }
    }

    @Mixin(FlameFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityFireRenderMixin extends RenderTypeFeatureRenderer<FlameFeatureRenderer.Submit> {
        @Inject(method = "buildGroup", at = @At("HEAD"), cancellable = true)
        private void naturality$boxFire(FeatureFrameContext context, List<FlameFeatureRenderer.Submit> submits, CallbackInfo ci) {
            var buffer = getVertexBuilder(EntityFireRenderer.LAYER);
            for (var submit : submits) {
                var state = (naturality.client.blocks.fire.SurfaceFireModel.EntityFireState) submit.entityRenderState();
                var sprite = context.atlasManager().get(state.naturality$soulFire()
                    ? new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS, Identifier.withDefaultNamespace("block/soul_fire_0"))
                    : ModelBakery.FIRE_0);
                EntityFireRenderer.emit(submit, buffer, sprite);
            }
            ci.cancel();
        }
    }

    @Mixin(EntityRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityFireStateMixin implements EntityFireState {
        @Unique private @org.jspecify.annotations.Nullable AABB naturality$box;
        @Unique private int naturality$seed;
        @Unique private boolean naturality$soulFire;
        public @org.jspecify.annotations.Nullable AABB naturality$fireBox() { return naturality$box; }
        public int naturality$fireSeed() { return naturality$seed; }
        public boolean naturality$soulFire() { return naturality$soulFire; }
        public void naturality$fireData(AABB box, int seed, boolean soulFire) {
            naturality$box = box;
            naturality$seed = seed;
            naturality$soulFire = soulFire;
        }
    }

    @Mixin(QuadParticleFeatureRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FireParticleBindingsMixin {
        @Inject(method = "executeGroup", at = @At("HEAD"))
        private void naturality$fireHeat(FeatureFrameContext context, OitStage stage, RenderPass pass, int index,
                List<QuadParticleFeatureRenderer.Submit> submits, boolean ordered, CallbackInfo ci) {
            ProceduralFire.bind(pass);
        }
    }

    @Mixin(ScreenEffectRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SoulFireOverlayMixin {
        @Redirect(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/sprite/SpriteGetter;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
        private TextureAtlasSprite naturality$soulFireOverlay(SpriteGetter sprites, SpriteId id, float partialTicks,
                SubmitNodeCollector collector, PlayerRenderState playerState, CameraRenderState cameraState, boolean hideGui) {
            if (id == ModelBakery.FIRE_1 && ((SoulFirePlayerState) playerState).naturality$soulFireOverlay()) {
                id = new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS, Identifier.withDefaultNamespace("block/soul_fire_1"));
            }
            return sprites.get(id);
        }
    }

    @Mixin(LevelExtractor.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SoulFirePlayerExtractMixin {
        @Inject(method = "extractPlayerState", at = @At("TAIL"))
        private void naturality$markOverlay(Camera camera, DeltaTracker deltaTracker, float partialTick,
                PlayerRenderState state, CallbackInfo ci) {
            var player = Minecraft.getInstance().player;
            if (player != null && !player.fireImmune() && player.isInLava()) state.isOnFire = true;
            ((SoulFirePlayerState) state).naturality$soulFireOverlay(
                player != null && state.isOnFire && SoulFireTracker.isSoulBurning(player));
        }
    }

    @Mixin(PlayerRenderState.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SoulFirePlayerStateMixin implements SoulFirePlayerState {
        @Unique private boolean naturality$soulFireOverlay;

        @Override public boolean naturality$soulFireOverlay() { return naturality$soulFireOverlay; }
        @Override public void naturality$soulFireOverlay(boolean value) { naturality$soulFireOverlay = value; }
    }
}
