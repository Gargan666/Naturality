package naturality.worldgen.end.lirestonecrags;

import naturality.worldgen.end.endwilds.EndWilds;
import naturality.Naturality;
import net.fabricmc.fabric.api.biome.v1.TheEndBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import naturality.worldgen.end.terrain.EndTerrainDensity;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import naturality.bootstrap.ContentRegistration.NaturalityBlocks;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class LirestoneCrags {
    public static final ResourceKey<Biome> BIOME=ResourceKey.create(Registries.BIOME,Naturality.id("lirestone_crags"));
    private LirestoneCrags() {}

    /** Seeded bowls are evaluated in world coordinates, but only edit the supplied chunk. */
    public static void carveCraters(net.minecraft.world.level.chunk.ChunkAccess chunk, long seed,
            net.minecraft.world.level.biome.BiomeResolver biomes) {
        if (!naturality.config.startup.FeatureConfig.Feature.LIRESTONE_CRAGS.enabled()) return;
        int minX = chunk.getPos().getMinBlockX(), minZ = chunk.getPos().getMinBlockZ();
        var pos = new BlockPos.MutableBlockPos();
        // One jittered candidate per 24-block cell; neighboring cells supply border-crossing bowls.
        for (int gz = Math.floorDiv(minZ - 9, 24); gz <= Math.floorDiv(minZ + 24, 24); gz++) {
            for (int gx = Math.floorDiv(minX - 9, 24); gx <= Math.floorDiv(minX + 24, 24); gx++) {
                var random = RandomSource.create(seed ^ 0x435241544552534CL
                    ^ (long)gx * 341873128712L ^ (long)gz * 132897987541L);
                if (random.nextInt(4) == 0) continue;
                int cx = gx * 24 + random.nextInt(24), cz = gz * 24 + random.nextInt(24);
                if (Math.hypot(cx, cz) < 1280
                        || !biomes.getNoiseBiome(QuartPos.fromBlock(cx), 0, QuartPos.fromBlock(cz)).is(BIOME)) continue;
                double radius = 4 + random.nextDouble() * 3;
                double stretch = .85 + random.nextDouble() * .3;
                double depth = 3 + random.nextDouble() * 2;
                double phase = random.nextDouble() * Math.PI * 2;
                for (int z = Math.max(minZ, cz - 9); z <= Math.min(minZ + 15, cz + 9); z++) {
                    for (int x = Math.max(minX, cx - 9); x <= Math.min(minX + 15, cx + 9); x++) {
                        if (!biomes.getNoiseBiome(QuartPos.fromBlock(x), 0, QuartPos.fromBlock(z)).is(BIOME)) continue;
                        double dx = (x - cx) / stretch, dz = (z - cz) * stretch;
                        double angle = Math.atan2(dz, dx);
                        double edge = radius * (1 + .08 * Math.sin(angle * 3 + phase));
                        double distance = (dx * dx + dz * dz) / (edge * edge);
                        int cut = (int)Math.floor(depth * (1 - distance) + .5);
                        if (cut <= 0) continue;
                        // Find the upper rock surface without querying or loading a neighboring chunk.
                        int top = chunk.getMaxY();
                        while (top >= chunk.getMinY()) {
                            pos.set(x, top, z);
                            if (!chunk.getBlockState(pos).isAir()) break;
                            top--;
                        }
                        int thickness = 0;
                        while (thickness < cut + 2 && top - thickness >= chunk.getMinY()) {
                            pos.set(x, top - thickness, z);
                            if (!LirestoneBoulderFeature.isEndStone(chunk.getBlockState(pos))) break;
                            thickness++;
                        }
                        // Thin outcrops retain at least two blocks rather than opening into the void.
                        for (int i = 0; i < Math.min(cut, thickness - 2); i++) {
                            pos.set(x, top - i, z);
                            chunk.setBlockState(pos, Blocks.AIR.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    public static void initialize() {
        if (!naturality.config.startup.FeatureConfig.Feature.LIRESTONE_CRAGS.enabled()) return;
        TheEndBiomes.addHighlandsBiome(BIOME,1.5);
        TheEndBiomes.addSmallIslandsBiome(BIOME,1.5);
        TheEndBiomes.addMidlandsBiome(BIOME,BIOME,1);
        TheEndBiomes.addBarrensBiome(BIOME,BIOME,1);
    }

    /** All four horizontal biome cells that Minecraft's biome zoom can select for this column. */
    public static boolean[] columns(BiomeManager biomes, int minX, int minZ) {
        boolean[] halo=new boolean[36],result=new boolean[256];
        int qx=QuartPos.fromBlock(minX)-1,qz=QuartPos.fromBlock(minZ)-1;
        for(int z=0;z<6;z++)for(int x=0;x<6;x++)
            halo[x+z*6]=biomes.getNoiseBiomeAtQuart(qx+x,0,qz+z).is(BIOME);
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int at=((x-2)>>2)+1+(((z-2)>>2)+1)*6;
            result[x+z*16]=halo[at] || halo[at+1] || halo[at+6] || halo[at+7];
        }
        return result;
    }

    /** A coherent, uneven fringe outside the biome, hiding the abrupt material cutoff. */
    public static boolean[] conversionColumns(BiomeManager biomes, int minX, int minZ, long seed) {
        return conversionColumns((net.minecraft.world.level.biome.BiomeResolver)biomes::getNoiseBiomeAtQuart,minX,minZ,seed);
    }

    public static boolean[] conversionColumns(net.minecraft.world.level.biome.BiomeResolver biomes,
            int minX, int minZ, long seed) {
        boolean[] result=new boolean[256];
        var random=net.minecraft.util.RandomSource.create(seed ^ 0x4352414753454447L);
        var broad=new net.minecraft.world.level.levelgen.synth.SimplexNoise(random);
        var detail=new net.minecraft.world.level.levelgen.synth.SimplexNoise(random);
        // Cache the complete fringe halo once; all samples use absolute world coordinates.
        int size=26,qx=QuartPos.fromBlock(minX)-11,qz=QuartPos.fromBlock(minZ)-11;
        boolean[] halo=new boolean[size*size];
        boolean any=false;
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            boolean crags=biomes.getNoiseBiome(qx+x,0,qz+z).is(BIOME);
            halo[x+z*size]=crags;any|=crags;
        }
        if(!any)return result;
        for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
            int wx=minX+x,wz=minZ+z;
            int baseX=QuartPos.fromBlock(wx-2)-qx,baseZ=QuartPos.fromBlock(wz-2)-qz;
            int at=baseX+baseZ*size;
            if(halo[at] || halo[at+1] || halo[at+size] || halo[at+size+1]) {
                result[x+z*16]=true;continue;
            }
            double reach=22+11*broad.get(wx/38.0,wz/38.0)+5*detail.get(wx/11.0,wz/11.0);
            // A circular search rather than a square expansion avoids axis-aligned seams.
            int cx=QuartPos.fromBlock(wx)-qx,cz=QuartPos.fromBlock(wz)-qz;
            int radius=(int)Math.ceil(reach/4);
            search: for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                if((dx*dx+dz*dz)*16>reach*reach)continue;
                if(halo[cx+dx+(cz+dz)*size]) {result[x+z*16]=true;break search;}
            }
        }
        return result;
    }

    /** Exclude the complete tree footprint, including crowns and the sprouts beside its root. */
    public static boolean near(LevelReader level, BlockPos root, int radius) {
        int minX=QuartPos.fromBlock(root.getX()-radius-2),maxX=QuartPos.fromBlock(root.getX()+radius+2);
        int minZ=QuartPos.fromBlock(root.getZ()-radius-2),maxZ=QuartPos.fromBlock(root.getZ()+radius+2);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            var biome=level.getNoiseBiome(x,0,z);
            if(biome.is(BIOME) || biome.is(EndWilds.BIOME))return true;
        }
        return false;
    }

    /** Continuous deposits in world coordinates, independent of chunk borders or generation order. */
    public static final class LirestonePatches {
        private final SimplexNoise shape;
        private final SimplexNoise edge;
        private final SimplexNoise regions;
        private final java.util.Map<EndTerrainDensity.Sampler.SmallIsland,Boolean> islands = new java.util.HashMap<>();

        public LirestonePatches(long seed) {
            var random = RandomSource.create(seed ^ 0x4c49524553544f4eL);
            shape = new SimplexNoise(random);
            edge = new SimplexNoise(random);
            regions = new SimplexNoise(random);
        }

        /** Broad areas have no deposits at all, with a smooth increase toward richer regions. */
        public double regionalAbundance(int x, int z) {
            return EndTerrainDensity.ease((regions.get(x / 1600.0, z / 1600.0) + .2) / .6);
        }

        public boolean contains(int x, int y, int z) {
            return contains(x, y, z, 1, regionalAbundance(x, z));
        }

        public boolean contains(int x, int y, int z, int depth, double abundance) {
            return contains(x,y,z,depth,abundance,1);
        }

        public boolean contains(int x, int y, int z, int depth, double abundance, double rimAffinity) {
            if (abundance == 0) return false;
            // Favor deposits near each ledge's upper surface. Deep cliff faces and undersides
            // retain only occasional deposits, rather than exposing veins all the way down.
            double burial = EndTerrainDensity.ease((depth - 3) / 12.0);
            double threshold = .56 + .7 * (1 - abundance) + .32 * burial + .34 * (1-rimAffinity);
            double body = shape.get(x / 28.0, y / 21.0, z / 28.0);
            // Only evaluate fine detail near the deposit boundary.
            if (body < threshold - .22) return false;
            if (body > threshold + .22) return true;
            return body + .22 * edge.get(x / 9.0, y / 8.0, z / 9.0) > threshold;
        }

        public boolean converts(EndTerrainDensity.Sampler.SmallIsland island) {
            if (island==null) return false;
            Boolean saved=islands.get(island);
            if(saved!=null)return saved;
            boolean allowed=regionalAbundance((island.minX()+island.maxX())/2,(island.minZ()+island.maxZ())/2)>0;
            // Make one decision for the complete island, including portions in neighboring chunks.
            // Entirely Lirestone islands are excluded wherever the regional field has zero abundance.
            for(int z=island.minZ();z<=island.maxZ() && allowed;z++)
                for(int x=island.minX();x<=island.maxX();x++)
                    if(regionalAbundance(x,z)==0){allowed=false;break;}
            islands.put(island,allowed);
            return allowed;
        }
    }

    /** Small, partly buried rocks. Validate the whole footprint before placing any blocks. */
    public record LirestoneBoulderFeature() implements Feature {
        public static final MapCodec<LirestoneBoulderFeature> CODEC = MapCodec.unit(LirestoneBoulderFeature::new);
        public static final ResourceKey<PlacedFeature> PLACED = ResourceKey.create(
            Registries.PLACED_FEATURE, Naturality.id("lirestone_boulder"));

        public static void initialize() {
            Registry.register(BuiltInRegistries.FEATURE_TYPE, Naturality.id("lirestone_boulder"), CODEC);
            if (!naturality.config.startup.FeatureConfig.Feature.LIRESTONE_BOULDERS.enabled()) return;
            BiomeModifications.addFeature(BiomeSelectors.foundInTheEnd(), GenerationStep.Decoration.LOCAL_MODIFICATIONS, PLACED);
        }

        @Override public MapCodec<LirestoneBoulderFeature> codec() { return CODEC; }

        public static boolean isEndStone(BlockState state) {
            return state.is(Blocks.END_STONE) || state.is(NaturalityBlocks.SMOOTH_ENDSTONE)
                || state.is(NaturalityBlocks.LIRESTONE) || state.is(NaturalityBlocks.SMOOTH_LIRESTONE);
        }

        @Override public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
            if (!naturality.config.startup.FeatureConfig.Feature.LIRESTONE_BOULDERS.enabled()) return false;
            // Material patches can reach the main island; additional geometry remains in the outer End.
            if (level.getLevel().dimension() != Level.END || Math.hypot(origin.getX(), origin.getZ()) < 1280
                    || !isEndStone(level.getBlockState(origin.below()))) return false;
            if (!nearRim(level,origin) && random.nextInt(5)!=0) return false;
            int rx = 2 + random.nextInt(2), rz = 2 + random.nextInt(2), height = 2 + random.nextInt(3);
            int leanX = random.nextBoolean() ? 1 : -1, leanZ = random.nextBoolean() ? 1 : -1;
            BlockPos base = origin.below();
            var blocks = new ArrayList<BlockPos>();
            for (int x = -rx-1; x <= rx+1; x++) for (int z = -rz-1; z <= rz+1; z++) {
                boolean first = true;
                for (int y = -2; y <= height; y++) {
                    double vertical = y / (y < 0 ? 2.4 : (double)height);
                    double main = x*x / (double)(rx*rx) + z*z / (double)(rz*rz) + vertical*vertical;
                    double side = (x-leanX)*(x-leanX) / (rx*rx*.7)
                        + (z-leanZ)*(z-leanZ) / (rz*rz*.7) + (y+.5)*(y+.5) / (height*height*.65);
                    if (Math.min(main, side) > 1) continue;
                    BlockPos pos = base.offset(x, y, z);
                    if (level.isOutsideBuildHeight(pos)) return false;
                    var existing = level.getBlockState(pos);
                    if (!existing.isAir() && !isEndStone(existing)) return false;
                    // Every occupied column must sit on rock; cliffs, void and structures reject the attempt.
                    if (first && !isEndStone(level.getBlockState(pos.below()))) return false;
                    first = false;
                    blocks.add(pos);
                }
            }
            for (BlockPos pos : blocks) {
                var state=level.getBlockState(pos);
                var material=state.is(NaturalityBlocks.SMOOTH_ENDSTONE) || state.is(NaturalityBlocks.SMOOTH_LIRESTONE)
                    ? NaturalityBlocks.SMOOTH_LIRESTONE : NaturalityBlocks.LIRESTONE;
                level.setBlock(pos,material.defaultBlockState(),2);
            }
            return !blocks.isEmpty();
        }

        public static boolean nearRim(net.minecraft.world.level.LevelReader level, BlockPos origin) {
            BlockPos shoulder=origin.below(4);
            return level.getBlockState(shoulder.offset(8,0,0)).isAir()
                || level.getBlockState(shoulder.offset(-8,0,0)).isAir()
                || level.getBlockState(shoulder.offset(0,0,8)).isAir()
                || level.getBlockState(shoulder.offset(0,0,-8)).isAir();
        }
    }
}
