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
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
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
            if ((Object) this instanceof FlameFeatureRenderer && !naturality.client.api.rendering.ShaderPacks.active())
                ProceduralFire.bind(pass);
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
            // The procedural layer encodes seed and pixel dimensions in color/light.
            // Iris owns entity shading; its native flame path needs ordinary attributes.
            if (naturality.client.api.rendering.ShaderPacks.active()) return;
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

        @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer;prepare(Lnet/minecraft/client/renderer/feature/FlameFeatureRenderer$Submit;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V"))
        private void naturality$packSoulFire(FlameFeatureRenderer renderer, FlameFeatureRenderer.Submit submit,
                com.mojang.blaze3d.vertex.VertexConsumer buffer, TextureAtlasSprite first, TextureAtlasSprite second,
                Operation<Void> original, @Local(argsOnly = true) FeatureFrameContext context) {
            if (((EntityFireState) submit.entityRenderState()).naturality$soulFire()) {
                first = context.atlasManager().get(new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS,
                    Identifier.withDefaultNamespace("block/soul_fire_0")));
                second = context.atlasManager().get(new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS,
                    Identifier.withDefaultNamespace("block/soul_fire_1")));
            }
            original.call(renderer, submit, buffer, first, second);
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
        @WrapOperation(method = "submit", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/resources/model/sprite/SpriteGetter;get(Lnet/minecraft/client/resources/model/sprite/SpriteId;)Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;"))
        private TextureAtlasSprite naturality$soulFireOverlay(SpriteGetter sprites, SpriteId id,
                Operation<TextureAtlasSprite> original, @Local(argsOnly = true) PlayerRenderState playerState) {
            if (id == ModelBakery.FIRE_1 && ((SoulFirePlayerState) playerState).naturality$soulFireOverlay()) {
                id = new SpriteId(naturality.client.api.rendering.AtlasLocations.BLOCKS, Identifier.withDefaultNamespace("block/soul_fire_1"));
            }
            return original.call(sprites, id);
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
