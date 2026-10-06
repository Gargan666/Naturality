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

        // Sodium reuses a snapshot instance. A new build must see changed supports,
        // while repeated queries inside one build should share their exact volume.
        blocks.clear();blocks.put(owner.below(),slab);
        var before=naturality.snow.SnowGeometry.shape(snapshot,owner,3);
        naturality.snow.SnowGeometryCache.begin();
        try {
            var first=naturality.snow.SnowGeometry.shape(snapshot,owner,3);
            check(first==naturality.snow.SnowGeometry.shape(snapshot,owner,3),"Mesh reuses fitted shape");
            check(naturality.snow.SnowGeometry.collisionShape(snapshot,owner,1).isEmpty(),"Single snow layer has no collision");
            check(naturality.snow.SnowGeometry.collisionShape(snapshot,owner,3).bounds().maxY<first.bounds().maxY,
                "Collision and outline caches remain distinct");
            naturality.snow.SnowGeometryCache.begin();
            naturality.snow.SnowGeometryCache.end();
            check(first==naturality.snow.SnowGeometry.shape(snapshot,owner,3),"Nested model scope keeps outer cache");
        } finally { naturality.snow.SnowGeometryCache.end(); }
        blocks.put(owner.below(),Blocks.STONE.defaultBlockState());
        naturality.snow.SnowGeometryCache.begin();
        try {
            var after=naturality.snow.SnowGeometry.shape(snapshot,owner,3);
            check(before.min(Direction.Axis.Y)==-.5 && after.min(Direction.Axis.Y)==0,
                "Reused snapshot invalidates position cache between builds");
        } finally { naturality.snow.SnowGeometryCache.end(); }
        blocks.put(owner.below(),slab);
        check(naturality.snow.SnowGeometry.shape(snapshot,owner,3).min(Direction.Axis.Y)==-.5,
            "Live world queries never retain position cache");
        naturality.snow.SnowGeometryCache.begin();
        try {
            naturality.snow.ShapeRecursionGuard.enterFire();
            try { check(naturality.snow.SnowGeometry.shape(snapshot,owner,3).min(Direction.Axis.Y)==0,
                "Fire recursion fallback remains vanilla"); }
            finally { naturality.snow.ShapeRecursionGuard.exitFire(); }
            check(naturality.snow.SnowGeometry.shape(snapshot,owner,3).min(Direction.Axis.Y)==-.5,
                "Recursion fallback does not poison fitted cache");
        } finally { naturality.snow.SnowGeometryCache.end(); }
    }
}
