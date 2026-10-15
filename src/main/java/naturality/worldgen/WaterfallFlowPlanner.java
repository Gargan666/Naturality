package naturality.worldgen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/** Bounded dry-run of water's downward priority, slope search, attenuation and source conversion. */
final class WaterfallFlowPlanner {
    private final WorldGenLevel level;
    private final BlockPos center;
    private final Map<BlockPos,BlockState> edits;
    private final ArrayDeque<BlockPos> queue=new ArrayDeque<>();
    private final Set<BlockPos> queued=new HashSet<>();
    private boolean invalid;
    private int steps;
    private WaterfallFlowPlanner(WorldGenLevel level,BlockPos center,Map<BlockPos,BlockState> edits) {
        this.level=level;this.center=center;this.edits=edits;
    }
    static boolean fill(WorldGenLevel level,BlockPos center,Map<BlockPos,BlockState> edits) {
        var planner=new WaterfallFlowPlanner(level,center,edits);
        for(var entry:edits.entrySet())if(entry.getValue().is(Blocks.WATER))planner.enqueue(entry.getKey());
        while(!planner.queue.isEmpty() && !planner.invalid) {
            if(++planner.steps>60000 || edits.size()>12000)return false;
            var pos=planner.queue.removeFirst();planner.queued.remove(pos);planner.spread(pos);
        }
        return !planner.invalid;
    }
    private void enqueue(BlockPos pos) {if(queued.add(pos))queue.addLast(pos);}
    private BlockState state(BlockPos pos) {return edits.getOrDefault(pos,level.getBlockState(pos));}
    private boolean within(BlockPos pos) {
        return Math.abs(pos.getX()-center.getX())<=23 && Math.abs(pos.getZ()-center.getZ())<=23
            && pos.getY()>=center.getY()-112 && pos.getY()<=center.getY()+8
            && MountainWaterfallFeature.writable(level,pos);
    }
    private boolean open(BlockPos pos) {
        if(!MountainWaterfallFeature.writable(level,pos)) {invalid=true;return false;}
        var state=state(pos);
        if(state.hasBlockEntity() || state.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
            invalid=true;return false;
        }
        return state.isAir() || state.is(Blocks.WATER) || state.is(Blocks.SNOW)
            || (naturality.snow.SnowGeometry.isFoliage(state) && state.getFluidState().isEmpty());
    }
    private boolean source(BlockPos pos) {return state(pos).getFluidState().is(FluidTags.WATER) && state(pos).getFluidState().isSource();}
    private int sourceCount(BlockPos pos) {
        int count=0;for(var direction:Direction.Plane.HORIZONTAL)if(source(pos.relative(direction)))count++;return count;
    }
    private boolean hole(BlockPos pos) {return open(pos.below());}
    @SuppressWarnings("deprecation") // Match FlowingFluid's native source-conversion support test.
    private void put(BlockPos pos,int amount,boolean falling) {
        if(!open(pos) || source(pos))return;
        if(!within(pos)) {invalid=true;return;}
        var previous=state(pos).getFluidState();
        boolean convert=sourceCount(pos)>=2 && (state(pos.below()).isSolid() || source(pos.below()))
            && level.getLevel().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.WATER_SOURCE_CONVERSION);
        var next=convert?Fluids.WATER.getSource(false):Fluids.FLOWING_WATER.getFlowing(amount,falling);
        if(previous.is(FluidTags.WATER) && !convert && previous.getAmount()>amount)return;
        var block=next.createLegacyBlock();
        if(block.equals(state(pos)))return;
        edits.put(pos.immutable(),block);enqueue(pos);
        for(var direction:Direction.Plane.HORIZONTAL)if(state(pos.relative(direction)).is(Blocks.WATER))enqueue(pos.relative(direction));
    }
    private void spread(BlockPos pos) {
        var fluid=state(pos).getFluidState();
        if(!fluid.is(FluidTags.WATER))return;
        boolean down=hole(pos);
        if(down && !source(pos.below())) {
            put(pos.below(),8,true);
            if(sourceCount(pos)<3)return;
        } else if(down && !fluid.isSource())return;
        int amount=fluid.getAmount()-1;
        if(fluid.getValue(net.minecraft.world.level.material.FlowingFluid.FALLING))amount=7;
        if(amount<=0)return;
        int best=1000;
        var routes=new LinkedHashMap<BlockPos,Integer>();
        for(var direction:Direction.Plane.HORIZONTAL) {
            var target=pos.relative(direction);
            if(!open(target) || source(target))continue;
            int distance=hole(target)?0:slope(target,1,direction.getOpposite());
            routes.put(target,distance);best=Math.min(best,distance);
        }
        for(var route:routes.entrySet())if(route.getValue()==best) {
            var target=route.getKey();
            if(state(target.above()).getFluidState().is(FluidTags.WATER))put(target,8,true);
            else put(target,amount,false);
        }
    }
    private int slope(BlockPos pos,int depth,Direction from) {
        int best=1000;
        for(var direction:Direction.Plane.HORIZONTAL) {
            if(direction==from)continue;
            var next=pos.relative(direction);
            if(!open(next) || source(next))continue;
            if(hole(next))return depth;
            if(depth<4)best=Math.min(best,slope(next,depth+1,direction.getOpposite()));
        }
        return best;
    }
}
