package naturality.systems.weather.wind;

import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import naturality.systems.weather.framework.WeatherSystem.WeatherState;
import naturality.systems.weather.framework.WeatherSystem;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.VegetationBlock;

/** Resolve intrusion before vanilla movement, which cannot resolve existing overlaps. */
@org.jspecify.annotations.NullMarked
public final class WindPush {
    public static final naturality.systems.weather.framework.WeatherSystem.ChannelDefinition CYCLE =
        new naturality.systems.weather.framework.WeatherSystem.ChannelDefinition(
            0x57494E44L, 1 - Math.pow(.65, 1.0 / 6000), 8000, 24000, 4200,
            profile -> profile.minWind, profile -> profile.maxWind);

    private WindPush() {}
    /** @return true when solid surroundings leave no safe escape this tick. */
    public static boolean resolve(Player player) {
        if(player.isSpectator() || player.noPhysics || !WindShapes.active(player.level()))return false;
        var level=player.level();
        var box=player.getBoundingBox();
        var overlaps=new ArrayList<AABB>();
        for(var pos:BlockPos.betweenClosed(BlockPos.containing(box.minX-1,box.minY-1,box.minZ-1),
                BlockPos.containing(box.maxX+1,box.maxY+1,box.maxZ+1))) {
            if(!naturality.api.worlds.LoadedChunks.has(level,pos))continue;
            var state=level.getBlockState(pos);
            if(!WindShapes.eligible(state) || WindShapes.offset(level,pos,state).equals(Vec3.ZERO))continue;
            for(var part:state.getCollisionShape(level,pos).toAabbs()) {
                var world=part.move(pos);
                if(world.intersects(box.deflate(1e-7)))overlaps.add(world);
            }
        }
        if(overlaps.isEmpty())return false;
        double left=0,right=0,north=0,south=0,up=0;
        for(var part:overlaps) {
            left=Math.min(left,part.minX-box.maxX-1e-5);
            right=Math.max(right,part.maxX-box.minX+1e-5);
            north=Math.min(north,part.minZ-box.maxZ-1e-5);
            south=Math.max(south,part.maxZ-box.minZ+1e-5);
            up=Math.max(up,part.maxY-box.minY+1e-5);
        }
        var candidates=new ArrayList<>(java.util.List.of(new Vec3(left,0,0),new Vec3(right,0,0),
            new Vec3(0,0,north),new Vec3(0,0,south),new Vec3(0,up,0)));
        candidates.sort(Comparator.comparingDouble(Vec3::lengthSqr));
        for(var shift:candidates) {
            if(shift.lengthSqr()>2.25)continue;
            var target=box.move(shift);
            if(!level.noCollision(player,target))continue;
            // Do not escape through a wall to reach an otherwise empty destination.
            var swept=box.minmax(target).deflate(1e-7);
            boolean blocked=false;
            for(var shape:level.getBlockCollisions(player,swept))for(var part:shape.toAabbs()) {
                if(part.intersects(swept) && !part.intersects(box))blocked=true;
            }
            if(blocked)continue;
            player.setPos(player.getX()+shift.x,player.getY()+shift.y,player.getZ()+shift.z);
            return false;
        }
        return true;
    }

