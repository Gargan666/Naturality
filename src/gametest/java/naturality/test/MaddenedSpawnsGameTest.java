package naturality.test;

import naturality.entity.*;
import naturality.sky.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.NaturalSpawner;

public final class MaddenedSpawnsGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    private static Mob natural(ServerLevel level, EntityType<?> type) {
        try {
            var method = NaturalSpawner.class.getDeclaredMethod("getMobForSpawn", ServerLevel.class, EntityType.class);
            method.setAccessible(true);
            return (Mob)method.invoke(null, level, type);
        } catch (ReflectiveOperationException e) { throw new AssertionError("Natural spawn factory", e); }
    }
    public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 102 5 180 10");
            server.runCommand("fill -8 100 -8 8 100 8 stone");
            server.runOnServer(s -> {
                var level = s.overworld();
                var settings = SkyEventWorldData.get(s).settings(level, SkyEventType.BLOOD_MOON);
                settings.override = true; settings.strength = 0;
                check(natural(level, EntityTypes.CREEPER).getType() == EntityTypes.CREEPER, "Ordinary night creeper");
                check(natural(level, EntityTypes.ZOMBIE).getType() == EntityTypes.ZOMBIE, "Ordinary night zombie");
                settings.strength = 1;
                checkSpawnMultiplier(level);
                var creeper = natural(level, EntityTypes.CREEPER);
                var zombie = natural(level, EntityTypes.ZOMBIE);
                check(creeper instanceof MaddenedCreeper && zombie instanceof MaddenedZombie, "Actual natural factory substitutes both counterparts");
                check(zombie.getMaxHealth() == 40, "Variant starts with its own attributes");
                check(natural(level, EntityTypes.SKELETON).getType() == EntityTypes.SKELETON, "Enemy without counterpart unchanged");
                check(MaddenedVariants.forSpawn(level, EntityTypes.COW) == EntityTypes.COW, "Passive mobs unchanged");
                check(MaddenedVariants.forSpawn(level, MaddenedCreepers.TYPE) == MaddenedCreepers.TYPE, "No recursive replacement");
                check(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND).getType() == EntityTypes.ZOMBIE,
                    "Explicit summons retain requested type");
                check(MaddenedVariants.forSpawn(s.getLevel(net.minecraft.world.level.Level.NETHER), EntityTypes.ZOMBIE) == EntityTypes.ZOMBIE,
                    "Dimension without Blood Moon unchanged");
                creeper.setPos(-2, 101, 0); creeper.setNoAi(true); level.addFreshEntity(creeper);
                zombie.setPos(2, 101, 0); zombie.setNoAi(true); level.addFreshEntity(zombie);
                settings.strength = 0;
                check(natural(level, EntityTypes.ZOMBIE).getType() == EntityTypes.ZOMBIE, "New spawns return to normal after Blood Moon");
                check(zombie.getType() == MaddenedZombies.TYPE && creeper.getType() == MaddenedCreepers.TYPE,
                    "Existing maddened mobs remain after event ends");
            });
            context.waitTicks(10);
            context.takeScreenshot("blood-moon-maddened-spawn-counterparts");
        }
    }
    @SuppressWarnings("unchecked")
    private static void checkSpawnMultiplier(ServerLevel level) {
        try {
            var local=new net.minecraft.world.level.LocalMobCapCalculator(null);
            var chunk=(net.minecraft.world.level.chunk.LevelChunk)level.getChunk(new net.minecraft.core.BlockPos(0,101,0));
            var playersField=local.getClass().getDeclaredField("playersNearChunk");playersField.setAccessible(true);
            ((it.unimi.dsi.fastutil.longs.Long2ObjectMap<java.util.List<net.minecraft.server.level.ServerPlayer>>)playersField.get(local))
                .put(chunk.getPos().pack(),java.util.List.of(level.players().getFirst()));
            var countClass=Class.forName("net.minecraft.world.level.LocalMobCapCalculator$MobCounts");
            var constructor=countClass.getDeclaredConstructor();constructor.setAccessible(true);
            var counts=constructor.newInstance();var add=countClass.getDeclaredMethod("add",net.minecraft.world.entity.MobCategory.class);add.setAccessible(true);
            var category=net.minecraft.world.entity.MobCategory.MONSTER;
            for(int i=0;i<category.getMaxInstancesPerChunk();i++) add.invoke(counts,category);
            var mapField=local.getClass().getDeclaredField("playerMobCounts");mapField.setAccessible(true);
            var map=(java.util.Map<Object,Object>)mapField.get(local);map.put(level.players().getFirst(),counts);
            var state=NaturalSpawner.createState(289,level,(key,consumer)->consumer.accept(chunk),local);
            var globalField=state.getClass().getDeclaredField("mobCategoryCounts");globalField.setAccessible(true);
            ((it.unimi.dsi.fastutil.objects.Object2IntMap<net.minecraft.world.entity.MobCategory>)globalField.get(state)).put(category,category.getMaxInstancesPerChunk());
            var global=state.getClass().getDeclaredMethod("canSpawnForCategoryGlobal",net.minecraft.world.entity.MobCategory.class);global.setAccessible(true);
            check((Boolean)global.invoke(state,category) && local.canSpawn(category,chunk.getPos()),"Both population caps double during Blood Moon");
            BloodMoonSpawnProbe.enabled=true;BloodMoonSpawnProbe.calls=0;
            NaturalSpawner.spawnForChunk(level,chunk,state,java.util.List.of(category));
            check(BloodMoonSpawnProbe.calls==2,"Two independent natural spawn passes during Blood Moon");
            check(BloodMoonSpawning.LOCAL_MULTIPLIER.get()==1,"Local cap context restored after lookup");
            var settings=SkyEventWorldData.get(level.getServer()).settings(level,SkyEventType.BLOOD_MOON);settings.strength=0;
            state=NaturalSpawner.createState(289,level,(key,consumer)->consumer.accept(chunk),local);
            ((it.unimi.dsi.fastutil.objects.Object2IntMap<net.minecraft.world.entity.MobCategory>)globalField.get(state)).put(category,category.getMaxInstancesPerChunk());
            check(!(Boolean)global.invoke(state,category) && !local.canSpawn(category,chunk.getPos()),"Both caps restore after event ends");
            map.put(level.players().getFirst(),constructor.newInstance());BloodMoonSpawnProbe.calls=0;
            NaturalSpawner.spawnForChunk(level,chunk,state,java.util.List.of(category));
            check(BloodMoonSpawnProbe.calls==1,"Ordinary spawning returns to one pass");
            settings.strength=1;
        } catch(ReflectiveOperationException ex) {throw new AssertionError(ex);}
        finally {BloodMoonSpawnProbe.enabled=false;}
    }
}
