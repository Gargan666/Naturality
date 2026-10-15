package naturality.client.blocks.snow.mixins;

import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.ArrayList;
import java.util.List;
import naturality.client.blocks.snow.SnowTextureAtlas;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import naturality.client.blocks.snow.SurfaceSnowModel.SnowSectionVisibility;
import net.minecraft.client.renderer.LevelRenderer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import naturality.client.blocks.snow.SurfaceSnowModel.SnowRenderRegion;
import naturality.config.gameplay.GameplaySettings;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.client.renderer.chunk.SectionCopy;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Optional;
import naturality.client.blocks.snow.SnowTextureAtlas.SnowSpritePixels;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.client.resources.metadata.texture.TextureMetadataSection;
import net.minecraft.server.packs.metadata.MetadataSectionType.WithValue;
import naturality.blocks.snow.SnowGeometry;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Pseudo;
import naturality.blocks.snow.SnowGeometry.SnowGeometryCache;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Feature integration hooks; each nested type retains its independent Mixin target and gate. */
public final class SnowMixins {
    private SnowMixins() {}

    @Mixin(TextureAtlas.class)
    @org.jspecify.annotations.NullMarked
    public interface SnowAtlasAccess {
        @Accessor("width") int naturality$width();
        @Accessor("height") int naturality$height();
        @Accessor("maxMipLevel") int naturality$mipLevel();
    }

