package naturality.client.fire;

import java.util.WeakHashMap;
import naturality.config.GameplaySettings;
import naturality.fire.FireGeometry;
import naturality.util.LoadedChunks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;

/** Keeps the fire appearance tied to the fire that ignited an entity until it is extinguished. */
public final class SoulFireTracker {
    private static final WeakHashMap<Entity, Boolean> SOUL_BURNING = new WeakHashMap<>();

    private SoulFireTracker() { }

    public static boolean isSoulBurning(Entity entity) {
        int fireContact = fireContact(entity);
        if (entity.isInLava() || fireContact == 1) SOUL_BURNING.remove(entity);
        else if (fireContact == 2) SOUL_BURNING.put(entity, true);
        else if (!entity.isOnFire()) SOUL_BURNING.remove(entity);
        return SOUL_BURNING.containsKey(entity);
    }

    /** 0 = no fire, 1 = regular fire, 2 = soul fire. Regular fire wins if both touch. */
    private static int fireContact(Entity entity) {
        var box = entity.getBoundingBox().inflate(1.0E-4);
        // Fitted flames can extend into the cell below or beside their owner.
        var search = box.inflate(1);
        boolean wrapping = GameplaySettings.fireWrapping(entity.level());
        boolean soulFire = false;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(search.minX, search.minY, search.minZ),
                BlockPos.containing(search.maxX, search.maxY, search.maxZ))) {
            if (!LoadedChunks.has(entity.level(), pos)) continue;
            var state = entity.level().getBlockState(pos);
            if (!(state.getBlock() instanceof BaseFireBlock)) continue;
            var shape = wrapping ? FireGeometry.shape(entity.level(), pos, state)
                : state.getShape(entity.level(), pos);
            boolean touching = false;
            for (var contact : shape.toAabbs()) {
                if (contact.move(pos.getX(), pos.getY(), pos.getZ()).intersects(box)) {
                    touching = true;
                    break;
                }
            }
            if (!touching) continue;
            if (state.is(Blocks.SOUL_FIRE)) soulFire = true;
            else return 1;
        }
        return soulFire ? 2 : 0;
    }
}
