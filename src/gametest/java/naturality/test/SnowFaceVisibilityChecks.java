package naturality.test;

import java.util.HashMap;
import naturality.client.snow.SnowFaceVisibility;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;

final class SnowFaceVisibilityChecks {
    private SnowFaceVisibilityChecks() { }
    private static void check(boolean condition,String message) {
        if (!condition) throw new AssertionError(message);
    }
    static void run() {
        var blocks=new HashMap<BlockPos,BlockState>();
        var snapshot=(BlockAndTintGetter)java.lang.reflect.Proxy.newProxyInstance(
            BlockAndTintGetter.class.getClassLoader(),new Class<?>[]{BlockAndTintGetter.class},
            (proxy,method,args)-> {
                if(method.getName().equals("getBlockState"))return blocks.getOrDefault(args[0],Blocks.AIR.defaultBlockState());
                throw new AssertionError("Unexpected snow culling snapshot query: "+method.getName());
            });
        var owner=BlockPos.ZERO;
        var slab=Blocks.STONE_SLAB.defaultBlockState();
        blocks.put(owner.below(),slab);
        var box=new AABB(0,-.5,0,1,-.375,1);
        var shape=Shapes.create(box);
        var visibility=new SnowFaceVisibility(snapshot,owner,shape);
        check(!visibility.visible(box,Direction.DOWN),"Slab hides the displaced snow underside");
        check(visibility.visible(box,Direction.UP),"Exposed top remains visible");
        check(visibility.visible(box,Direction.EAST),"Air leaves displaced side visible");
        blocks.put(owner.east().below(),Blocks.STONE.defaultBlockState());
        visibility=new SnowFaceVisibility(snapshot,owner,shape);
        check(!visibility.visible(box,Direction.EAST),"Opaque neighbor hides the entire displaced face");
        blocks.put(owner.east().below(),Blocks.GLASS.defaultBlockState());
        check(new SnowFaceVisibility(snapshot,owner,shape).visible(box,Direction.EAST),
            "Transparent neighbors cannot remove snow faces");
        blocks.put(owner.east().below(),slab);
        blocks.put(owner.east(),Blocks.SNOW.defaultBlockState());
        check(!new SnowFaceVisibility(snapshot,owner,shape).visible(box,Direction.EAST),
            "Equal adjacent fitted snow hides their shared edge");
        var taller=new AABB(0,-.5,0,1,-.25,1);
        check(new SnowFaceVisibility(snapshot,owner,Shapes.create(taller)).visible(taller,Direction.EAST),
            "Partially covered faces remain visible");
        blocks.put(owner.east().below(),Blocks.OAK_LEAVES.defaultBlockState());
        var cap=new AABB(0,0,0,1,.125,1);
        check(new SnowFaceVisibility(snapshot,owner,Shapes.create(cap)).visible(cap,Direction.EAST),
            "Moving neighboring leaf snow is never a static occluder");

        var left=new AABB(0,0,0,.5,.125,1);
        var right=new AABB(.5,0,0,1,.25,1);
        var joined=Shapes.or(Shapes.create(left),Shapes.create(right));
        visibility=new SnowFaceVisibility(snapshot,owner,joined);
        check(!visibility.visible(left,Direction.EAST),"Internal box face is removed");
        check(visibility.visible(right,Direction.WEST),"Exposed step above an internal face remains");
    }
}
