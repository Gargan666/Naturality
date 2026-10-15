package naturality.test;

import naturality.NaturalityEffects;
import naturality.client.insanity.Insanity;
import naturality.client.insanity.InsanityState;
import naturality.client.insanity.InsanitySoundSettings;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityTypes;

public final class InsanityGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    private static Mob cow(net.minecraft.client.Minecraft client) {
        for (var entity : TestFixtures.level(client).entitiesForRendering()) if (entity.getType() == EntityTypes.COW) return (Mob)entity;
        throw new AssertionError("Missing cow fixture");
    }
    public void runTest(ClientGameTestContext context) {
        check(Insanity.distanceWeight(3) == 0 && Insanity.distanceWeight(4) == 0
            && Insanity.distanceWeight(6) == .5F && Insanity.distanceWeight(8) == 1, "Smooth 4-8 block reveal");
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set noon");
            server.runCommand("fill -8 100 -8 8 100 24 stone");
            server.runCommand("tp @a 0 102 -2 0 5");
            server.runCommand("summon cow 0 101 12 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            context.waitTicks(20);
            context.runOnClient(client -> {
                var state = client.getEntityRenderDispatcher().extractEntity(cow(client), 1);
                check(((InsanityState)state).naturality$insanity().hallucination == null, "Unaffected observer sees real mob");
            });
            server.runCommand("effect give @a naturality:insanity 60 0 true");
            context.waitTicks(10);
            context.runOnClient(client -> {
                var cow = cow(client);
                var dispatcher = client.getEntityRenderDispatcher();
                var data = ((InsanityState)dispatcher.extractEntity(cow, 1)).naturality$insanity();
                check(data.hallucination != null && data.alpha == 0 && data.distortion == 1, "Far mob has distorted replacement model");
                check(data.hallucination.entityType != cow.getType(), "Replacement differs from actual entity type");
                var next = ((InsanityState)dispatcher.extractEntity(cow, 1)).naturality$insanity();
                check(data.hallucination.entityType == next.hallucination.entityType, "Stable randomized identity across frames");
                var sound = new SimpleSoundInstance(SoundEvents.SKELETON_AMBIENT, SoundSource.NEUTRAL, .7F, .9F,
                    RandomSource.create(42), cow.getX(), cow.getY(), cow.getZ());
                var changed = Insanity.sound(sound);
                check(changed != sound && !changed.getIdentifier().equals(sound.getIdentifier()), "Mob voice is randomized");
                check(((InsanitySoundSettings)changed).naturality$volume() == ((InsanitySoundSettings)sound).naturality$volume()
                    && ((InsanitySoundSettings)changed).naturality$pitch() == ((InsanitySoundSettings)sound).naturality$pitch()
                    && changed.getX() == sound.getX(), "Sound placement and mix retained");
                client.getSoundManager().play(sound);
            });
            context.takeScreenshot("insanity-far-hallucination");
            server.runCommand("summon pig 3 101 12 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            server.runCommand("summon ender_dragon 0 112 24 {NoAI:1b,Silent:1b}");
            context.waitTicks(5);
            context.runOnClient(client -> {
                var dispatcher=client.getEntityRenderDispatcher();
                for(var entity:TestFixtures.level(client).entitiesForRendering()) if(entity.getType()==EntityTypes.PIG)
                    check(((InsanityState)dispatcher.extractEntity(entity,1)).naturality$insanity().hallucination!=null,
                        "Mob spawned after effect starts is immediately affected");
                // Exercise a live set larger than the old cache ceiling, then revisit an evicted mob.
                var sources=new java.util.ArrayList<Mob>();
                for(int i=0;i<320;i++) {
                    var mob=TestFixtures.require(EntityTypes.COW.create(TestFixtures.level(client),net.minecraft.world.entity.EntitySpawnReason.COMMAND), "mob");
                    mob.setId(-4_000_000-i);mob.setPos(0,101,12);sources.add(mob);
                    check(((InsanityState)dispatcher.extractEntity(mob,1)).naturality$insanity().hallucination!=null,
                        "Every mob beyond 256 receives a model: "+i);
                }
                var first=sources.getFirst();var expected=Insanity.replacementType(first);
                check(((InsanityState)dispatcher.extractEntity(first,1)).naturality$insanity().hallucination.entityType==expected,
                    "Evicted entries recreate the same randomized identity");
                var firstState=((InsanityState)dispatcher.extractEntity(first,1)).naturality$insanity().hallucination;
                var other=sources.get(3);other.setUUID(first.getUUID());other.tickCount=123;
                var otherState=((InsanityState)dispatcher.extractEntity(other,1)).naturality$insanity().hallucination;
                check(firstState!=otherState && firstState.ageInTicks==1 && otherState.ageInTicks==124,
                    "Shared templates preserve each mob's independently captured render state");
                var rider=sources.get(1);var mount=sources.get(2);
                check(rider.startRiding(mount),"Riding fixture");
                check(((InsanityState)dispatcher.extractEntity(rider,1)).naturality$insanity().hallucination!=null
                    && ((InsanityState)dispatcher.extractEntity(mount,1)).naturality$insanity().hallucination!=null,
                    "Riders and ridden mobs both receive hallucinations");
                rider.stopRiding();
                var difficulty=TestFixtures.level(client).getLevelData().getDifficulty();
                TestFixtures.level(client).getLevelData().setDifficulty(net.minecraft.world.Difficulty.PEACEFUL);
                try {
                    for(int i=0;i<32;i++) {
                        var mob=TestFixtures.require(EntityTypes.COW.create(TestFixtures.level(client),net.minecraft.world.entity.EntitySpawnReason.COMMAND), "mob");
                        mob.setId(-4_002_000-i);mob.setPos(0,101,12);
                        check(((InsanityState)dispatcher.extractEntity(mob,1)).naturality$insanity().hallucination!=null,
                            "Peaceful never rejects a render-only monster disguise");
                    }
                } finally {TestFixtures.level(client).getLevelData().setDifficulty(difficulty);}
                var dragon=TestFixtures.require(EntityTypes.ENDER_DRAGON.create(TestFixtures.level(client),net.minecraft.world.entity.EntitySpawnReason.COMMAND), "dragon");
                dragon.setId(-4_001_000);dragon.setPos(0,110,20);
                check(((InsanityState)dispatcher.extractEntity(dragon,1)).naturality$insanity().hallucination!=null,
                    "Non-living-render-state boss receives a hallucination");
            });
            server.runCommand("tp @e[type=cow] 0 101 4");
            context.waitTicks(10);
            context.runOnClient(client -> {
                var data = ((InsanityState)client.getEntityRenderDispatcher().extractEntity(cow(client), 1)).naturality$insanity();
                check(data.hallucination != null && data.alpha > 0 && data.alpha < 1, "Both models crossfade at middle distance");
                check(Math.abs(data.alpha + ((InsanityState)data.hallucination).naturality$insanity().alpha - 1) < .0001,
                    "Crossfade opacity is complementary");
            });
            context.takeScreenshot("insanity-middle-crossfade");
            server.runCommand("tp @e[type=cow] 0 101 0");
            context.waitTicks(10);
            context.runOnClient(client -> {
                var cow = cow(client);
                var data = ((InsanityState)client.getEntityRenderDispatcher().extractEntity(cow, 1)).naturality$insanity();
                check(data.hallucination == null && data.alpha == 1 && data.distortion == 0, "Close mob fully returns to real form");
                var sound = new SimpleSoundInstance(SoundEvents.SKELETON_AMBIENT, SoundSource.NEUTRAL, 1, 1,
                    RandomSource.create(42), cow.getX(), cow.getY(), cow.getZ());
                check(Insanity.sound(sound) == sound, "Close mob real voice returns");
            });
            server.runCommand("tp @e[type=cow] 0 101 12");
            server.runCommand("effect clear @a naturality:insanity");
            context.waitTicks(10);
            context.runOnClient(client -> {
                check(!TestFixtures.player(client).hasEffect(NaturalityEffects.INSANITY), "Effect clears on client");
                var data = ((InsanityState)client.getEntityRenderDispatcher().extractEntity(cow(client), 1)).naturality$insanity();
                check(data.hallucination == null && data.alpha == 1, "Distant hallucinations clear with effect");
            });
            server.runOnServer(s -> {
                long cows = java.util.stream.StreamSupport.stream(s.overworld().getAllEntities().spliterator(), false)
                    .filter(e -> e.getType() == EntityTypes.COW).count();
                check(cows == 1, "Client hallucinations never spawn server entities or change real mob type");
            });
        }
    }
}
