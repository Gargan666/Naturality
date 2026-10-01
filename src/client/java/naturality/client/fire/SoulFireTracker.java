package naturality.client.fire;

import java.util.WeakHashMap;
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
        if (!entity.isOnFire() || fireContact == 1) SOUL_BURNING.remove(entity);
        else if (fireContact == 2) SOUL_BURNING.put(entity, true);
        return entity.isOnFire() && SOUL_BURNING.containsKey(entity);
    }

    /** 0 = no fire, 1 = regular fire, 2 = soul fire. Regular fire wins if both touch. */
    private static int fireContact(Entity entity) {
        var box = entity.getBoundingBox();
        int minX = BlockPos.containing(box.minX + 1.0E-4, box.minY + 1.0E-4, box.minZ + 1.0E-4).getX();
        int maxX = BlockPos.containing(box.maxX - 1.0E-4, box.minY + 1.0E-4, box.maxZ - 1.0E-4).getX();
        int minZ = BlockPos.containing(box.minX + 1.0E-4, box.minY + 1.0E-4, box.minZ + 1.0E-4).getZ();
        int maxZ = BlockPos.containing(box.maxX - 1.0E-4, box.minY + 1.0E-4, box.maxZ - 1.0E-4).getZ();
        int y = BlockPos.containing(box.minX, box.minY + 1.0E-4, box.minZ).getY();
        boolean soulFire = false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            {
                var block = entity.level().getBlockState(new BlockPos(x, y, z)).getBlock();
                if (!(block instanceof BaseFireBlock)) continue;
                if (block == Blocks.SOUL_FIRE) soulFire = true;
                else return 1;
            }
        return soulFire ? 2 : 0;
    }
}
