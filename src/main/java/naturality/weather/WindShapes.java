package naturality.weather;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Rigid gameplay displacement: broad sway only, settling to a steady severe-wind pose. */
public final class WindShapes {
    public static final ThreadLocal<ClipContext> PICK=new ThreadLocal<>();
    private static final ThreadLocal<Integer> RAW=ThreadLocal.withInitial(() -> 0);
    private static final java.util.Map<Level,Cache> CACHE=new java.util.WeakHashMap<>();
    private static final class Cache {
        long tick=Long.MIN_VALUE;
        WeatherState weather=WeatherState.CLEAR;
        final java.util.Map<BlockPos,Entry> values=new java.util.HashMap<>();
    }
    private record Entry(BlockState state,Vec3 offset) {}
    private WindShapes() {}
    public static <T> T raw(Supplier<T> work) {
        int previous=RAW.get(); RAW.set(previous+1);
        try { return work.get(); } finally { if(previous==0)RAW.remove(); else RAW.set(previous); }
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
        if(!(view instanceof Level) || RAW.get()!=0 || naturality.snow.ShapeRecursionGuard.active() || shape.isEmpty() || !eligible(state))return shape;
        Vec3 offset=offset(view,pos,state);
        return offset.equals(Vec3.ZERO)?shape:shape.move(offset.x,0,offset.z);
    }
    public static Vec3 offset(BlockGetter view,BlockPos pos,BlockState state) {
        if(!(view instanceof Level level) || !eligible(state))return Vec3.ZERO;
        var weather=WeatherSystem.state(level);
        if(weather==null || weather.wind()==0)return Vec3.ZERO;
        synchronized(CACHE) {
            var cache=CACHE.computeIfAbsent(level,ignored -> new Cache());
            if(cache.tick!=level.getGameTime() || !cache.weather.equals(weather)) {
                cache.values.clear();cache.tick=level.getGameTime();cache.weather=weather;
            }
            var previous=cache.values.get(pos);
            if(previous!=null && previous.state()==state)return previous.offset();
            var result=raw(() -> compute(level,pos,state,weather));
            if(cache.values.size()>=4096)cache.values.clear();
            cache.values.put(pos.immutable(),new Entry(state,result));
            return result;
        }
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
