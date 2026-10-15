package naturality.entity;

import naturality.Naturality;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public final class MaddenedZombies {
    public static final EntityType<MaddenedZombie> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
        Naturality.id("maddened_zombie"), EntityType.Builder.of(MaddenedZombie::new, MobCategory.MONSTER)
            .sized(.6F, 1.95F).clientTrackingRange(8)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Naturality.id("maddened_zombie"))));
    public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM,
        Naturality.id("maddened_zombie_spawn_egg"), new SpawnEggItem(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("maddened_zombie_spawn_egg"))).spawnEgg(TYPE)));
    public static void initialize() {
        MaddenedVariants.register(net.minecraft.world.entity.EntityTypes.ZOMBIE, TYPE);
        FabricDefaultAttributeRegistry.register(TYPE, Zombie.createAttributes().add(Attributes.MAX_HEALTH, 40));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(SPAWN_EGG));
    }
    private MaddenedZombies() {}
}