    /** Rigid gameplay displacement: broad sway only, settling to a steady severe-wind pose. */
    public static final class WindShapes {
        public static final ThreadLocal<ClipContext> PICK=new ThreadLocal<>();
        // Keep the tiny counter in place: remove/set used to allocate a ThreadLocal
        // entry again for almost every block-shape query, including ordinary terrain.
        private static final ThreadLocal<int[]> RAW=ThreadLocal.withInitial(() -> new int[1]);
        private static final java.util.Map<Level,Cache> CACHE=new java.util.WeakHashMap<>();
        private static final class Cache {
            long tick=Long.MIN_VALUE;
            WeatherState weather=WeatherState.CLEAR;
            final java.util.Map<BlockPos,Entry> values=new java.util.HashMap<>();
        }
        private static final class Entry {
            final BlockState state;
            final Vec3 offset;
            // A small identity cache retains context-specific collision/outline variants.
            // Shapes are immutable; entries already expire with the tick, weather or state.
            java.util.@org.jspecify.annotations.Nullable IdentityHashMap<VoxelShape,VoxelShape> moved;
            Entry(BlockState state,Vec3 offset) {this.state=state;this.offset=offset;}
            VoxelShape move(VoxelShape shape) {
                if(offset.equals(Vec3.ZERO))return shape;
                var cache = moved;
                if(cache==null) { cache=new java.util.IdentityHashMap<>(4); moved=cache; }
                var result=cache.get(shape);
                if(result==null) {
                    result=shape.move(offset.x,0,offset.z);
                    if(cache.size()>=4)cache.clear();
                    cache.put(shape,result);
                }
                return result;
            }
        }
        private WindShapes() {}
        public static <T> T raw(Supplier<T> work) {
            var depth=RAW.get();depth[0]++;
            try {return work.get();} finally {depth[0]--;}
        }
        public static boolean eligible(BlockState state) {
            return state.is(BlockTags.LEAVES) || state.getBlock() instanceof VineBlock || state.is(Blocks.SNOW);
        }
        public static boolean active(BlockGetter view) {
            if(!(view instanceof Level level))return false;
            var weather=WeatherSystem.state(level);
            return weather!=null && weather.wind()>0;
        }
        public static VoxelShape move(BlockGetter view,BlockPos pos,BlockState state,VoxelShape shape) {
            if(!(view instanceof Level level) || shape.isEmpty() || !eligible(state) || RAW.get()[0]!=0 || naturality.api.geometry.ShapeRecursionGuard.active())return shape;
            var weather=WeatherSystem.state(level);
            if(weather==null || weather.wind()==0)return shape;
            synchronized(CACHE) {return entry(level,pos,state,weather).move(shape);}
        }
        public static Vec3 offset(BlockGetter view,BlockPos pos,BlockState state) {
            if(!(view instanceof Level level) || !eligible(state))return Vec3.ZERO;
            var weather=WeatherSystem.state(level);
            if(weather==null || weather.wind()==0)return Vec3.ZERO;
            synchronized(CACHE) {
                return entry(level,pos,state,weather).offset;
            }
        }
        /** Called under CACHE's lock, shared by offsets and translated shape variants. */
        private static Entry entry(Level level,BlockPos pos,BlockState state,WeatherState weather) {
            var cache=CACHE.computeIfAbsent(level,_ -> new Cache());
            if(cache.tick!=level.getGameTime() || !cache.weather.equals(weather)) {
                cache.values.clear();cache.tick=level.getGameTime();cache.weather=weather;
            }
            var previous=cache.values.get(pos);
            if(previous!=null && previous.state==state)return previous;
            var result=new Entry(state,raw(() -> compute(level,pos,state,weather)));
            if(cache.values.size()>=4096)cache.values.clear();
            cache.values.put(pos.immutable(),result);
            return result;
        }
        private static Vec3 compute(Level level,BlockPos pos,BlockState state,WeatherState weather) {
            if(state.is(Blocks.SNOW)) {
                for(int depth=1;depth<=7;depth++) {
                    var support=pos.below(depth); var below=level.getBlockState(support);
                    if(below.is(BlockTags.LEAVES))return compute(level,support,below,weather);
                    if(!below.is(Blocks.SNOW))break;
                }
                return Vec3.ZERO;
            }
            if(!state.getFluidState().isEmpty() || !WindShelter.hasOpenSkyPath(level,pos,p -> level.getBrightness(LightLayer.SKY,p)))return Vec3.ZERO;
            boolean leaf=state.is(BlockTags.LEAVES);
            Vec3 shift=broad(weather,level.getGameTime(),pos,leaf);
            if(leaf)return shift;
            // Represent the vine's bending top segment by the mean of its free
            // displacement and its anchored upper edge, keeping the shape rigid.
            if(!level.getBlockState(pos.above()).is(state.getBlock())) {
                Vec3 anchor=null;
                for(var direction:Direction.values()) {
                    if(direction==Direction.DOWN || !state.getValue(VineBlock.getPropertyForFace(direction)))continue;
                    var support=pos.relative(direction); var block=level.getBlockState(support);
                    if(block.is(BlockTags.LEAVES)) { if(anchor==null)anchor=compute(level,support,block,weather); }
                    else if(block.isFaceSturdy(level,support,direction.getOpposite())) { anchor=Vec3.ZERO; break; }
                }
                if(anchor!=null)shift=shift.add(anchor).scale(.5);
            }
            boolean wallSupported=false;
            for(var face:Direction.Plane.HORIZONTAL) {
                if(!state.getValue(VineBlock.getPropertyForFace(face)))continue;
                var support=pos.relative(face);var block=level.getBlockState(support);
                if(!block.is(BlockTags.LEAVES) && block.isFaceSturdy(level,support,face.getOpposite()))wallSupported=true;
            }
            if(wallSupported)shift=shift.scale(.04);
            double x=shift.x,z=shift.z;
            for(var face:Direction.Plane.HORIZONTAL) {
                if(!state.getValue(VineBlock.getPropertyForFace(face)))continue;
                var support=pos.relative(face);var block=level.getBlockState(support);
                if(block.is(BlockTags.LEAVES) || !block.isFaceSturdy(level,support,face.getOpposite()))continue;
                switch(face) { case WEST -> x=Math.max(x,0); case EAST -> x=Math.min(x,0);
                    case NORTH -> z=Math.max(z,0); case SOUTH -> z=Math.min(z,0); default -> {} }
            }
            return new Vec3(x,0,z);
        }
        public static Vec3 broad(WeatherState weather,long tick,BlockPos pos,boolean leaf) {
            double x=((pos.getX()>>4)<<4)%4096+(pos.getX()&15)+.5;
            double z=((pos.getZ()>>4)<<4)%4096+(pos.getZ()&15)+.5;
            double phase=(x*111+z*85)*(Math.PI*2/4096)+(leaf?0:(pos.getY()+.5)*.08);
            double time=(tick%24000)/20.0;
            double wave=Math.sin(time*1.256637+phase)*.7+Math.sin(time*3.141593+phase*2)*.3;
            double severe=Math.clamp((weather.wind()/100.0-.8)/.2,0,1);
            severe=severe*severe*(3-2*severe);
            double amount=(wave*(1-severe)+severe)*(leaf?.12:.22);
            return new Vec3(weather.windX()*amount,0,weather.windZ()*amount);
        }
    }

