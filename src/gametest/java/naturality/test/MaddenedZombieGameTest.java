package naturality.test;

import naturality.bootstrap.ContentRegistration.NaturalityEffects;
import naturality.entities.maddened.MaddenedZombies.MaddenedZombie;
import naturality.entities.maddened.MaddenedZombies;
import naturality.client.entities.maddened.MaddenedZombieRenderer;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.util.ProblemReporter;

public final class MaddenedZombieGameTest implements FabricClientGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public void runTest(ClientGameTestContext context) {
        try (var world = TestWorlds.disposable(context).create()) {
            var server = world.getServer();
            server.runCommand("gamemode spectator @a");
            server.runCommand("time set midnight");
            server.runCommand("effect give @a night_vision infinite 0 true");
            server.runCommand("fill -8 100 -8 24 100 8 stone");
            server.runCommand("tp @a 0 102 5 180 10");
            server.runCommand("summon naturality:maddened_zombie 0 101 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b}");
            server.runCommand("summon naturality:maddened_zombie 3 101 0 {NoAI:1b,Silent:1b,PersistenceRequired:1b,IsBaby:1b}");
            context.waitTicks(20);
            context.runOnClient(client -> {
                int count = 0;
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof MaddenedZombie zombie) {
                    count++;
                    check(zombie.getMaxHealth() == 40 && zombie.getHealth() == 40, "Adult and baby spawn with double health");
                    var renderer = (MaddenedZombieRenderer)client.getEntityRenderDispatcher().getRenderer(zombie);
                    var state = renderer.createRenderState(zombie, 1);
                    check(renderer.getTextureLocation(state).getPath().equals("textures/entity/maddened_zombie.png"), "Custom texture for adults and babies");
                }
                check(count == 2, "Adult and baby custom zombie fixtures");
            });
            context.takeScreenshot("maddened-zombie-adult-baby-model");
            server.runCommand("effect clear @a night_vision");
            context.waitTicks(10);
            context.takeScreenshot("maddened-zombie-emissive-night");
            server.runOnServer(s -> {
                for (var e : s.overworld().getAllEntities()) if (e instanceof MaddenedZombie zombie)
                    zombie.hurtServer(s.overworld(), s.overworld().damageSources().generic(), 1);
            });
            context.waitTicks(5);
            context.runOnClient(client -> {
                for (var e : TestFixtures.level(client).entitiesForRendering()) if (e instanceof MaddenedZombie zombie) {
                    var renderer = (MaddenedZombieRenderer)client.getEntityRenderDispatcher().getRenderer(zombie);
                    check(zombie.isEnraged() && ((MaddenedZombieRenderer.State)renderer.createRenderState(zombie, 1)).enraged,
                        "Rage shiver state synchronizes to adult and baby renderers");
                    try {
                        var sound=new net.minecraft.client.resources.sounds.SimpleSoundInstance(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT,
                            net.minecraft.sounds.SoundSource.HOSTILE,1,.25F,net.minecraft.util.RandomSource.create(1),zombie.getX(),zombie.getY(),zombie.getZ());
                        sound.getOrResolve(client.getSoundManager());
                        var field=net.minecraft.client.sounds.SoundManager.class.getDeclaredField("soundEngine");field.setAccessible(true);
                        var method=net.minecraft.client.sounds.SoundEngine.class.getDeclaredMethod("calculatePitch",net.minecraft.client.resources.sounds.SoundInstance.class);
                        method.setAccessible(true);
                        check((Float)method.invoke(field.get(client.getSoundManager()),sound)<.5F,"Maddened playback bypasses vanilla half-pitch floor");
                    } catch(ReflectiveOperationException ex) {throw new AssertionError(ex);}
                }
            });
            context.takeScreenshot("maddened-zombie-rage-shivering");
            server.runOnServer(s -> {
                var level = s.overworld();
                var zombie = TestFixtures.require(MaddenedZombies.TYPE.create(level, EntitySpawnReason.COMMAND), "zombie");
                zombie.setPos(12, 101, 0); zombie.setNoAi(true); level.addFreshEntity(zombie);
                var ordinary = TestFixtures.require(EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND), "ordinary");
                zombie.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, 1, 1.4F);
                check(Math.abs(MaddenedSoundProbe.pitch - .7F) < .0001, "Zombie sound pitch is halved");
                var creeper = TestFixtures.require(naturality.entities.maddened.MaddenedCreepers.TYPE.create(level, EntitySpawnReason.COMMAND), "creeper");
                creeper.playSound(net.minecraft.sounds.SoundEvents.CREEPER_PRIMED, 1, .8F);
                check(Math.abs(MaddenedSoundProbe.pitch - .4F) < .0001, "Creeper priming sound pitch is halved");
                creeper.playSound(net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value(), 1, .8F);
                check(Math.abs(MaddenedSoundProbe.pitch - .8F) < .0001, "Creeper explosion pitch stays normal");
                ordinary.playSound(net.minecraft.sounds.SoundEvents.ZOMBIE_AMBIENT, 1, 1.4F);
                check(Math.abs(MaddenedSoundProbe.pitch - 1.4F) < .0001, "Ordinary zombie pitch unchanged");
                check(zombie.getMaxHealth() == ordinary.getMaxHealth() * 2 && zombie.getHealth() == zombie.getMaxHealth(),
                    "Maximum and initial health are twice a regular zombie");
                check(zombie.is(net.minecraft.tags.EntityTypeTags.ZOMBIES)
                    && zombie.is(net.minecraft.tags.EntityTypeTags.UNDEAD)
                    && zombie.is(net.minecraft.tags.EntityTypeTags.SENSITIVE_TO_SMITE), "Normal zombie undead classification");
                check(!zombie.canBeAffected(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 20)),
                    "Zombie retains poison immunity");
                var victim = TestFixtures.require(EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND), "victim");
                victim.setPos(13, 101, 0); victim.setNoAi(true);
                TestFixtures.require(victim.getAttribute(Attributes.MAX_HEALTH), "victim.getAttribute(Attributes.MAX_HEALTH)").setBaseValue(100); victim.setHealth(100);
                level.addFreshEntity(victim);
                double speed = zombie.getAttributeValue(Attributes.MOVEMENT_SPEED);
                double attack = zombie.getAttributeValue(Attributes.ATTACK_DAMAGE);
                float health = victim.getHealth();
                check(zombie.doHurtTarget(level, victim), "Ordinary melee hit succeeds");
                float normalDamage = health - victim.getHealth();
                check(victim.hasEffect(NaturalityEffects.INSANITY), "Successful attack applies Insanity");
                victim.removeEffect(NaturalityEffects.INSANITY); victim.setPermanentlyInvulnerable(true); victim.setInvulnerableTime(0);
                check(!zombie.doHurtTarget(level, victim) && !victim.hasEffect(NaturalityEffects.INSANITY), "Rejected hits do not apply Insanity");
                zombie.setPermanentlyInvulnerable(true);
                check(!zombie.hurtServer(level, level.damageSources().generic(), 1) && zombie.rageTicks() == 0, "Rejected damage does not trigger rage");
                zombie.setPermanentlyInvulnerable(false);
                check(zombie.hurtServer(level, level.damageSources().generic(), 1), "Taking damage starts rage");
                check(zombie.rageTicks() == 200 && zombie.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed * 2
                    && zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack * 2, "Rage exactly doubles speed and damage");
                victim.setPermanentlyInvulnerable(false);
                zombie.setNoAi(false); zombie.setTarget(victim); zombie.setOnGround(true);
                victim.setPos(zombie.getX()+3, zombie.getY(), zombie.getZ());
                var leap = new MaddenedZombie.RageLeapGoal(zombie);
                boolean canLeap=false;
                for(int i=0;i<100 && !canLeap;i++) canLeap=leap.canUse();
                check(canLeap, "Enraged zombie enables spider-style leap: rage="+zombie.isEnraged()+" ai="+!zombie.isNoAi()+" ground="+zombie.onGround()+" water="+zombie.isInWater()+" target="+zombie.getTarget()+" distance="+zombie.distanceToSqr(victim)+" los="+zombie.getSensing().hasLineOfSight(victim));
                leap.start();
                check(zombie.getDeltaMovement().x>0 && zombie.getDeltaMovement().y>.3, "Leap points toward target and upward");
                zombie.setNoAi(true); zombie.setTarget(null); zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                victim.setPermanentlyInvulnerable(false);
                // LivingEntity's separate damage cooldown must elapse between measured hits.
                for (int i = 0; i < 20; i++) victim.tick();
                health = victim.getHealth();
                check(zombie.doHurtTarget(level, victim) && Math.abs(health - victim.getHealth() - normalDamage * 2) < .001,
                    "Actual melee damage doubles during rage");
                for (int i = 0; i < 30; i++) zombie.tick();
                zombie.setInvulnerableTime(0); zombie.hurtServer(level, level.damageSources().generic(), 1);
                check(zombie.rageTicks() == 200 && zombie.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack * 2,
                    "Further damage refreshes timer without stacking multipliers");
                var saved = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, s.registryAccess());
                zombie.saveWithoutId(saved);
                var restored = TestFixtures.require(MaddenedZombies.TYPE.create(level, EntitySpawnReason.COMMAND), "restored");
                restored.load(TagValueInput.create(ProblemReporter.DISCARDING, s.registryAccess(), saved.buildResult()));
                restored.setId(-3_000_000);
                check(restored.rageTicks() == 200 && restored.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed * 2,
                    "Rage timer and transient modifiers restore from save");
                for (int i = 0; i < 200; i++) restored.tick();
                check(restored.rageTicks() == 0 && restored.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed
                    && restored.getAttributeValue(Attributes.ATTACK_DAMAGE) == attack, "Rage expires and restores baseline exactly");
                check(!restored.isEnraged() && !new MaddenedZombie.RageLeapGoal(restored).canUse(), "Expired rage stops shaking and leaping");
                zombie.discard(); victim.discard();
            });
        }
    }
}
