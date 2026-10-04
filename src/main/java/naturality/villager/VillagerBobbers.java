package naturality.villager;

import naturality.Naturality;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class VillagerBobbers {
    public static final EntityType<VillagerBobber> TYPE;
    public static final EntityType<PlayerFishingReturn> PLAYER_RETURN;
    static {
        var id = Naturality.id("villager_bobber");
        TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, id,
            EntityType.Builder.<VillagerBobber>of(VillagerBobber::new, MobCategory.MISC)
                .sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(1).noSave()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));
        var playerId = Naturality.id("player_fishing_return");
        PLAYER_RETURN = Registry.register(BuiltInRegistries.ENTITY_TYPE, playerId,
            EntityType.Builder.<PlayerFishingReturn>of(PlayerFishingReturn::new, MobCategory.MISC)
                .sized(0.25F, 0.25F).clientTrackingRange(10).updateInterval(1).noSave()
                .build(ResourceKey.create(Registries.ENTITY_TYPE, playerId)));
    }
    private VillagerBobbers() { }
    public static void initialize() { }
}
