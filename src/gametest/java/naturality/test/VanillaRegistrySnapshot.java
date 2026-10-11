package naturality.test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.world.level.block.Block;

/** Also launched directly, without Fabric/Knot/mixins, to obtain the vanilla wire tables. */
public final class VanillaRegistrySnapshot {
    private VanillaRegistrySnapshot() {}
    public static Map<String, String> capture() {
        var values = new TreeMap<String, String>();
        for (var registry : BuiltInRegistries.REGISTRY) record(registry, values);
        for (var state : Block.BLOCK_STATE_REGISTRY)
            if (BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft"))
                values.put("block-state/" + state, Integer.toString(Block.getId(state)));
        for (String name : new String[] {"net.minecraft.world.entity.LivingEntity", "net.minecraft.world.entity.player.Player",
                "net.minecraft.world.entity.npc.villager.Villager", "net.minecraft.world.entity.monster.zombie.Zombie",
                "net.minecraft.world.entity.monster.Ghast", "net.minecraft.world.entity.animal.happyghast.HappyGhast",
                "net.minecraft.world.entity.projectile.hurtingprojectile.Fireball"}) {
            try {
                for (Class<?> type = Class.forName(name); type != null; type = type.getSuperclass()) {
                    for (var field : type.getDeclaredFields()) if (Modifier.isStatic(field.getModifiers())
                            && field.getType() == EntityDataAccessor.class) {
                        field.setAccessible(true);
                        var accessor = (EntityDataAccessor<?>)field.get(null);
                        values.put("entity-data/" + type.getName() + "/" + field.getName(),
                            accessor.id() + ":" + EntityDataSerializers.getSerializedId(accessor.serializer()));
                    }
                }
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        }
        return values;
    }
    private static <T> void record(Registry<T> registry, Map<String, String> values) {
        for (T value : registry) {
            var id = registry.getKey(value);
            if (id != null && id.getNamespace().equals("minecraft"))
                values.put("registry/" + registry.key().identifier() + "/" + id, Integer.toString(registry.getId(value)));
        }
    }
    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        var snapshot = capture();
        Files.writeString(Path.of(args[0]), new com.google.gson.Gson().toJson(snapshot));
        System.out.println("Captured " + snapshot.size() + " vanilla registry, block-state and tracked-data entries without Fabric.");
    }
}
