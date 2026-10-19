package naturality.client.rendering.culling.mixins;

import naturality.client.rendering.culling.FogCulling;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Pseudo;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Unique;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class CullingMixins {
    private CullingMixins() {}

    @Mixin(BlockEntityRenderDispatcher.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class BlockEntityFogCullingMixin {
        @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
        private void naturality$hiddenBlockEntity(BlockEntity entity, float partialTick,
                ModelFeatureRenderer.CrumblingOverlay overlay, boolean global,
                CallbackInfoReturnable<BlockEntityRenderState> cir) {
            // Global renderers can emit distant beams; their block cell does not bound the effect.
            if (global || !FogCulling.active() || !FogCulling.matches(entity.getLevel())) return;
            var pos = entity.getBlockPos();
            if (FogCulling.hidden(pos.getX() - 2.0, pos.getY() - 2.0, pos.getZ() - 2.0,
                    pos.getX() + 3.0, pos.getY() + 3.0, pos.getZ() + 3.0)) cir.setReturnValue(null);
        }
    }

    @Mixin(EntityRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class EntityFogCullingMixin<T extends Entity> {
        @Shadow protected abstract AABB getBoundingBoxForCulling(T entity, float partialTick);
        @Shadow protected abstract boolean affectedByCulling(T entity);

        @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
        private void naturality$hiddenInFog(@NonNull T entity, Frustum frustum, double x, double y, double z,
                float partialTick, CallbackInfoReturnable<Boolean> cir) {
            if (!cir.getReturnValueZ() || !FogCulling.matches(entity.level())
                    || !affectedByCulling(entity) || entity.isCurrentlyGlowing()) return;
            AABB bounds = getBoundingBoxForCulling(entity, partialTick);
            if (naturality.client.rendering.culling.FogCulling.OcclusionCulling.hidden(entity, bounds)) { cir.setReturnValue(false); return; }
            if (!FogCulling.active()) return;
            // Leashes and their holders can cross the fog boundary independently of this body.
            if (entity instanceof net.minecraft.world.entity.Leashable leashable) {
                Entity holder = leashable.getLeashHolder();
                if (holder != null) bounds = bounds.minmax(holder.getInterpolatedBoundingBox(partialTick));
            }
            if (FogCulling.hidden(bounds.minX - 2, bounds.minY - 2, bounds.minZ - 2,
                    bounds.maxX + 2, bounds.maxY + 2, bounds.maxZ + 2))
                cir.setReturnValue(false);
        }
    }

    @Mixin(FogRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class FogCullingCaptureMixin {
        @Shadow private static boolean fogEnabled;

        @WrapMethod(method = "setupFog")
        private FogData naturality$capture(Camera camera, int distance, DeltaTracker delta, float darken,
                ClientLevel level, Operation<FogData> original) {
            FogData fog = original.call(camera, distance, delta, darken, level);
            if (level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)
                    && naturality.client.rendering.fog.EndFogComposite.EndFogTransparency.isActive(level, camera)) {
                naturality.client.rendering.fog.EndFogComposite.DistanceFogSpan.apply(fog, distance);
            }
            FogCulling.capture(level, camera, fog, fogEnabled);
            return fog;
        }
    }

    /** Filters draw commands, including translucent draws and disabled face culling. */
    @Pseudo
    @Mixin(targets = "net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer", remap = false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumFogCullingMixin {
        @ModifyExpressionValue(method = "fillCommandBuffer", at = @At(value = "INVOKE",
            target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/data/SectionRenderDataUnsafe;getSliceMask(J)I"))
        private static int naturality$fogMask(int mask, @Local(index = 15) int x,
                @Local(index = 16) int y, @Local(index = 17) int z) {
            int drop = naturality.config.gameplay.GameplaySettings.clientSnowWrapping()
                ? naturality.client.api.rendering.layers.SurfaceLayerModel.SnowSectionVisibility.MAX_DROP : 0;
            return FogCulling.active() && FogCulling.sectionHidden(x << 4, y << 4, z << 4, drop) ? 0 : mask;
        }
    }

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class TerrainFogCullingMixin {
        @Unique private final ObjectArrayList<SectionRenderDispatcher.RenderSection> naturality$fogSections = new ObjectArrayList<>();

        @ModifyExpressionValue(method = "extractSectionDrawGroups", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;visibleSections:Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
        private ObjectArrayList<SectionRenderDispatcher.RenderSection> naturality$visibleInFog(
                ObjectArrayList<SectionRenderDispatcher.RenderSection> original) {
            naturality$fogSections.clear();
            if (!FogCulling.active()) return original;
            for (var section : original) {
                var pos = section.getRenderOrigin();
                if (!FogCulling.sectionHidden(pos.getX(), pos.getY(), pos.getZ())) naturality$fogSections.add(section);
            }
            return naturality$fogSections;
        }
    }
}
