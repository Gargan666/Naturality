package naturality.bootstrap;

import naturality.Naturality;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.core.particles.ParticleType;

/** Shared registration groups retain independent class initialization. */
@org.jspecify.annotations.NullMarked
public final class ContentRegistration {
    private ContentRegistration() {}

    public static final class NaturalityBlocks {
        public static final Block SMOOTH_ENDSTONE = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("smooth_endstone")),
            BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE));
        public static final BlockItem SMOOTH_ENDSTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("smooth_endstone")),
            new BlockItem(SMOOTH_ENDSTONE, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("smooth_endstone")))
                .useBlockDescriptionPrefix()));

        private NaturalityBlocks() {}

        public static final Block LIRESTONE = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("lirestone")),
            BlockBehaviour.Properties.ofFullCopy(Blocks.END_STONE).strength(2.5F, 9.0F).sound(NaturalitySounds.LIRESTONE));
        public static final BlockItem LIRESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")),
            new BlockItem(LIRESTONE, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lirestone")))
                .useBlockDescriptionPrefix()));

        public static final Block SMOOTH_LIRESTONE = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("smooth_lirestone")),
            BlockBehaviour.Properties.ofFullCopy(LIRESTONE));
        public static final BlockItem SMOOTH_LIRESTONE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("smooth_lirestone")),
            new BlockItem(SMOOTH_LIRESTONE, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("smooth_lirestone")))
                .useBlockDescriptionPrefix()));

        public static final Block CHORUS_SPROUT = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("chorus_sprout")),
            naturality.blocks.chorus.ChorusFlowers.ChorusSproutBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.SHORT_GRASS).sound(net.minecraft.world.level.block.SoundType.ROOTS));
        public static final BlockItem CHORUS_SPROUT_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("chorus_sprout")),
            new BlockItem(CHORUS_SPROUT, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("chorus_sprout")))
                .useBlockDescriptionPrefix()));

        public static final Block LYREN_SPROUT = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_sprout")),
            naturality.blocks.chorus.ChorusFlowers.ChorusSproutBlock::new, BlockBehaviour.Properties.ofFullCopy(CHORUS_SPROUT));
        public static final BlockItem LYREN_SPROUT_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("lyren_sprout")),
            new BlockItem(LYREN_SPROUT, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_sprout"))).useBlockDescriptionPrefix()));

        public static final Block LYREN_GROWTH = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_growth")),
            naturality.blocks.chorus.ChorusFlowers.ChorusSproutBlock::new, BlockBehaviour.Properties.ofFullCopy(CHORUS_SPROUT));
        public static final BlockItem LYREN_GROWTH_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("lyren_growth")),
            new BlockItem(LYREN_GROWTH, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_growth"))).useBlockDescriptionPrefix()));

        public static final Block ECHO_CORAL = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("echo_coral")),
            BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_MUSHROOM_BLOCK).sound(NaturalitySounds.ECHO_CORAL));
        public static final BlockItem ECHO_CORAL_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("echo_coral")),
            new BlockItem(ECHO_CORAL, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("echo_coral")))
                .useBlockDescriptionPrefix()));

        public static final net.minecraft.world.level.block.SoundType LYREN_HEATH_SOUNDS =
            new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
                net.minecraft.world.level.block.SoundType.STONE.getBreakSound(),
                Blocks.WARPED_NYLIUM.defaultBlockState().getSoundType().getStepSound(),
                net.minecraft.world.level.block.SoundType.STONE.getPlaceSound(),
                net.minecraft.world.level.block.SoundType.STONE.getHitSound(),
                net.minecraft.world.level.block.SoundType.STONE.getFallSound());

        public static final Block LYREN_HEATH = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("lyren_heath")),
            naturality.blocks.lyrenheath.LyrenHeathBlock::new,
            BlockBehaviour.Properties.ofFullCopy(SMOOTH_ENDSTONE).strength(2.1F, 9.0F)
                .sound(LYREN_HEATH_SOUNDS).randomTicks());
        public static final BlockItem LYREN_HEATH_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("lyren_heath")),
            new BlockItem(LYREN_HEATH, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("lyren_heath")))
                .useBlockDescriptionPrefix()));

        public static final Block MOSS = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("moss")),
            net.minecraft.world.level.block.GlowLichenBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLOW_LICHEN).lightLevel(_ -> 0)
                .sound(Blocks.MOSS_BLOCK.defaultBlockState().getSoundType()));
        public static final BlockItem MOSS_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("moss")),
            new BlockItem(MOSS, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("moss")))
                .useBlockDescriptionPrefix()));

        public static final Block CLOVERS = Blocks.register(
            ResourceKey.create(Registries.BLOCK, Naturality.id("clovers")),
            properties -> new net.minecraft.world.level.block.FlowerBedBlock(properties, 3),
            BlockBehaviour.Properties.ofFullCopy(Blocks.WILDFLOWERS));
        public static final BlockItem CLOVERS_ITEM = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("clovers")),
            new BlockItem(CLOVERS, new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, Naturality.id("clovers")))
                .useBlockDescriptionPrefix()));

        public static void initialize() {
            CLOVERS_ITEM.registerBlocks(Item.BY_BLOCK, CLOVERS_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(output -> output.insertAfter(Blocks.WILDFLOWERS, CLOVERS));
            MOSS_ITEM.registerBlocks(Item.BY_BLOCK, MOSS_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(output -> output.insertAfter(Blocks.GLOW_LICHEN, MOSS));
            LYREN_HEATH_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_HEATH_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(output -> output.insertAfter(Blocks.END_STONE, LYREN_HEATH));
            ECHO_CORAL_ITEM.registerBlocks(Item.BY_BLOCK, ECHO_CORAL_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(output -> output.insertAfter(Blocks.BROWN_MUSHROOM_BLOCK, ECHO_CORAL));
            CHORUS_SPROUT_ITEM.registerBlocks(Item.BY_BLOCK, CHORUS_SPROUT_ITEM);
            LYREN_SPROUT_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_SPROUT_ITEM);
            LYREN_GROWTH_ITEM.registerBlocks(Item.BY_BLOCK, LYREN_GROWTH_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.NATURAL_BLOCKS)
                .register(output -> output.insertAfter(Blocks.SHORT_GRASS, CHORUS_SPROUT, LYREN_SPROUT, LYREN_GROWTH));
            SMOOTH_ENDSTONE_ITEM.registerBlocks(Item.BY_BLOCK, SMOOTH_ENDSTONE_ITEM);
            LIRESTONE_ITEM.registerBlocks(Item.BY_BLOCK, LIRESTONE_ITEM);
            SMOOTH_LIRESTONE_ITEM.registerBlocks(Item.BY_BLOCK, SMOOTH_LIRESTONE_ITEM);
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.BUILDING_BLOCKS)
                .register(output -> output.insertAfter(Blocks.END_STONE, SMOOTH_ENDSTONE, LIRESTONE, SMOOTH_LIRESTONE));
        }
    }

    public static final class NaturalityItems {
        public static final Item FOUR_LEAF_CLOVER = Registry.register(BuiltInRegistries.ITEM,
            ResourceKey.create(Registries.ITEM, Naturality.id("four_leaf_clover")),
            new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, Naturality.id("four_leaf_clover")))));

        private NaturalityItems() {}

        public static void initialize() {
            CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(output -> output.insertAfter(Items.RABBIT_FOOT, FOUR_LEAF_CLOVER));
        }
    }

    public static final class NaturalityEffects {
        public static final Holder<MobEffect> CONFUSION = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Naturality.id("confusion"), new ConfusionEffect());
        public static final Holder<MobEffect> DISTORTION = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Naturality.id("distortion"), new DistortionEffect());
        public static final Holder<MobEffect> INSANITY = Registry.registerForHolder(
            BuiltInRegistries.MOB_EFFECT, Naturality.id("insanity"), new InsanityEffect());
        private NaturalityEffects() {}
        public static void initialize() {}
        private static final class ConfusionEffect extends MobEffect {
            private ConfusionEffect() { super(MobEffectCategory.HARMFUL, 0xD67ACB); }
        }
        private static final class DistortionEffect extends MobEffect {
            private DistortionEffect() { super(MobEffectCategory.HARMFUL, 0x9565CD); }
        }
        private static final class InsanityEffect extends MobEffect {
            private InsanityEffect() { super(MobEffectCategory.HARMFUL, 0xAE253B); }
        }
    }

    public static final class NaturalityParticles {
        public static final SimpleParticleType END_CRYSTAL_TEXT=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("end_crystal_text"),FabricParticleTypes.simple());
        public static final SimpleParticleType END_EXPLOSION=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("end_explosion"),FabricParticleTypes.simple(true));
        public static final SimpleParticleType POTION_CLOUD=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("potion_cloud"),FabricParticleTypes.simple());
        public static final SimpleParticleType RAIN_CLUSTER=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("rain_cluster"),FabricParticleTypes.simple());
        public static final SimpleParticleType SNOW_CLUSTER=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("snow_cluster"),FabricParticleTypes.simple());
        public static final SimpleParticleType WATERFALL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("waterfall"),FabricParticleTypes.simple());
        public static final SimpleParticleType WATERFALL_BIG=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("waterfall_big"),FabricParticleTypes.simple());
        public static final SimpleParticleType LAVA_SPLASH=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("lava_splash"),FabricParticleTypes.simple());
        public static final SimpleParticleType LAVAFALL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("lavafall"),FabricParticleTypes.simple());
        public static final SimpleParticleType LAVAFALL_BIG=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("lavafall_big"),FabricParticleTypes.simple());
        public static final SimpleParticleType STAR_TRAIL=Registry.register(BuiltInRegistries.PARTICLE_TYPE,
            Naturality.id("star_trail"),FabricParticleTypes.simple());
        public static void initialize() {}
        private NaturalityParticles() {}
    }

    public static final class NaturalitySounds {
        public static final SoundEvent CAVE_AMBIENCE = register("cave_ambience");
        public static final java.util.List<SoundEvent> END_AMBIENCE_CLIPS = java.util.stream.IntStream.rangeClosed(1, 5)
            .mapToObj(index -> {
                var id = Naturality.id("end_ambience_" + index);
                return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
            }).toList();
        public static final SoundEvent END_AMBIENCE = Registry.register(
            BuiltInRegistries.SOUND_EVENT, Naturality.id("end_ambience"),
            SoundEvent.createVariableRangeEvent(Naturality.id("end_ambience")));
        public static final SoundEvent WIND_WEAK = Registry.register(BuiltInRegistries.SOUND_EVENT,
            Naturality.id("wind_weak"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_weak")));
        public static final SoundEvent WIND_STRONG = Registry.register(BuiltInRegistries.SOUND_EVENT,
            Naturality.id("wind_strong"), SoundEvent.createVariableRangeEvent(Naturality.id("wind_strong")));
        public static final SoundEvent PORTAL_OPEN = Registry.register(
            BuiltInRegistries.SOUND_EVENT, Naturality.id("portal_open"),
            SoundEvent.createVariableRangeEvent(Naturality.id("portal_open")));

        public static final SoundEvent WATER_SPLASH_LOUD = Registry.register(
            BuiltInRegistries.SOUND_EVENT, Naturality.id("watersplash_loud"),
            SoundEvent.createVariableRangeEvent(Naturality.id("watersplash_loud")));

        public static final SoundEvent LIRESTONE_BREAK = register("block.lirestone.break");
        public static final SoundEvent LIRESTONE_STEP = register("block.lirestone.step");
        public static final SoundEvent LIRESTONE_PLACE = register("block.lirestone.place");
        public static final net.minecraft.world.level.block.SoundType LIRESTONE =
            new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
                LIRESTONE_BREAK, LIRESTONE_STEP, LIRESTONE_PLACE, LIRESTONE_BREAK, LIRESTONE_STEP);

        public static final SoundEvent ECHO_CORAL_BREAK = register("block.echo_coral.break");
        public static final SoundEvent ECHO_CORAL_STEP = register("block.echo_coral.step");
        public static final SoundEvent ECHO_CORAL_PLACE = register("block.echo_coral.place");
        public static final net.minecraft.world.level.block.SoundType ECHO_CORAL =
            new net.minecraft.world.level.block.SoundType(1.0F, 1.0F,
                ECHO_CORAL_BREAK, ECHO_CORAL_STEP, ECHO_CORAL_PLACE, ECHO_CORAL_BREAK, ECHO_CORAL_STEP);

        private static SoundEvent register(String name) {
            var id = Naturality.id(name);
            return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
        }

        private NaturalitySounds() {}

        public static void initialize() {
            // Load and register the sound before the server can open a portal.
        }
    }

    public static final class NaturalityTags {
        public static final TagKey<net.minecraft.world.entity.EntityType<?>> NO_END_GRAVITY =
            TagKey.create(Registries.ENTITY_TYPE, Naturality.id("no_end_gravity"));
        public static final TagKey<ParticleType<?>> NO_WIND_EFFECT =
            TagKey.create(Registries.PARTICLE_TYPE, Naturality.id("no_wind_effect"));
        private NaturalityTags() {}
    }
}
