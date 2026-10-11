package naturality.client.config;

import naturality.config.Feature;
import naturality.client.config.NaturalityCategoryScreen.Category;

/** One canonical topic for every startup switch, also used by global search. */
public final class ConfigLayout {
    private ConfigLayout() {}

    public static Category category(Feature feature) {
        return switch (feature) {
            case CONTENT -> Category.CONTENT;
            case END_TERRAIN, END_MATERIALS, LIRESTONE_CRAGS, END_WILDS, LIRESTONE_BOULDERS,
                WILDS_BOULDERS, LYREN_PLANTS -> Category.END_WORLDGEN;
            case CHORUS_FLOWERS, CHORUS_CROWNS, CHORUS_SPROUTS -> Category.CHORUS;
            case DISTORTION, CONFUSION, STARFALL -> Category.CONTENT_EFFECTS;
            case VILLAGER_WORK, FARMER_WORK, FISHERMAN_WORK, BUTCHER_WORK, ANIMAL_CARE,
                VILLAGER_BREAD, VILLAGER_GATES, VILLAGER_SHELTER, REPUTATION -> Category.VILLAGERS;
            case CROWD_PANIC, FISHING_RETURN, FIREBALL_FIRE_SUPPRESSION, GHAST_PHYSICS,
                ARROW_IMPACT_EVENTS, FIREBALL_IMPACT_EVENTS -> Category.MOB_GAMEPLAY;
            case SAND_PLACEMENT_EVENTS, SNOW_WORLDGEN -> Category.BLOCK_RULES;
            case WEATHER_CYCLES, END_WEATHER, UNDERGROUND_LIGHTNING, SKY_EVENT_CYCLES -> Category.WEATHER_RULES;
            case WIND_PHYSICS, ELYTRA_MOMENTUM -> Category.TRAVEL_RULES;
            case VISUAL_OVERHAUL, RESOURCE_OVERRIDES -> Category.VISUALS;
            case END_SKY, SKY_EVENTS, METEORS, PROXIMITY, RAINBOWS, AURORAS, END_FLASHES -> Category.SKY;
            case RISE_PARTICLES, LIGHTNING -> Category.WEATHER;
            case END_AMBIENCE, WEATHER_AUDIO, UNDERWATER_AUDIO -> Category.AUDIO;
            case PIXEL_BREAKING, SAND_PARTICLES, DEATH_SMOKE, EXPLOSION_PARTICLES -> Category.PARTICLES;
            case PIXEL_SHADOWS -> Category.LIGHTING;
            case PHYSICS_ROPES, FISHING_MODELS, MOB_ARROWS, FIREBALL_MODELS, GHAST_SQUASH,
                MOB_DEATH_PIXELS, ELYTRA_CAPE -> Category.ENTITIES;
            case ENTITY_FIRE -> Category.FIRE;
            case FLUID_TAILS -> Category.LIQUIDS;
        };
    }
}
