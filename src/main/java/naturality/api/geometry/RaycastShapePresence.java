package naturality.api.geometry;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import naturality.systems.weather.wind.WindPush.WindShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Conservative palette queries scoped to one synchronous ray, including nested rays. */
@org.jspecify.annotations.NullMarked
public final class RaycastShapePresence {
    public static final int FIRE=1, SNOW=2, WIND=4;
    private static final int KNOWN=8;
    private static final ThreadLocal<ScopeStack> SCOPES=ThreadLocal.withInitial(ScopeStack::new);
    private static final class ScopeStack {
        private final @org.jspecify.annotations.Nullable Scope[] reusable=new Scope[8];
        private @org.jspecify.annotations.Nullable Scope current;
        private int depth;
    }
    public static final class Scope {
        private @org.jspecify.annotations.Nullable BlockGetter view;
        private @org.jspecify.annotations.Nullable Scope previous;
        private final Long2ByteOpenHashMap sections=new Long2ByteOpenHashMap();
        private Scope() {}
    }
    private RaycastShapePresence() {}
    /** Conservative broad phase; the original shape still computes every possible hit. */
    public static boolean mayIntersect(VoxelShape shape,Vec3 from,Vec3 to,BlockPos owner) {
        if(shape.isEmpty())return false;
        // Native AABB clipping accepts tiny edge tolerances. Keep a larger halo
        // here so boundary hits, including inside starts, always reach that code.
        // VoxelShape.bounds constructs an AABB from these same six extrema.
        double minX=shape.min(Direction.Axis.X)+owner.getX()-1e-6,maxX=shape.max(Direction.Axis.X)+owner.getX()+1e-6;
        if(from.x<minX && to.x<minX || from.x>maxX && to.x>maxX)return false;
        double minY=shape.min(Direction.Axis.Y)+owner.getY()-1e-6,maxY=shape.max(Direction.Axis.Y)+owner.getY()+1e-6;
        if(from.y<minY && to.y<minY || from.y>maxY && to.y>maxY)return false;
        double minZ=shape.min(Direction.Axis.Z)+owner.getZ()-1e-6,maxZ=shape.max(Direction.Axis.Z)+owner.getZ()+1e-6;
        return !(from.z<minZ && to.z<minZ || from.z>maxZ && to.z>maxZ);
    }
    public static Scope begin(BlockGetter view) {
        var stack=SCOPES.get();
        Scope scope;
        if(stack.depth<stack.reusable.length) {
            var cached=stack.reusable[stack.depth];
            if(cached==null)stack.reusable[stack.depth]=cached=new Scope();
            scope=cached;
        } else scope=new Scope();
        scope.view=view;
        scope.previous=stack.current;
        stack.current=scope;
        stack.depth++;
        return scope;
    }
    public static void end(Scope scope) {
        var stack=SCOPES.get();
        if(stack.current!=scope)throw new IllegalStateException("Ray scopes must close in nesting order");
        stack.current=scope.previous;
        stack.depth--;
        // Worker threads outlive worlds. Retain only bounded empty scratch maps;
        // each new ray still rechecks palettes, including after block changes.
        scope.view=null;
        scope.previous=null;
        scope.sections.clear();
    }
    public static boolean mayContain(BlockGetter view,int kind,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        var scope=SCOPES.get().current;
        if(scope==null || scope.view!=view || !(view instanceof Level level))return true;
        for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)for(int y=minY>>4;y<=maxY>>4;y++) {
            long key=SectionPos.asLong(x,y,z);
            int presence=scope.sections.get(key);
            if(presence==0) {
                if(y<level.getMinSectionY() || y>level.getMaxSectionY())presence=KNOWN;
                else {
                    // Never request a chunk or inspect a server future just for this shortcut.
                    var chunk=level.getChunkSource().getChunkNow(x,z);
                    if(chunk==null)return true;
                    var section=chunk.getSection(level.getSectionIndexFromSectionY(y));
                    presence=KNOWN;
                    if(!section.hasOnlyAir()) {
                        if(section.maybeHas(s -> s.getBlock() instanceof BaseFireBlock))presence|=FIRE;
                        if(section.maybeHas(s -> s.is(Blocks.SNOW) || naturality.blocks.ash.Ash.isLayer(s)))presence|=SNOW;
                        if(section.maybeHas(WindShapes::eligible))presence|=WIND;
                    }
                }
                if(scope.sections.size()<256)scope.sections.put(key,(byte)presence);
            }
            if((presence&kind)!=0)return true;
        }
        return false;
    }
}
