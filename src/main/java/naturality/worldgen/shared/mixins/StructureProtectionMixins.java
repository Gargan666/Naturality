package naturality.worldgen.shared.mixins;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import naturality.worldgen.shared.StructureProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.FeaturePlacer;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.structures.NetherFossilPieces;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class StructureProtectionMixins {
    private StructureProtectionMixins() {}

    @Mixin(FeaturePlacer.class)
    public static abstract class DecorationScope {
        @Shadow @Final private WorldGenLevel level;

        @WrapMethod(method = "place(Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;Z)Z")
        private boolean naturality$protectStructures(PlacedFeature feature, RandomSource random, BlockPos origin,
                boolean biomeCheck, Operation<Boolean> original) {
            return StructureProtection.decorate(level, () -> original.call(feature, random, origin, biomeCheck));
        }
    }

    @Mixin(WorldGenRegion.class)
    public static abstract class DecorationWrites {
        @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
        private void naturality$preserveStructures(BlockPos pos, BlockState state, int flags, int limit,
                CallbackInfoReturnable<Boolean> cir) {
            if (StructureProtection.blocksWrite((WorldGenRegion)(Object)this, pos, state)) cir.setReturnValue(false);
        }
    }

    @Mixin(NetherFossilPieces.NetherFossilPiece.class)
    public static abstract class FossilOverlap {
        @Inject(method = "postProcess", at = @At("HEAD"), cancellable = true)
        private void naturality$avoidBuildings(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
                RandomSource random, BoundingBox chunkBox, ChunkPos chunkPos, BlockPos reference, CallbackInfo ci) {
            if (StructureProtection.overlapsStructure(level, ((StructurePiece)(Object)this).getBoundingBox())) ci.cancel();
        }
    }
}
