package naturality.config;

import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import org.slf4j.LoggerFactory;

/** Shared list keeps happy-ghast rider offsets in sync with body lean. Restart to reload. */
public final class FloatingMobTiltConfig {
    public List<String> entities=List.of("minecraft:ghast","minecraft:happy_ghast","minecraft:vex","minecraft:allay","minecraft:breeze","minecraft:blaze","minecraft:parrot");
    private static FloatingMobTiltConfig instance;
    private static Set<String> ids;
    public static FloatingMobTiltConfig get() {
        if(instance!=null) return instance;
        var config=new FloatingMobTiltConfig();
        var file=FabricLoader.getInstance().getConfigDir().resolve("naturality-floating-mob-tilt.json");
        var gson=new GsonBuilder().setPrettyPrinting().create();
        try {
            if(Files.exists(file)) {
                try(var reader=Files.newBufferedReader(file)) {
                    var loaded=gson.fromJson(reader,FloatingMobTiltConfig.class);
                    if(loaded==null || loaded.entities==null || loaded.entities.stream().anyMatch(java.util.Objects::isNull))
                        throw new IllegalArgumentException("entities must be an array of entity IDs");
                    config=loaded;
                }
            } else {
                Files.createDirectories(file.getParent()); Files.writeString(file,gson.toJson(config)+"\n");
            }
        } catch(Exception error) {
            LoggerFactory.getLogger("naturality").error("Could not load floating mob tilt list; using defaults",error);
        }
        ids=Set.copyOf(config.entities); instance=config; return instance;
    }
    public static boolean enabled(LivingEntity entity) {
        get(); return ids.contains(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
    }
}
