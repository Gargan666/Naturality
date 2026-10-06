package naturality.client.snow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** Immutable atlas-space buckets, replaced on resource reload. */
final class SnowSpriteLookup {
    private static final int SIZE=64;
    private final List<List<TextureAtlasSprite>> cells=new ArrayList<>(SIZE*SIZE);
    SnowSpriteLookup(Map<Identifier,TextureAtlasSprite> sprites) {
        for(int i=0;i<SIZE*SIZE;i++)cells.add(new ArrayList<>());
        for(var sprite:sprites.values()) {
            int x0=cell(sprite.getU0()),z0=cell(sprite.getV0());
            int x1=cell(Math.nextDown(sprite.getU1())),z1=cell(Math.nextDown(sprite.getV1()));
            for(int z=z0;z<=z1;z++)for(int x=x0;x<=x1;x++)cells.get(z*SIZE+x).add(sprite);
        }
    }
    private static int cell(float coordinate) { return Math.clamp((int)(coordinate*SIZE),0,SIZE-1); }
    @Nullable TextureAtlasSprite find(float u,float v) {
        for(var sprite:cells.get(cell(v)*SIZE+cell(u)))
            if(u>=sprite.getU0() && u<sprite.getU1() && v>=sprite.getV0() && v<sprite.getV1())return sprite;
        return null;
    }
}
