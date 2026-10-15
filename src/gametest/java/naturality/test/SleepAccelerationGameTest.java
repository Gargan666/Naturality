package naturality.test;

import naturality.config.NaturalityServerConfig;
import naturality.sleep.SleepAcceleration;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.AbstractBedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.GameType;

public final class SleepAccelerationGameTest implements FabricClientGameTest {
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    @Override public void runTest(ClientGameTestContext context) {
        var angle = new naturality.client.sleep.SleepSky.Angle();
        float initial = angle.sample(6.2F, .016, true);
        float wrapped = angle.sample(.05F, .016, true);
        check(wrapped > initial && wrapped < initial + .05F, "Sky crosses dawn continuously through the short angular arc");
        float frozen = angle.sample(.2F, 0, true);
        check(frozen == wrapped, "Paused sky easing does not advance");
        var snapshots = new naturality.client.sleep.SleepCloudClock();
        snapshots.sample(1000, 1, 0, true);
        check(snapshots.sample(1200, 2, .06, true) == 1100, "Snapshot interpolation has a bounded eased midpoint");
        check(snapshots.sample(1200, 2, .06, true) == 1200, "Interpolation reaches the server snapshot");
        check(snapshots.sample(9999, 2, 1, true) == 1200, "Without a new snapshot rendering holds rather than predicting time");
        check(snapshots.sample(1400, 3, 0, true) == 1200, "A new snapshot preserves continuity");
        check(angle.sample(.3F, 0, false) == .3F, "Sky motion returns to normal immediately on waking");
        var continuous = new naturality.client.sleep.SleepCloudClock();
        continuous.sample(1000, 1, 0, true);
        double a = continuous.sample(1200, 2, .08, true);
        double b = continuous.sample(1200, 2, .000001, true);
        double next = continuous.sample(1400, 3, .000001, true);
        check(Math.abs((next - b) - (b - a)) < .000001,
            "A new time packet carries the displayed velocity instead of stopping the sky");
        double held = continuous.sample(1400, 3, 1, true);
        check(held == 1400 && continuous.sample(1400, 3, 1, true) == held,
            "Missing packets hold the endpoint without extrapolating");
        check(Math.abs(SleepAcceleration.elapsedTicks(0, 30, 200, 30, 20) - 60300) < .00001,
            "Default 30-second ramp integrates exactly to 60,300 clock ticks");
        check(Math.abs(SleepAcceleration.elapsedTicks(29, 2, 200, 30, 20) - (8000 - 199.0 / 3)) < .00001,
            "An interval crossing the ramp cap integrates both speeds");
        var cloud = new naturality.client.sleep.SleepCloudClock();
        check(cloud.sample(1000, .016, false) == 1000, "Ordinary cloud movement is unchanged");
        cloud.sample(1000, 0, true);
        double drift = cloud.sample(1200, .016, true);
        check(drift > 1000 && drift < 1200, "Cloud drift eases between accelerated tick updates");
        check(cloud.sample(1400, 0, true) == drift, "Paused cloud drift does not advance");
        check(cloud.sample(1400, 0, false) == 1400 && cloud.sample(1500, .016, false) == 1500,
            "Cloud motion returns to normal immediately on waking");
        float eased = naturality.client.sleep.SleepAudio.ease(0, 1, .5);
        check(eased > 0 && eased < 1, "Sleep audio eases instead of snapping");
        check(Math.abs(naturality.client.sleep.SleepAudio.ease(eased, 1, .5)
            - naturality.client.sleep.SleepAudio.ease(0, 1, 1)) < .00001, "Audio easing is independent of update frequency");
        check(naturality.client.sleep.SleepAudio.highFrequencyGain(1) < naturality.client.sleep.SleepAudio.gain(1), "Sleep filter removes high frequencies more strongly than overall volume");
        var config = NaturalityServerConfig.get();
        boolean enabled = config.sleepAcceleration;
        int maximum = config.sleepMaxSpeed, ramp = config.sleepRampSeconds;
        try (var world = context.worldBuilder().create()) {
            var server = world.getServer();
            config.sleepAcceleration = true;
            config.sleepMaxSpeed = 2; config.sleepRampSeconds = 1;
            server.runCommand("time set day");
            server.runOnServer(s -> {
                var level = s.overworld();
                var player = s.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                for (var pos : BlockPos.betweenClosed(-4, 99, -4, 4, 104, 4))
                    level.setBlockAndUpdate(pos, pos.getY() == 99 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                var foot = new BlockPos(0, 100, 0); var head = foot.north();
                var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.NORTH);
                level.setBlockAndUpdate(foot, bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
                level.setBlockAndUpdate(head, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
                player.setPos(.5, 100, .5);
                var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
                zombie.setPos(2, 100, 0); zombie.setNoAi(true); level.addFreshEntity(zombie);
                player.awardStat(Stats.TIME_SINCE_REST, 12345);
                var block = (AbstractBedBlock)bed.getBlock();
                check(player.startSleepInBed(block, bed, block.getBedRule(level, head), head).right().isPresent(), "Daytime sleep near a monster succeeds in survival");
                check(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= 12345, "Entering bed must not reset phantom debt");
            });
            context.waitTicks(120);
            context.runOnClient(client -> {
                check(naturality.client.sleep.SleepSnapshots.latest(client.level) != null, "Server time packets capture celestial and cloud snapshots");
                check(client.level.tickRateManager().tickrate() == 20, "Sleep preserves normal client simulation rate");
                check(naturality.client.sleep.SleepVisuals.speed() == 2, "Visual speed is synchronized independently of tick rate");
                check(naturality.config.GameplaySettings.displayedSettings().sleepMaxSpeed() == 2,
                    "Server sleep configuration is synchronized to clients");
            });
            server.runOnServer(s -> {
                var definition = s.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
                var instance = s.clockManager().getInstance(definition);
                var saved = instance.packState();
                var advanceRule = net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME;
                boolean advanceTime = s.overworld().getGameRules().get(advanceRule);
                s.overworld().getGameRules().set(advanceRule, true, s);
                try {
                    long gameTime = levelTime(s);
                    int playerTicks = s.getPlayerList().getPlayers().getFirst().tickCount;
                    for (int tick = 0; tick < 1000; tick++) naturality.sleep.SleepServerSnapshots.tickClocks(s);
                    check(instance.totalTicks() == saved.totalTicks() + 2000,
                        "Two-speed clock advancement requires no extra simulation ticks");
                    check(levelTime(s) == gameTime && s.getPlayerList().getPlayers().getFirst().tickCount == playerTicks,
                        "Clock advancement does not replay world or entity ticks");
                    instance.loadFrom(new net.minecraft.world.clock.ClockState(saved.totalTicks(), .25F, .5F, false));
                    for (int tick = 0; tick < 100; tick++) naturality.sleep.SleepServerSnapshots.tickClocks(s);
                    check(instance.totalTicks() == saved.totalTicks() + 100 && instance.partialTick() == .25F,
                        "Clock batching preserves fractional clock rates and partial ticks");
                    instance.loadFrom(new net.minecraft.world.clock.ClockState(saved.totalTicks(), .25F, 1, true));
                    for (int tick = 0; tick < 100; tick++) naturality.sleep.SleepServerSnapshots.tickClocks(s);
                    check(instance.totalTicks() == saved.totalTicks() && instance.partialTick() == .25F,
                        "Paused clocks remain paused during batched sleep updates");
                    instance.loadFrom(saved);
                    naturality.sleep.SleepServerSnapshots.tickClocks(s);
                    s.clockManager().setTotalTicks(definition, 1234);
                    check(instance.totalTicks() == 1234, "Time commands have no delayed clock work to replay afterward");
                } finally {
                    instance.loadFrom(saved);
                    s.overworld().getGameRules().set(advanceRule, advanceTime, s);
                    s.getPlayerList().broadcastAll(s.clockManager().createFullSyncPacket());
                }
            });
            var particle = new ProbeParticle[1];
            var births = new int[1];
            var watching = new boolean[]{true};
            var visualClock = new long[2];
            context.runOnClient(client -> {
                particle[0] = new ProbeParticle(client.level);
                client.particleEngine.add(particle[0]);
                visualClock[0] = naturality.client.fire.ProceduralFire.ticks();
                naturality.client.sleep.SleepVisuals.register(c -> {
                    if (watching[0]) {
                        births[0]++;
                        c.particleEngine.add(new ProbeParticle(c.level));
                    }
                });
            });
            context.waitTicks(20);
            context.runOnClient(client -> {
                watching[0] = false;
                visualClock[1] = naturality.client.fire.ProceduralFire.ticks();
                check(births[0] >= 19 && births[0] <= 22, "Particle spawn callbacks keep normal cadence during sleep");
                check(particle[0].ticks >= 18 && particle[0].ticks <= 22 && particle[0].distance() >= 3.5,
                    "Particle movement accelerates while age and lifetime stay at normal cadence");
                check(visualClock[1] - visualClock[0] >= 35, "Procedural fire animation follows accelerated visual ticks");
                check(naturality.client.sleep.SleepAudio.amount() > .5, "Sleeping listener receives eased muffling");
                verifySleepFilter();
                double speed = naturality.client.sleep.SleepVisuals.speed();
                int before = particle[0].ticks, playerTicks = client.player.tickCount, beforeBirths = births[0];
                double beforeX = particle[0].distance();
                try {
                    naturality.client.sleep.SleepVisuals.accept(new naturality.sleep.SleepStatePayload(true, 200));
                    long beforeTime = client.level.getGameTime();
                    client.level.tick(() -> true);
                    check(client.level.getGameTime() == beforeTime + 1,
                        "Requested 200x rate must not predict extra client world time ahead of the server");
                    check(particle[0].ticks == before && births[0] == beforeBirths, "Normal clock ticking never replays particle ticking or spawning");
                    client.particleEngine.tick();
                    check(particle[0].ticks - before == 1, "The 200x default still executes only one particle tick");
                    check(Math.abs(particle[0].distance() - beforeX - 20) < .00001, "One particle movement uses the 200x velocity multiplier");
                    check(client.player.tickCount == playerTicks, "Visual acceleration must preserve the local input/player clock");
                } finally { naturality.client.sleep.SleepVisuals.accept(new naturality.sleep.SleepStatePayload(true, speed)); }
            });
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                check(player.isSleeping(), "Native deep sleep must not skip time or wake at 100 ticks");
                check(s.tickRateManager().tickrate() == 20 && SleepAcceleration.speed(s) == 2, "Time ramps to its cap while the server stays at 20 TPS");
                // Drive the real controller deterministically to test the strict rest threshold.
                player.stopSleepInBed(true, true);
                config.sleepMaxSpeed = 1;
                player.startSleeping(new BlockPos(0, 100, -1));
                for (int i = 0; i < 6000; i++) SleepAcceleration.tick(s);
                check(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) >= 12345, "Exactly half a night is insufficient");
                SleepAcceleration.tick(s);
                check(player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_REST)) == 0, "More than half a night resets phantom debt");
                player.stopSleepInBed(true, true);
                check(Math.abs(s.tickRateManager().tickrate() - 20) < .01, "Wake restores original rate immediately, before another server tick");
                config.sleepMaxSpeed = 2;
                s.tickRateManager().setTickRate(30);
                player.startSleeping(new BlockPos(0, 100, -1));
                for (int i = 0; i < 60; i++) SleepAcceleration.tick(s);
                check(s.tickRateManager().tickrate() == 30 && SleepAcceleration.speed(s) == 2, "Time accelerates without changing an administrator rate");
                s.tickRateManager().setTickRate(25);
                player.stopSleepInBed(true, true);
                check(s.tickRateManager().tickrate() == 25 && !SleepAcceleration.active(s), "Wake preserves administrator changes and immediately disables clock acceleration");
                s.tickRateManager().setTickRate(20);
                var pos = new BlockPos(2, 100, 2);
                levelBed(s.overworld(), pos);
                check(player.startSleeping(pos), "Can enter straw bed");
                player.stopSleepInBed(true, true);
                check(!s.overworld().getBlockState(pos).is(Blocks.STRAW_BED), "Immediate wake consumes straw bed");
                config.sleepAcceleration = false;
                var block = (AbstractBedBlock)Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED);
                check(!block.getBedRule(s.overworld(), new BlockPos(0, 100, -1)).canSleep(s.overworld()), "Disabled system restores daytime rejection");
                check(SleepAcceleration.multiplier(0, 20, 30) == 1 && SleepAcceleration.multiplier(30, 20, 30) == 20, "Ramp endpoints");
                config.sleepAcceleration = true;
                player.startSleeping(new BlockPos(0, 100, -1));
            });
            server.runCommand("time set 24000");
            context.waitTicks(3);
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                check(player.isSleeping(), "Crossing morning must not wake the sleeper");
                player.stopSleepInBed(true, true);
                check(!SleepAcceleration.active(s) && SleepAcceleration.clockTicks(s) == 1,
                    "Wake stops acceleration inside the wake operation");
                var definition = s.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD);
                var clock = s.clockManager().getInstance(definition);
                boolean advance = s.overworld().getGameRules().get(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME);
                s.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, true, s);
                long before = clock.totalTicks();
                naturality.sleep.SleepServerSnapshots.tickClocks(s);
                check(clock.totalTicks() == before + 1, "The very next clock update after waking advances by one tick");
                s.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, advance, s);
            });
            float[] beforeFade = new float[1];
            context.runOnClient(client -> beforeFade[0] = naturality.client.sleep.SleepAudio.amount());
            context.waitTicks(20);
            context.runOnClient(client -> {
                check(!naturality.client.sleep.SleepVisuals.accelerating(), "Wake stops particle velocity acceleration on the client");
                check(naturality.client.sleep.SleepVisuals.speed() == 1, "Wake restores normal particle velocity");
                check(naturality.client.sleep.SleepAudio.amount() < beforeFade[0], "Wake restores audio gradually");
                double offset = naturality.client.sleep.SleepVisuals.boostSeconds();
                naturality.client.sleep.SleepVisuals.accept(new naturality.sleep.SleepStatePayload(false, 1));
                check(naturality.client.sleep.SleepVisuals.boostSeconds() == offset,
                    "Wake keeps the cloud offset fixed instead of jumping or continuing acceleration");
            });
            // Run the actual server at the default 200x setting, then the 1000x cap.
            var observations = new long[3];
            for (int cap : new int[]{200, 1000}) {
                server.runOnServer(s -> {
                    config.sleepMaxSpeed = cap; config.sleepRampSeconds = 1;
                    s.overworld().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.ADVANCE_TIME, true, s);
                    s.getPlayerList().getPlayers().getFirst().startSleeping(new BlockPos(0, 100, -1));
                });
                context.waitTicks(30);
                server.runOnServer(s -> {
                    check(s.tickRateManager().tickrate() == 20 && SleepAcceleration.speed(s) == cap,
                        "Even the highest sleep speed leaves the server at 20 TPS");
                    observations[0] = levelTime(s);
                    observations[1] = s.clockManager().getInstance(s.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD)).totalTicks();
                    observations[2] = s.getPlayerList().getPlayers().getFirst().tickCount;
                });
                context.waitTicks(20);
                server.runOnServer(s -> {
                    long worldTicks = levelTime(s) - observations[0];
                    long clockTicks = s.clockManager().getInstance(s.registryAccess().getOrThrow(net.minecraft.world.clock.WorldClocks.OVERWORLD)).totalTicks() - observations[1];
                    long playerTicks = s.getPlayerList().getPlayers().getFirst().tickCount - observations[2];
                    check(worldTicks >= 15 && worldTicks <= 30 && playerTicks <= 30,
                        "One second of sleep executes ordinary world and player work, independent of speed");
                    check(clockTicks == worldTicks * cap, "High-speed time advancement matches the cap without extra ticks");
                    System.out.println("Sleep " + cap + "x: " + worldTicks + " world ticks, " + playerTicks
                        + " player ticks, " + clockTicks + " celestial ticks; server rate " + s.tickRateManager().tickrate());
                    s.getPlayerList().getPlayers().getFirst().stopSleepInBed(true, true);
                    check(!SleepAcceleration.active(s), "High-speed sleep stops synchronously on waking");
                });
                world.getConnection().waitForClientboundPackets();
                context.runOnClient(client -> check(naturality.client.sleep.SleepVisuals.speed() == 1,
                    "The wake packet immediately restores normal visual speed"));
            }
        } finally {
            config.sleepAcceleration = enabled;
            config.sleepMaxSpeed = maximum; config.sleepRampSeconds = ramp;
        }
    }
    private static long levelTime(net.minecraft.server.MinecraftServer server) { return server.overworld().getGameTime(); }
    private static final class ProbeParticle extends net.minecraft.client.particle.NoRenderParticle {
        int ticks;
        ProbeParticle(net.minecraft.client.multiplayer.ClientLevel level) { super(level, 0, 102, 0); lifetime = 1000; hasPhysics = false; }
        @Override public void tick() { ticks++; age++; move(.1, 0, 0); }
        double distance() { return x; }
    }
    private static void verifySleepFilter() {
        com.mojang.blaze3d.audio.Channel channel = null;
        try {
            var create = com.mojang.blaze3d.audio.Channel.class.getDeclaredMethod("create");
            create.setAccessible(true);
            channel = (com.mojang.blaze3d.audio.Channel)create.invoke(null);
            check(channel != null, "OpenAL channel can be created during sleep");
            ((naturality.client.sound.WeatherAudioFilter)channel).naturality$setEnvironmentAmounts(0, 0);
            if (org.lwjgl.openal.ALC.getCapabilities().ALC_EXT_EFX) {
                var field = com.mojang.blaze3d.audio.Channel.class.getDeclaredField("naturality$weatherFilter");
                field.setAccessible(true);
                int filter = field.getInt(channel);
                check(filter != 0, "Sleep alone attaches a low-pass filter without immersion or shelter");
                float hf = org.lwjgl.openal.EXTEfx.alGetFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF);
                check(Math.abs(hf - naturality.client.sleep.SleepAudio.highFrequencyGain(naturality.client.sleep.SleepAudio.amount())) < .0001,
                    "Actual OpenAL high-frequency gain follows eased sleep depth");
                ((naturality.client.sound.WeatherAudioFilter)channel).naturality$setEnvironmentAmounts(1, 1);
                float combined = org.lwjgl.openal.EXTEfx.alGetFilterf(filter, org.lwjgl.openal.EXTEfx.AL_LOWPASS_GAINHF);
                check(combined < hf, "Sleep filtering composes with shelter and underwater filtering");
            }
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        finally { if (channel != null) channel.destroy(); }
    }
    private static void levelBed(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        var bed = Blocks.STRAW_BED.defaultBlockState().setValue(AbstractBedBlock.FACING, Direction.NORTH);
        level.setBlockAndUpdate(pos.south(), bed.setValue(AbstractBedBlock.PART, BedPart.FOOT));
        level.setBlockAndUpdate(pos, bed.setValue(AbstractBedBlock.PART, BedPart.HEAD));
    }
}