    /** Shared geometric shelter probes for rendering and physical wind. */
    public static final class WindShelter {
        private static final int[] EXPOSURE_DISTANCES={4,8};
        private static final int[] EXPOSURE_HEIGHTS={3};
        public static boolean hasOpenSkyPath(net.minecraft.world.level.BlockGetter level,
                net.minecraft.core.BlockPos pos, java.util.function.ToIntFunction<net.minecraft.core.BlockPos> sky) {
            if (sky.applyAsInt(pos) <= 0) return false;
            Vec3 origin = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
            if (openSkyColumn(level, origin, sky)) return true;
            for (int distance : EXPOSURE_DISTANCES) for (int dy : EXPOSURE_HEIGHTS)
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    if ((dx == 0 && dz == 0) || Math.max(Math.abs(dx), Math.abs(dz)) != 1) continue;
                    Vec3 target = origin.add(dx * distance, dy, dz * distance);
                    var targetPos = net.minecraft.core.BlockPos.containing(target);
                    if (sky.applyAsInt(targetPos.above(5)) <= 0) continue;
                    if (passable(level, targetPos) && openSkyColumn(level, target, sky) && clearRay(level, origin, target)) return true;
                }
            return false;
        }
        private static boolean openSkyColumn(net.minecraft.world.level.BlockGetter level, Vec3 origin, java.util.function.ToIntFunction<net.minecraft.core.BlockPos> sky) {
            Vec3 top = origin.add(0, 5, 0);
            var topPos = net.minecraft.core.BlockPos.containing(top);
            return sky.applyAsInt(topPos) > 0 && passable(level, topPos)
                && clearColumn(level, origin, top);
        }
        private static boolean clearColumn(net.minecraft.world.level.BlockGetter level, Vec3 from, Vec3 to) {
            // These endpoints are block centers exactly five blocks apart. The old
            // quarter-block ray visits these same six cells, in the same order.
            var pos = net.minecraft.core.BlockPos.containing(from).mutable();
            int bottom = pos.getY();
            for (int y = bottom; y <= bottom + 5; y++) {
                pos.setY(y);
                var state = level.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock) continue;
                var shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) return false;
            }
            return true;
        }
        private static boolean passable(net.minecraft.world.level.BlockGetter level,
                net.minecraft.core.BlockPos pos) {
            var state = level.getBlockState(pos);
            return state.isAir() || state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock
                || state.getCollisionShape(level, pos).isEmpty();
        }
        private static boolean clearRay(net.minecraft.world.level.BlockGetter level, Vec3 from, Vec3 to) {
            double span = Math.max(Math.max(Math.abs(to.x - from.x), Math.abs(to.y - from.y)), Math.abs(to.z - from.z));
            int steps = Math.max(1, (int)Math.ceil(span * 4));
            var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
            int previousX = Integer.MIN_VALUE, previousY = Integer.MIN_VALUE, previousZ = Integer.MIN_VALUE;
            for (int i = 1; i < steps; i++) {
                double fraction = i / (double)steps;
                // Match Vec3.lerp's arithmetic and quarter-block samples exactly.
                int x = net.minecraft.util.Mth.floor(net.minecraft.util.Mth.lerp(fraction, from.x, to.x));
                int y = net.minecraft.util.Mth.floor(net.minecraft.util.Mth.lerp(fraction, from.y, to.y));
                int z = net.minecraft.util.Mth.floor(net.minecraft.util.Mth.lerp(fraction, from.z, to.z));
                if (x == previousX && y == previousY && z == previousZ) continue;
                previousX = x; previousY = y; previousZ = z;
                pos.set(x, y, z);
                var state = level.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES) || state.getBlock() instanceof VegetationBlock) continue;
                var shape = state.getCollisionShape(level, pos);
                if (!shape.isEmpty() && shape.clip(from, to, pos) != null) return false;
            }
            return true;
        }
    }
}
