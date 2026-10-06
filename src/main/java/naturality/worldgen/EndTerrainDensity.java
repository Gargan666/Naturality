package naturality.worldgen;

import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import naturality.Naturality;
import net.minecraft.util.Interval;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.densityfunction.*;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/** Connected rock shelves with related elevations and an untouched central region. */
public record EndTerrainDensity(DensityFunction input) implements DensityFunction {
    public static final MapCodec<EndTerrainDensity> CODEC = DensityFunction.CODEC.fieldOf("input")
        .xmap(EndTerrainDensity::new, EndTerrainDensity::input);

    @Override public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context), context.createRandom(Naturality.id("end_terrain")));
    }
    @Override public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return new EndTerrainDensity(rule.rewrite(input));
    }
    @Override public Interval range() { return Interval.INFINITE; }
    @Override public int domainAxes() { return ALL_AXES; }
    @Override public MapCodec<EndTerrainDensity> codec() { return CODEC; }

    public static double ease(double t) {
        t = Math.clamp(t, 0, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    public static double outerBlend(double x, double z) {
        return ease((Math.hypot(x, z) - 1024) / 256);
    }

    public static final class Sampler implements DensitySampler {
        private static final int ISLAND_SPACING = 288;
        private final DensitySampler vanilla;
        private final SimplexNoise height, detail, warp, selectorA, selectorB, selectorC, sparse, paths;
        private final long islandSeed;
        // Geometry depends only on the world seed. Bounded caches never retain chunk contexts.
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Island>> islands =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(256));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<Column>> columns =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(256));
        private final ThreadLocal<Long2ObjectLinkedOpenHashMap<SmallIsland>> smallIslands =
            ThreadLocal.withInitial(() -> new Long2ObjectLinkedOpenHashMap<>(256));

        public Sampler(DensitySampler vanilla, RandomSource random) {
            this.vanilla = vanilla;
            height = new SimplexNoise(random);
            detail = new SimplexNoise(random);
            warp = new SimplexNoise(random);
            selectorA = new SimplexNoise(random);
            selectorB = new SimplexNoise(random);
            selectorC = new SimplexNoise(random);
            sparse = new SimplexNoise(random);
            paths = new SimplexNoise(random);
            islandSeed = random.nextLong();
        }

        /** Regional height relative to vanilla's Y=64; individual islands sample it once at their anchor. */
        public double heightShift(int x, int z) {
            double regional = 0.8 * height.get(x / 2600.0, z / 2600.0)
                + 0.2 * height.get(x / 1100.0, z / 1100.0);
            return 64 + 50 * Math.tanh(1.3 * regional);
        }

        public double[] weights(int x, int z) {
            double a = Math.exp(8 * selectorA.get(x / 1400.0, z / 1400.0));
            double b = Math.exp(8 * selectorB.get(x / 1400.0, z / 1400.0));
            double c = Math.exp(8 * selectorC.get(x / 1400.0, z / 1400.0));
            double sum = a + b + c;
            return new double[]{a / sum, b / sum, c / sum};
        }

        /** Independent, smoothly changing branching-path and sparse-archipelago amounts. */
        public double[] subtypes(int x, int z) {
            return new double[]{ease((paths.get(x / 900.0, z / 900.0) + 0.35) / 0.8),
                ease((sparse.get(x / 1200.0, z / 1200.0) + 0.15) / 0.8)};
        }

        /** A beveled rock rib, with either a level shelf or a sloping path along its spine. */
        private record Rib(double x, double z, double ux, double uz, double length,
                           double width, double startHeight, double endHeight, double depth,
                           double roughness, double reach, int rock) {
            Rib asRock(int index) {
                return new Rib(x,z,ux,uz,length,width,startHeight,endHeight,depth,roughness,reach,index);
            }
        }
        private record Ramp(Rib path, Rib from, Rib to) {}
        private record Rock(double x, double z, Rib[] ribs, double reach, boolean candidate) {}
        private record Island(int gx, int gz, double x, double z, Rib[] ribs, Ramp[] ramps, Rock[] rocks, double reach) {}
        private record Slice(double footprint, double radius, double top, double depth, double roughness,
                             boolean flat, SmallIsland island) {}
        private record Column(int x, int z, double blend, Island island, Slice[] slices,
                              double edge, double ledges, double low, double high) {}
        private static final Slice[] EMPTY = new Slice[0];

        public record SmallIsland(long id, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
        private static final SmallIsland NOT_ISLAND = new SmallIsland(0,0,0,0,0,0,0);

        /** Material information derived from the same shapes that generated the blocks. */
        public static final class SurfaceMaterials {
            private final Column column;
            private final SmallIsland island;
            private final double low, high;

            private SurfaceMaterials(Column column) {
                this.column = column;
                SmallIsland selected = null; double min = 256, max = 0;
                for (Slice slice : column.slices) if (slice.island != null) {
                    selected = slice.island; min = Math.min(min,slice.top-slice.depth); max = Math.max(max,slice.top+4);
                }
                island = selected; low = min; high = max;
            }

            public SmallIsland smallIsland() { return island; }
            public boolean inSmallIsland(int y) { return island != null && y > low && y < high; }

            public double rimAffinity(int surfaceY) {
                if (column.blend < 1) return .5;
                double interior = Double.NEGATIVE_INFINITY; boolean flat = false;
                for (Slice slice : column.slices) {
                    if (Math.abs(slice.top-surfaceY)>6) continue;
                    interior = Math.max(interior,slice.footprint+column.edge);
                    flat |= slice.flat;
                }
                return flat ? ease((18-Math.max(0,interior))/12) : 0;
            }
        }

        public SurfaceMaterials surfaceMaterials(int x, int z) {
            return new SurfaceMaterials(cachedColumn(x,z));
        }

        private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
        private static <T> void remember(Long2ObjectLinkedOpenHashMap<T> cache, long key, T value, int limit) {
            if (cache.size() >= limit) cache.removeFirst();
            cache.put(key, value);
        }

        private static Rib rib(double ax, double az, double bx, double bz, double width,
                               double ay, double by, double depth, double roughness) {
            double length = Math.hypot(bx - ax, bz - az);
            return new Rib((ax + bx) * .5, (az + bz) * .5, (bx - ax) / length, (bz - az) / length,
                length, width, ay, by, depth, roughness, length * .5 + width + 12, -1);
        }

        private Island island(int gx, int gz) {
            var cache = islands.get();
            long key = key(gx, gz);
            Island saved = cache.get(key);
            if (saved != null) return saved;
            var random = RandomSource.create(islandSeed ^ ((long)gx * 341873128712L) ^ ((long)gz * 132897987541L));
            double x = gx * (double)ISLAND_SPACING + 144 + (random.nextDouble() - .5) * 72;
            double z = gz * (double)ISLAND_SPACING + 144 + (random.nextDouble() - .5) * 72;
            double[] styles = weights((int)x, (int)z), subtypes = subtypes((int)x, (int)z);
            double elevation = 64 + heightShift((int)x, (int)z) + (random.nextDouble() - .5) * 18;
            double angle = random.nextDouble() * Math.PI * 2;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            double separation = subtypes[1];
            double branching = styles[1] * (.3 + .7 * subtypes[0]);
            double scale = .9 + random.nextDouble() * .2;
            double breadth = (1 - .38 * separation) * (1 - .42 * branching);
            double roughness = 2.6 + 2.2 * styles[0] + styles[1];
            var ribs = new java.util.ArrayList<Rib>();
            var ramps = new java.util.ArrayList<Ramp>();
            Rock[] rocks = new Rock[3];
            Rib[] landings = new Rib[5];
            // A bent spine and an offset fork produce concave outlines before noise is applied.
            // Distinct landings have their own elevations; the narrow ribs between them are ramps.
            double[] u = {-94, -28, 42, 98, -12};
            double[] v = {-24, 22, -14, 34, -83};
            double[] levels = {-17, -3, 18, 31, 9};
            double[] px = new double[5], pz = new double[5], py = new double[5];
            for (int n = 0; n < 5; n++) {
                double localU = (u[n] + (random.nextDouble() - .5) * 22) * scale;
                double localV = (v[n] + (random.nextDouble() - .5) * 24) * scale;
                localV *= 1 + .45 * styles[1];
                px[n] = x + localU * cos - localV * sin;
                pz[n] = z + localU * sin + localV * cos;
                py[n] = elevation + levels[n] * (.8 + .4 * styles[1]) + random.nextDouble() * 6;
                double shelfAngle = angle + (random.nextDouble() - .5) * 1.4;
                double length = (32 + random.nextDouble() * 27) * scale;
                double width = (23 + random.nextDouble() * 13 + 7 * styles[2]) * breadth;
                double sx = Math.cos(shelfAngle) * length * .5, sz = Math.sin(shelfAngle) * length * .5;
                landings[n] = rib(px[n] - sx, pz[n] - sz, px[n] + sx, pz[n] + sz,
                    width, py[n], py[n] + 2, 64 + random.nextDouble() * 30, roughness);
                ribs.add(landings[n]);
                if (n > 0) {
                    int parent = n == 4 ? 1 : n - 1;
                    // Sparse regions break some links, leaving larger shelves among tall rock stacks.
                    if (separation < .7 || random.nextDouble() > .6 * separation) {
                        Rib path = rib(px[parent], pz[parent], px[n], pz[n],
                            (10 + random.nextDouble() * 5) * (1 - .25 * separation),
                            py[parent] + 1, py[n] + 1, 52 + random.nextDouble() * 20, .7);
                        ribs.add(path); ramps.add(new Ramp(path, landings[parent], landings[n]));
                    }
                }
            }
            // A high ledge projects over a lower landing; its approach bends around the cliff.
            double upperX = px[2] - cos * 20 - sin * 18, upperZ = pz[2] - sin * 20 + cos * 18;
            double upperY = py[2] + 22 + 7 * styles[1];
            Rib upper = rib(upperX - cos * 28, upperZ - sin * 28, upperX + cos * 22, upperZ + sin * 22,
                16 * breadth, upperY, upperY + 3, 44, 2);
            ribs.add(upper);
            double bendX = px[3] - sin * 44, bendZ = pz[3] + cos * 44;
            Rib approach = rib(px[3], pz[3], bendX, bendZ, 10, py[3] + 1, (py[3] + upperY) * .5, 48, .7);
            ribs.add(approach); ramps.add(new Ramp(approach, landings[3], null));
            Rib ascent = rib(bendX, bendZ, upperX + cos * 20, upperZ + sin * 20,
                9, (py[3] + upperY) * .5, upperY + 3, 44, .7);
            ribs.add(ascent); ramps.add(new Ramp(ascent, null, upper));
            // Satellite rocks replace vanilla discs. Offset angular ribs give them stepped tops.
            for (int n = 0; n < 3; n++) {
                double side = n == 0 ? -1 : 1;
                double ru = -100 + random.nextDouble() * 200;
                double rv = side * (103 + random.nextDouble() * 26);
                double rx = x + ru * cos - rv * sin, rz = z + ru * sin + rv * cos;
                double ry = elevation - 20 + random.nextDouble() * 54;
                double rockAngle = angle + random.nextDouble() * 1.5;
                double cx = Math.cos(rockAngle), cz = Math.sin(rockAngle);
                double width = 10 + random.nextDouble() * 9;
                double length = 12 + random.nextDouble() * 22;
                Rib lower = rib(rx - cx * length, rz - cz * length, rx + cx * length, rz + cz * length,
                    width, ry, ry + 6, 64 + random.nextDouble() * 24, 2).asRock(n);
                Rib upperRock = rib(rx - cz * width, rz + cx * width, rx + cx * length * .7, rz + cz * length * .7,
                    width * .65, ry + 14, ry + 22, 62, 1.5).asRock(n);
                ribs.add(lower); ribs.add(upperRock);
                double rockReach = Math.max(Math.hypot(lower.x-rx,lower.z-rz)+lower.reach,
                    Math.hypot(upperRock.x-rx,upperRock.z-rz)+upperRock.reach);
                // Separate random stream: material selection must not change terrain geometry.
                boolean candidate = RandomSource.create(islandSeed ^ key(gx,gz) ^ (n*0x9e3779b97f4a7c15L)
                    ^ 0x4c49524549534c45L).nextInt(20)==0;
                rocks[n] = new Rock(rx,rz,new Rib[]{lower,upperRock},rockReach,candidate);
            }
            double reach = 0;
            for (Rib rib : ribs) reach = Math.max(reach, Math.hypot(rib.x - x, rib.z - z) + rib.reach);
            Island result = new Island(gx,gz,x,z,ribs.toArray(Rib[]::new),ramps.toArray(Ramp[]::new),rocks,reach);
            remember(cache, key, result, 256);
            return result;
        }

        private SmallIsland smallIsland(Island owner, int index) {
            Rock rock = owner.rocks[index];
            if (!rock.candidate) return null;
            long id = ((long)owner.gx << 34) ^ ((owner.gz & 0xffffffffL) << 2) ^ index;
            var cache = smallIslands.get();
            SmallIsland saved = cache.get(id);
            if (saved != null) return saved == NOT_ISLAND ? null : saved;
            boolean isolated = Math.hypot(rock.x,rock.z)-rock.reach-24 >= 1280;
            // Conservative horizontal bounds exclude rocks attached to any other formation.
            // This is resolved from seeded geometry, without loading or traversing neighbor chunks.
            for (int dz=-1;dz<=1 && isolated;dz++) for (int dx=-1;dx<=1 && isolated;dx++) {
                Island neighbor = island(owner.gx+dx,owner.gz+dz);
                for (Rib other : neighbor.ribs) {
                    if (neighbor.gx==owner.gx && neighbor.gz==owner.gz && other.rock==index) continue;
                    for (Rib part : rock.ribs) {
                        double gap=1.07*(part.width+other.width)+32;
                        if (ribDistanceSquared(part,other) < gap*gap) {isolated=false;break;}
                    }
                    if(!isolated)break;
                }
            }
            SmallIsland result = NOT_ISLAND;
            if (isolated) {
                double low=256,high=0;
                for (Rib part : rock.ribs) {
                    low=Math.min(low,Math.min(part.startHeight,part.endHeight)-part.depth-8);
                    high=Math.max(high,Math.max(part.startHeight,part.endHeight)+8);
                }
                result = new SmallIsland(id,(int)Math.floor(rock.x-rock.reach-24),(int)Math.ceil(rock.x+rock.reach+24),
                    Math.max(28,(int)Math.floor(low)),Math.min(255,(int)Math.ceil(high)),
                    (int)Math.floor(rock.z-rock.reach-24),(int)Math.ceil(rock.z+rock.reach+24));
            }
            remember(cache,id,result,256);
            return result == NOT_ISLAND ? null : result;
        }

        private static double pointDistanceSquared(double x, double z, Rib rib) {
            double dx=x-rib.x,dz=z-rib.z;
            double along=Math.clamp(dx*rib.ux+dz*rib.uz,-rib.length*.5,rib.length*.5);
            dx-=along*rib.ux;dz-=along*rib.uz;
            return dx*dx+dz*dz;
        }

        /** Distance between finite rock spines; broad circles would wrongly reject almost every outcrop. */
        private static double ribDistanceSquared(Rib a, Rib b) {
            double dot=a.ux*b.ux+a.uz*b.uz,denominator=1-dot*dot;
            if(denominator>1e-10) {
                double dx=a.x-b.x,dz=a.z-b.z;
                double d=dx*a.ux+dz*a.uz,e=dx*b.ux+dz*b.uz;
                double s=(dot*e-d)/denominator,t=(e-dot*d)/denominator;
                if(Math.abs(s)<=a.length*.5 && Math.abs(t)<=b.length*.5) return 0;
            }
            double ax=a.ux*a.length*.5,az=a.uz*a.length*.5;
            double bx=b.ux*b.length*.5,bz=b.uz*b.length*.5;
            return Math.min(Math.min(pointDistanceSquared(a.x-ax,a.z-az,b),pointDistanceSquared(a.x+ax,a.z+az,b)),
                Math.min(pointDistanceSquared(b.x-bx,b.z-bz,a),pointDistanceSquared(b.x+bx,b.z+bz,a)));
        }

        private Column column(int x, int z) {
            double blend = outerBlend(x, z);
            if (blend == 0) return new Column(x, z, 0, null, EMPTY, 0, 0, 0, 0);
            double wx = x + 17 * warp.get(x / 180.0, z / 180.0) + 4 * warp.get(x / 47.0, z / 47.0);
            double wz = z + 17 * warp.get((x + 731) / 180.0, (z - 269) / 180.0)
                + 4 * warp.get((x - 183) / 47.0, (z + 419) / 47.0);
            int gx = (int)Math.floor(wx / ISLAND_SPACING), gz = (int)Math.floor(wz / ISLAND_SPACING);
            Island nearest = null;
            double first = Double.POSITIVE_INFINITY, low = 256, high = 0;
            double edge = 3.5 * detail.get(x / 53.0, z / 53.0) + 1.5 * detail.get(x / 23.0, z / 23.0);
            double hills = detail.get(x / 83.0, z / 83.0) + .3 * detail.get(x / 27.0, z / 27.0);
            var slices = new java.util.ArrayList<Slice>(4);
            // Union all nearby formations. There is no nearest-island partition cutting bridges.
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                Island i = island(gx + dx, gz + dz);
                double ix = wx - i.x, iz = wz - i.z, distance = ix * ix + iz * iz;
                if (distance < first) { first = distance; nearest = i; }
                if (distance > i.reach * i.reach) continue;
                for (Rib r : i.ribs) {
                    double rx = wx - r.x, rz = wz - r.z;
                    if (Math.abs(rx) > r.reach || Math.abs(rz) > r.reach) continue;
                    double along = rx * r.ux + rz * r.uz, across = Math.abs(-rx * r.uz + rz * r.ux);
                    double end = Math.abs(along) - r.length * .5;
                    // Beveled angular ends rather than elliptical distance fields.
                    double footprint = Math.min(r.width - across,
                        Math.min(r.width * .7 - end, (r.width * 1.35 - across - end) * .707));
                    if (footprint + edge <= -6) continue;
                    double t = Math.clamp(along / r.length + .5, 0, 1);
                    double top = r.startHeight + (r.endHeight - r.startHeight) * t + hills * r.roughness;
                    // Cut the approach into its destination cliff. Unioning a ramp with a tall
                    // shelf alone leaves a vertical wall where the two footprints overlap.
                    for (Ramp ramp : i.ramps) {
                        if (r != ramp.from && r != ramp.to) continue;
                        Rib path = ramp.path;
                        double qx = wx - path.x, qz = wz - path.z;
                        double alongPath = qx * path.ux + qz * path.uz;
                        if (Math.abs(alongPath) > path.length * .5) continue;
                        double acrossPath = Math.abs(-qx * path.uz + qz * path.ux);
                        if (acrossPath > path.width) continue;
                        double pathT = alongPath / path.length + .5;
                        double pathTop = path.startHeight + (path.endHeight - path.startHeight) * pathT + hills * .7;
                        double shoulder = Math.max(0, acrossPath - path.width * .65) * 5;
                        top = Math.min(top, pathTop + shoulder);
                    }
                    double depth = Math.min(r.depth, top - 28);
                    slices.add(new Slice(footprint,r.width,top,depth,r.roughness,
                        Math.abs(r.endHeight-r.startHeight)/r.length<.12,r.rock<0?null:smallIsland(i,r.rock)));
                    low = Math.min(low, top - depth); high = Math.max(high, top + 4);
                }
            }
            return new Column(x, z, blend, nearest, slices.toArray(Slice[]::new), edge,
                detail.get(x / 80.0, z / 80.0) * 8, low, high);
        }

        private double shaped(Column c, int y) {
            if (c.slices.length == 0 || y <= c.low || y >= c.high) return -1;
            double erosion = 2.4 * detail.get(c.x / 31.0, y / 23.0, c.z / 31.0)
                + 1.1 * warp.get(c.x / 13.0, y / 17.0, c.z / 13.0);
            double ledge = 1.2 * Math.sin((y + c.ledges) / 7);
            double body = -12;
            for (Slice s : c.slices) {
                double below = s.top - y;
                if (below < -4 || below >= s.depth) continue;
                double progress = Math.max(0, below - 14) / (s.depth - 14);
                // Tall shoulders taper to a keel. Noise fades at the tip, so low formations
                // close naturally above Y=28 instead of meeting a common clipping plane.
                double taper = s.radius * progress * progress;
                double edge = (c.edge + erosion + ledge) * (1 - progress);
                double side = s.footprint - taper + edge;
                double cap = below + erosion * Math.min(.35, s.roughness * .15);
                body = Math.max(body, Math.min(side, cap));
            }
            return body / 12;
        }

        @Override public float sampleValue(SamplerContext context, int x, int y, int z) {
            if (outerBlend(x, z) == 0) return vanilla.sampleValue(context, x, y, z);
            Column column = cachedColumn(x,z);
            double shaped = shaped(column, y);
            if (column.blend == 1) return (float)shaped;
            double exact = vanilla.sampleValue(context, x, y, z);
            return (float)(exact + (shaped - exact) * column.blend);
        }

        private Column cachedColumn(int x, int z) {
            var cache = columns.get();
            long key = key(x, z);
            Column column = cache.get(key);
            if (column == null) {
                column = column(x, z);
                remember(cache, key, column, 256);
            }
            return column;
        }

        @Override public void sampleVolume(SamplerContext context, DensityBuffer output, DensityVolume volume) {
            if (outerBlend(volume.minBlockX(), volume.minBlockZ()) == 0
                && outerBlend(volume.maxBlockX(), volume.minBlockZ()) == 0
                && outerBlend(volume.minBlockX(), volume.maxBlockZ()) == 0
                && outerBlend(volume.maxBlockX(), volume.maxBlockZ()) == 0) {
                vanilla.sampleVolume(context, output, volume);
                return;
            }
            // The transition used to invoke vanilla's whole noise graph separately for every voxel.
            // Sample that graph once into the output, then blend the custom islands over it in place.
            int nearX = Math.clamp(0, volume.minBlockX(), volume.maxBlockX());
            int nearZ = Math.clamp(0, volume.minBlockZ(), volume.maxBlockZ());
            boolean needsVanilla = (long)nearX * nearX + (long)nearZ * nearZ < 1280L * 1280;
            if (needsVanilla) vanilla.sampleVolume(context, output, volume);
            int index = 0;
            for (int z = 0; z < volume.sizeZ(); z++) for (int x = 0; x < volume.sizeX(); x++) {
                Column column = column(volume.blockX(x), volume.blockZ(z));
                if (column.blend == 0) { index += volume.sizeY(); continue; }
                if (column.blend == 1 && column.slices.length == 0) {
                    output.setRange(index, volume.sizeY(), -1);
                    index += volume.sizeY();
                    continue;
                }
                for (int y = 0; y < volume.sizeY(); y++) {
                    double shaped = shaped(column, volume.blockY(y));
                    double value = shaped;
                    if (column.blend != 1) {
                        double exact = output.get(index);
                        value = exact + (shaped - exact) * column.blend;
                    }
                    output.set(index++, (float)value);
                }
            }
        }
    }
}
