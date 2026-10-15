package naturality.entities.maddened;

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
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import naturality.entities.maddened.MaddenedVariants.MaddenedMob;
import naturality.entities.maddened.mixins.MaddenedMixins.CreeperFuseAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;

public final class MaddenedCreepers {
    public static final EntityType<MaddenedCreeper> TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE,
        Naturality.id("maddened_creeper"), EntityType.Builder.of(MaddenedCreeper::new, MobCategory.MONSTER)
            .sized(.6F, 1.7F).clientTrackingRange(8)
            .build(ResourceKey.create(Registries.ENTITY_TYPE, Naturality.id("maddened_creeper"))));
    public static final Item SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM,
        Naturality.id("maddened_creeper_spawn_egg"), new SpawnEggItem(new Item.Properties()
            .setId(ResourceKey.create(Registries.ITEM, Naturality.id("maddened_creeper_spawn_egg"))).spawnEgg(TYPE)));

    public static void initialize() {
        MaddenedVariants.register(net.minecraft.world.entity.EntityTypes.CREEPER, TYPE);
        FabricDefaultAttributeRegistry.register(TYPE, Creeper.createAttributes().add(Attributes.MOVEMENT_SPEED, .35));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(entries -> entries.accept(SPAWN_EGG));
    }
    private MaddenedCreepers() {}

    /** Vanilla creeper goals, cats, targeting, ignition and explosions with a shorter fuse. */
    public static final class MaddenedCreeper extends Creeper implements MaddenedMob {
        public static final int FUSE_TICKS = 16;
        private int leapCooldown;

        public MaddenedCreeper(EntityType<? extends Creeper> type, Level level) {
            super(type, level);
            ((CreeperFuseAccess)(Object)this).naturality$setFuse(FUSE_TICKS);
        }

        @Override protected void readAdditionalSaveData(ValueInput input) {
            super.readAdditionalSaveData(input);
            ((CreeperFuseAccess)(Object)this).naturality$setFuse(Math.clamp(input.getShortOr("Fuse", (short)FUSE_TICKS), 3, FUSE_TICKS));
        }

        @Override public void tick() {
            if (!level().isClientSide()) {
                if (leapCooldown > 0) leapCooldown--;
                var target = getTarget();
                if (!isNoAi() && isAlive() && onGround() && leapCooldown == 0 && getSwellDir() > 0
                        && target != null && target.isAlive() && distanceToSqr(target) < 49
                        && getSensing().hasLineOfSight(target)) {
                    var direction = target.position().subtract(position()).multiply(1, 0, 1);
                    if (direction.lengthSqr() > .01) {
                        var horizontal = direction.normalize().scale(.55);
                        setDeltaMovement(horizontal.x, .42, horizontal.z);
                        needsSync = true;
                        leapCooldown = 30;
                    }
                }
            }
            super.tick();
        }
    }
}
