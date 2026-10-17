package naturality.worldgen.overworld.ocean.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import naturality.worldgen.overworld.ocean.OceanTerrain;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure;
import net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

/** Hooks remain separate from the ocean geometry and only affect standard Overworld terrain. */
public final class OceanMixins {
    private OceanMixins() {}

    @Mixin(OceanMonumentPieces.MonumentBuilding.class)
    public interface OceanMonumentPartsMixin extends OceanTerrain.MonumentParts {
        @Override
        @org.spongepowered.asm.mixin.gen.Accessor("childPieces")
        java.util.List<? extends @org.jspecify.annotations.NonNull StructurePiece> naturality$rooms();
    }

    @Mixin(OceanMonumentStructure.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class OceanMonumentHeightMixin {
        @WrapOperation(method = "generatePieces", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/structure/structures/OceanMonumentStructure;createTopPiece(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/world/level/levelgen/WorldgenRandom;)Lnet/minecraft/world/level/levelgen/structure/StructurePiece;"))
        private static StructurePiece naturality$seabedHeight(
                net.minecraft.world.level.ChunkPos pos, net.minecraft.world.level.levelgen.WorldgenRandom random,
                Operation<StructurePiece> original,
                @Local(argsOnly = true) Structure.GenerationContext context) {
            var piece = original.call(pos, random);
            OceanTerrain.placeMonument(piece, context);
            return piece;
        }

        @WrapOperation(method = "regeneratePiecesAfterLoad", at = @At(value = "NEW",
            target = "net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces$MonumentBuilding"))
        private static OceanMonumentPieces.MonumentBuilding naturality$restoreHeight(
                net.minecraft.util.RandomSource random, int west, int north, net.minecraft.core.Direction direction,
                Operation<OceanMonumentPieces.MonumentBuilding> original,
                @Local(argsOnly = true) PiecesContainer saved) {
            var piece = original.call(random, west, north, direction);
            OceanTerrain.moveMonument(piece, saved.pieces().get(0).getBoundingBox().minY());
            return piece;
        }
    }

    @Mixin(NoiseBasedChunkGenerator.class)
    @org.jspecify.annotations.NullMarked
    public static abstract class OceanTerrainMixin {
        @Shadow public abstract Holder<NoiseGeneratorSettings> generatorSettings();

        @ModifyVariable(method = "applyCarvingMask", at = @At("HEAD"), argsOnly = true)
        private CarvingMask.@Nullable Filter naturality$protectOceanFloor(CarvingMask.@Nullable Filter filter,
                @Local(argsOnly = true) ChunkAccess chunk, @Local(argsOnly = true) NoiseChunk noise) {
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.OVERWORLD)
                ? OceanTerrain.protectFloor(chunk, noise, generatorSettings().value().noiseRouter().continents(), filter)
                : filter;
        }

        @Inject(method = "buildSurface", at = @At("RETURN"))
        private void naturality$oceanSediment(ChunkAccess chunk, NoiseChunk noise, RandomState random,
                BiomeManager biomes, Set<Holder<Biome>> possibleBiomes, MaterialRule rule, CallbackInfo ci) {
            if (((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.OVERWORLD)) {
                var settings = generatorSettings().value();
                OceanTerrain.surface(chunk, biomes, noise, settings.noiseRouter().continents(), settings.seaLevel());
            }
        }

        @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/NoiseRouter;finalDensity()Lnet/minecraft/world/level/levelgen/densityfunction/DensityFunction;"))
        private DensityFunction naturality$ocean(NoiseRouter router, Operation<DensityFunction> original) {
            DensityFunction density = original.call(router);
            return ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.OVERWORLD)
                ? new OceanTerrain(density, router.continents()) : density;
        }

        @WrapOperation(method = {"doFill", "iterateNoiseColumn"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/Aquifer;computeSubstance(IIID)Lnet/minecraft/world/level/block/state/BlockState;"))
        private @Nullable BlockState naturality$oceanWater(Aquifer aquifer, int x, int y, int z, double density,
                Operation<@Nullable BlockState> original, @Local NoiseChunk noiseChunk) {
            BlockState state = original.call(aquifer, x, y, z, density);
            NoiseGeneratorSettings settings = generatorSettings().value();
            if (density <= 0 && y >= -20 && y < settings.seaLevel()
                    && ((NoiseBasedChunkGenerator)(Object)this).stable(NoiseGeneratorSettings.OVERWORLD)
                    && OceanTerrain.ocean(noiseChunk.cachingSamplers().sampleValue(settings.noiseRouter().continents(), x, 0, z))) {
                return Blocks.WATER.defaultBlockState();
            }
            return state;
        }
    }
}