    @Mixin(SpriteLoader.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowAtlasSpaceMixin {
        @SuppressWarnings("null") @Shadow @Final private Identifier location;
        @Shadow @Final private int maxSupportedTextureSize;
        @ModifyVariable(method="stitch",at=@At("HEAD"),argsOnly=true)
        private List<SpriteContents> naturality$compositeSpace(List<SpriteContents> original) {
            if(!location.equals(naturality.client.api.rendering.AtlasLocations.BLOCKS))return original;
            var result=new ArrayList<>(original);
            result.add(SnowTextureAtlas.reserve(maxSupportedTextureSize));
            return result;
        }
    }

    @Mixin(TextureAtlas.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowAtlasTickMixin {
        @Inject(method="cycleAnimationFrames",at=@At("TAIL"))
        private void naturality$compositeAnimation(CallbackInfo ci) {
            SnowTextureAtlas.tick((TextureAtlas)(Object)this);
        }
    }

    @Mixin(LevelRenderer.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowLevelRendererResetMixin {
        @Inject(method="resetLevelRenderData",at=@At("HEAD"))
        private void naturality$clearSnowBounds(CallbackInfo ci) { SnowSectionVisibility.clear(); }
    }

    /** Run the normal lighter in the cell occupied by each fitted snow volume. */
    @Mixin(targets="net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowLightingMixin {
        @SuppressWarnings("null") @Shadow private BlockAndTintGetter level;
        @SuppressWarnings("null") @Shadow private BlockPos pos;
        @SuppressWarnings("null") @Shadow private BlockState blockState;
        @SuppressWarnings("null") @Shadow @Final private AoCalculator aoCalc;

        @WrapMethod(method="shadeQuad")
        private void naturality$surfaceLight(MutableQuadViewImpl quad,boolean ao,boolean emissive,boolean vanillaShade,
                Operation<Void> original) {
            if((quad.tag() & 0xFFFFFF00)!=0x534E0000) { original.call(quad,ao,emissive,vanillaShade);return; }
            int shift=(quad.tag()&255)-64;
            if(shift==0) { original.call(quad,ao,emissive,vanillaShade);return; }
            var owner=pos;
            pos=owner.above(shift);
            quad.translate(0,-shift,0);
            aoCalc.prepare(level,blockState,pos);
            try { original.call(quad,ao,emissive,vanillaShade); }
            finally {
                quad.translate(0,shift,0);
                pos=owner;
                aoCalc.prepare(level,blockState,owner);
            }
        }
    }

    @Mixin(targets="net.minecraft.client.renderer.Octree$Branch")
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowOctreeBranchMixin {
        @WrapOperation(method="visitNodes", at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/culling/Frustum;cubeInFrustum(Lnet/minecraft/world/level/levelgen/structure/BoundingBox;)I"))
        private int naturality$includeLowerSnow(Frustum frustum, BoundingBox bounds, Operation<Integer> original) {
            int visible=original.call(frustum,bounds);
            if (!naturality.config.gameplay.GameplaySettings.clientSnowWrapping() || visible==-2 || visible==-1) return visible;
            return original.call(frustum,new BoundingBox(bounds.minX(),bounds.minY()-SnowSectionVisibility.MAX_DROP,
                bounds.minZ(),bounds.maxX(),bounds.maxY(),bounds.maxZ()));
        }
    }

    @Mixin(targets="net.minecraft.client.renderer.Octree$Leaf")
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowOctreeLeafMixin {
        @SuppressWarnings("null") @Shadow @Final private SectionRenderDispatcher.RenderSection section;

        @WrapOperation(method="visitNodes", at=@org.spongepowered.asm.mixin.injection.At(value="INVOKE",
            target="Lnet/minecraft/client/renderer/culling/Frustum;isVisible(Lnet/minecraft/world/phys/AABB;)Z"))
        private boolean naturality$includeLowerSnow(Frustum frustum, AABB bounds, Operation<Boolean> original) {
            if (original.call(frustum,bounds)) return true;
            if (!naturality.config.gameplay.GameplaySettings.clientSnowWrapping()) return false;
            int drop=SnowSectionVisibility.below(section.getSectionNode());
            return drop>0 && original.call(frustum,new AABB(bounds.minX,bounds.minY-drop,bounds.minZ,
                bounds.maxX,bounds.maxY,bounds.maxZ));
        }
    }

    @Mixin(RenderRegionCache.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowRenderRegionCacheMixin {
        @Invoker("getSectionDataCopy")
        protected abstract SectionCopy naturality$copy(Level level, int x, int y, int z);

        @Inject(method = "createRegion", at = @At("RETURN"))
        private void naturality$captureSnow(ClientLevel level, long node, CallbackInfoReturnable<RenderSectionRegion> cir) {
            if (!GameplaySettings.clientSnowWrapping()) return;
            int x = SectionPos.x(node), y = SectionPos.y(node), z = SectionPos.z(node);
            // Probe palettes on the client thread before copying. Conservatively
            // include the complete old snapshot volume, including neighboring owners.
            boolean snow = naturality.client.blocks.snow.SurfaceSnowModel.SnowRegionPresence.mayHaveSnow(level, node);
            if (!snow) {
                ((SnowRenderRegion) cir.getReturnValue()).naturality$markSnowFree();
                // Tall sugar cane also uses this existing vertical snapshot to find
                // its wind anchor. Keep the lower margin for sections with stalks.
                if (!naturality.client.blocks.snow.SurfaceSnowModel.SnowRegionPresence.hasRootedStalk(level, node)) return;
            }
            var copies = new HashMap<Long, SectionCopy>();
            // A 32-block owner/support search plus one AO neighbor needs a three-section
            // vertical margin. Reuse vanilla's cache and capture before workers start.
            for (int dy : new int[] {-3, -2, 2, 3}) {
                if (!snow && dy > 0) continue;
                if (y + dy < level.getMinSectionY() || y + dy >= level.getMaxSectionY()) continue;
                for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                    copies.put(SectionPos.asLong(x + dx, y + dy, z + dz),
                        naturality$copy(level, x + dx, y + dy, z + dz));
                }
            }
            ((SnowRenderRegion) cir.getReturnValue()).naturality$setSnowSections(copies);
        }
    }

    @Mixin(RenderSectionRegion.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowRenderRegionMixin implements SnowRenderRegion {
        @Shadow @Final private int minSectionX;
        @Shadow @Final private int minSectionY;
        @Shadow @Final private int minSectionZ;
        @Unique private Map<Long, SectionCopy> naturality$snowSections = Map.of();

        @Unique private boolean naturality$mayHaveSnow = true;
        @Override public boolean naturality$mayHaveSnow() { return naturality$mayHaveSnow; }
        @Override public void naturality$markSnowFree() { naturality$mayHaveSnow = false; }

        @Override
        public void naturality$setSnowSections(Map<Long, SectionCopy> sections) {
            naturality$snowSections = Map.copyOf(sections);
        }

        @Unique private boolean naturality$outside(BlockPos pos) {
            int x = SectionPos.blockToSectionCoord(pos.getX()) - minSectionX;
            int y = SectionPos.blockToSectionCoord(pos.getY()) - minSectionY;
            int z = SectionPos.blockToSectionCoord(pos.getZ()) - minSectionZ;
            return x < 0 || x >= 3 || y < 0 || y >= 3 || z < 0 || z >= 3;
        }

        @Unique private BlockState naturality$snowState(BlockPos pos) {
            var copy = naturality$snowSections.get(SectionPos.asLong(pos));
            return copy == null ? Blocks.AIR.defaultBlockState() : copy.getBlockState(pos);
        }

        @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
        private void naturality$block(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
            if (naturality$outside(pos)) cir.setReturnValue(naturality$snowState(pos));
        }

        @Inject(method = "getFluidState", at = @At("HEAD"), cancellable = true)
        private void naturality$fluid(BlockPos pos, CallbackInfoReturnable<FluidState> cir) {
            if (naturality$outside(pos)) cir.setReturnValue(naturality$snowState(pos).getFluidState());
        }

        @Inject(method = "getBlockEntity", at = @At("HEAD"), cancellable = true)
        private void naturality$entity(BlockPos pos, CallbackInfoReturnable<@Nullable BlockEntity> cir) {
            if (!naturality$outside(pos)) return;
            var copy = naturality$snowSections.get(SectionPos.asLong(pos));
            cir.setReturnValue(copy == null ? null : copy.getBlockEntity(pos));
        }
    }

    @Mixin(SectionCompiler.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowSectionCompilerMixin {
        @WrapMethod(method="compile")
        private SectionCompiler.Results naturality$captureSnowBounds(SectionPos section,
                RenderSectionRegion region, VertexSorting sorting, SectionBufferBuilderPack builders,
                Operation<SectionCompiler.Results> original) {
            SnowSectionVisibility.begin(section);
            naturality.blocks.snow.SnowGeometry.SnowGeometryCache.begin();
            naturality.client.rendering.distantdetail.RenderDetail.beginBuild();
            try { return original.call(section, region, sorting, builders); }
            finally { naturality.client.rendering.distantdetail.RenderDetail.endBuild(); naturality.blocks.snow.SnowGeometry.SnowGeometryCache.end(); SnowSectionVisibility.finish(); }
        }
    }

    @Mixin(SpriteContents.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class SnowSpritePixelsMixin implements SnowSpritePixels {
        @Unique private Optional<AnimationMetadataSection> naturality$animation=Optional.empty();
        @Unique private Optional<TextureMetadataSection> naturality$texture=Optional.empty();
        @Override @Accessor("originalImage") public abstract NativeImage naturality$image();
        @Override @Accessor("byMipLevel") public abstract NativeImage[] naturality$mips();
        @Override public Optional<AnimationMetadataSection> naturality$animation() { return naturality$animation; }
        @Override public Optional<TextureMetadataSection> naturality$textureMetadata() { return naturality$texture; }
        @Inject(method="<init>(Lnet/minecraft/resources/Identifier;Lnet/minecraft/client/resources/metadata/animation/FrameSize;Lcom/mojang/blaze3d/platform/NativeImage;Ljava/util/Optional;Ljava/util/List;Ljava/util/Optional;)V",at=@At("TAIL"))
        private void naturality$metadata(Identifier name,FrameSize size,NativeImage image,Optional<AnimationMetadataSection> animation,
                List<WithValue<?>> extra,Optional<TextureMetadataSection> texture,CallbackInfo ci) {
            naturality$animation=animation;naturality$texture=texture;
        }
    }

    /** Sodium caches state-only snow occlusion, which cannot describe a displaced mesh. */
    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext",remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumSnowCullingMixin {
        @SuppressWarnings("null") @Shadow protected BlockAndTintGetter level;
        @SuppressWarnings("null") @Shadow protected BlockPos pos;

        @Inject(method="shouldDrawSide",at=@At("HEAD"),cancellable=true)
        private void naturality$displacedSnowFace(Direction facing,CallbackInfoReturnable<Boolean> cir) {
            if(!naturality.config.gameplay.GameplaySettings.clientSnowWrapping())return;
            var neighbor=pos.relative(facing);
            if(level.getBlockState(neighbor).is(Blocks.SNOW) && !SnowGeometry.usesVanillaGeometry(level,neighbor))
                cir.setReturnValue(true);
        }
    }

    /** A Sodium LevelSlice is reused, so discard every cache at the end of its build. */
    @Pseudo
    @Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.compile.tasks.ChunkBuilderMeshingTask", remap=false)
    @org.jspecify.annotations.NullMarked
    public static abstract class SodiumSnowGeometryCacheMixin {
        @WrapMethod(method="execute(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildContext;Lnet/caffeinemc/mods/sodium/client/util/task/CancellationToken;)Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/ChunkBuildOutput;")
        @Coerce
        private Object naturality$snowCache(@Coerce Object context, @Coerce Object token, Operation<Object> original) {
            SnowGeometryCache.begin();
            naturality.client.rendering.distantdetail.RenderDetail.beginBuild();
            try { return original.call(context,token); }
            finally { naturality.client.rendering.distantdetail.RenderDetail.endBuild(); SnowGeometryCache.end(); }
        }
    }
}
