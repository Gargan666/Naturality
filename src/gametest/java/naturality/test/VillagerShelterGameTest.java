package naturality.test;

import naturality.villager.VillagerSnowShelter;
import naturality.weather.WeatherProfile;
import naturality.weather.WeatherWorldData;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public final class VillagerShelterGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 0 105 0");
            server.runCommand("time set 1000");
            server.runCommand("gamerule minecraft:advance_time false");
            server.runCommand("fill -18 100 -18 18 107 18 air");
            server.runCommand("fill -18 100 -18 18 100 18 stone");
            server.runCommand("fill 5 104 -2 9 104 2 oak_planks");
            server.runCommand("fillbiome -16 96 -16 15 127 15 snowy_plains");
            var roof = new BlockPos(7, 101, 0);
            var outside = new BlockPos(0, 101, 0);
            Villager[] body = new Villager[1];
            server.runOnServer(s -> {
                var level = s.overworld();
                WeatherWorldData.get(s).setProfile("minecraft:overworld", new WeatherProfile(false));
                s.setWeatherParameters(0, 12000, true, false);
                level.setRainLevel(1);
                body[0] = new Villager(EntityTypes.VILLAGER, level);
                body[0].setPos(.5, 101, .5); body[0].setOnGround(true); body[0].setVillagerXp(1);
                body[0].refreshBrain(level);
                body[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), roof));
                body[0].getBrain().setActiveActivityIfPossible(Activity.IDLE);
                level.addFreshEntity(body[0]);
                check(VillagerSnowShelter.snowing(level, body[0]), "Actual snowfall activates shelter preference");
            });
            context.waitTicks(240);
            server.runOnServer(s -> {
                var level = s.overworld();
                var villager = body[0];
                check(!level.canSeeSky(villager.blockPosition()), "Off-duty villager walks into reachable shelter: " + villager.position());
                villager.setNoAi(true);
                var controller = new VillagerSnowShelter();
                villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(outside, .5F, 0));
                controller.tick(level, villager, 500);
                check(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Sheltered villager rejects outdoor wandering");
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(roof, .5F, 0));
                controller.tick(level, villager, 501);
                check(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Indoor movement remains available");
                villager.getBrain().setActiveActivityIfPossible(Activity.PANIC);
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(outside, .5F, 0));
                controller.tick(level, villager, 502);
                check(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Panic movement takes priority");
                villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
                s.setWeatherParameters(12000, 0, false, false); level.setRainLevel(0);
                controller.tick(level, villager, 503);
                check(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "Clear weather permits outdoor wandering");
            });
            server.runCommand("fillbiome -16 96 -16 15 127 15 plains");
            server.runOnServer(s -> {
                s.setWeatherParameters(0, 12000, true, false); s.overworld().setRainLevel(1);
                check(!VillagerSnowShelter.snowing(s.overworld(), body[0]), "Warm rain does not trigger snow shelter");
                body[0].discard();
            });
        }
    }
}
