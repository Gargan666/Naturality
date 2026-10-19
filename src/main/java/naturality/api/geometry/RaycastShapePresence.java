package naturality.api.geometry;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import naturality.systems.weather.wind.WindPush.WindShapes;

/** Conservative palette queries scoped to one synchronous ray, including nested rays. */
@org.jspecify.annotations.NullMarked
public final class RaycastShapePresence {
    public static final int FIRE=1, SNOW=2, WIND=4;
    private static final int KNOWN=8;
    private static final ThreadLocal<Scope> CURRENT=new ThreadLocal<>();
    public static final class Scope {
        private final BlockGetter view;
        private final @org.jspecify.annotations.Nullable Scope previous;
        private final Long2ByteOpenHashMap sections=new Long2ByteOpenHashMap();
        private Scope(BlockGetter view,@org.jspecify.annotations.Nullable Scope previous) { this.view=view;this.previous=previous; }
    }
    private RaycastShapePresence() {}
    public static Scope begin(BlockGetter view) {
        var scope=new Scope(view,CURRENT.get());CURRENT.set(scope);return scope;
    }
    public static void end(Scope scope) {
        if(scope.previous==null)CURRENT.remove();else CURRENT.set(scope.previous);
    }
    public static boolean mayContain(BlockGetter view,int kind,int minX,int minY,int minZ,int maxX,int maxY,int maxZ) {
        var scope=CURRENT.get();
        if(scope==null || scope.view!=view || !(view instanceof Level level))return true;
        for(int x=minX>>4;x<=maxX>>4;x++)for(int z=minZ>>4;z<=maxZ>>4;z++)for(int y=minY>>4;y<=maxY>>4;y++) {
            long key=SectionPos.asLong(x,y,z);
            int presence=scope.sections.get(key);
            if(presence==0) {
                if(y<level.getMinSectionY() || y>=level.getMaxSectionY())presence=KNOWN;
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
