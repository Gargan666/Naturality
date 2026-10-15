package naturality.systems.reputation;

import naturality.systems.villagerwork.sleep.VillagerSleep;
import java.util.Comparator;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.npc.villager.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import org.jspecify.annotations.Nullable;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;

public final class Reputation {
    public static final byte NOD_EVENT = 67;
    public static void initialize() {
        ReputationCommand.initialize();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, server) -> {
            if (ReputationData.get(server).takeHero(handler.player.getUUID()))
                handler.player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
        });
    }
    public static void credit(ServerLevel level, java.util.UUID donor, int amount) {
        var player = java.util.Objects.requireNonNull(level.getServer()).getPlayerList().getPlayer(donor);
        if (player != null) { change(player, amount); return; }
        var data = ReputationData.get(java.util.Objects.requireNonNull(level.getServer()));
        if (amount > 0 && data.score(donor) == 100) data.queueHero(donor);
        data.change(donor, amount);
    }
    public static int score(Player player) {
        return player.level() instanceof ServerLevel level ? ReputationData.get(java.util.Objects.requireNonNull(level.getServer())).score(player.getUUID()) : 50;
    }
    public static void set(Player player, int value) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = ReputationData.get(java.util.Objects.requireNonNull(level.getServer()));
        int after = data.change(player.getUUID(), value - data.score(player.getUUID()));
        if (after <= 10 && player instanceof ServerPlayer serverPlayer && player.containerMenu instanceof MerchantMenu)
            serverPlayer.closeContainer();
    }
    public static void upset(ServerLevel level, net.minecraft.world.phys.Vec3 origin) {
        for (var villager : level.getEntitiesOfClass(Villager.class, new net.minecraft.world.phys.AABB(origin, origin).inflate(16), Villager::isAlive)) {
            villager.setUnhappyCounter(40);
            level.broadcastEntityEvent(villager, (byte)13);
            villager.makeSound(SoundEvents.VILLAGER_NO);
        }
    }
    public static void change(Player player, int amount) {
        change(player, amount, player.position());
    }
    public static void change(Player player, int amount, net.minecraft.world.phys.Vec3 origin) {
        change(player, amount, origin, true);
    }
    private static void change(Player player, int amount, net.minecraft.world.phys.Vec3 origin, boolean notifyNearby) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = ReputationData.get(java.util.Objects.requireNonNull(level.getServer()));
        int before = data.score(player.getUUID()), after = data.change(player.getUUID(), amount);
        if (amount < 0 && notifyNearby) upset(level, origin);
        if (amount > 0 && before == 100) player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
        if (after <= 10 && player instanceof ServerPlayer serverPlayer && player.containerMenu instanceof MerchantMenu) serverPlayer.closeContainer();
    }
    public static ReputationState state(Mob mob) { return ((ReputationHolder)mob).naturality$reputationState(); }
    public static boolean tradeBlocked(Villager body, Player player) {
        if (score(player) <= 10 || VillagerSleep.debt(body) >= 24000) return true;
        long time = body.level().getOverworldClockTime();
        var state = state(body);
        if (state.wakeUntil >= 0 && (time >= state.wakeUntil || Math.floorMod(time, 24000) < 12000)) state.wakeUntil = -1;
        return state.wakeUntil >= 0;
    }
    public static void wake(Villager body, Player player) {
        if (body.isSleeping()) forceWoken(body, player);
    }
    public static void forceWoken(Villager body, Player player) {
        long time = body.level().getOverworldClockTime();
        if (Math.floorMod(time, 24000) >= 12000) {
            change(player, -1, body.position(), false);
            body.setUnhappyCounter(40);
            body.makeSound(SoundEvents.VILLAGER_NO);
            ((ServerLevel)body.level()).broadcastEntityEvent(body, (byte)13);
            body.getGossips().add(player.getUUID(), net.minecraft.world.entity.ai.gossip.GossipType.MINOR_NEGATIVE, 25);
            state(body).wakeUntil = (Math.floorDiv(time, 24000) + 1) * 24000;
        }
    }
    public static boolean illagerAlly(Mob mob, Player player) { return (mob instanceof Raider || mob instanceof Vex) && score(player) <= 10; }
    public static int priceDiff(int count, int score) { return -(int)Math.round(count * (score - 50) / 100.0); }
    public static boolean crop(ItemStack stack) {
        return stack.is(ItemTags.VILLAGER_PLANTABLE_SEEDS) || stack.is(Items.WHEAT) || stack.is(Items.CARROT)
            || stack.is(Items.POTATO) || stack.is(Items.BEETROOT) || stack.is(Items.MELON_SLICE) || stack.is(Items.PUMPKIN);
    }
    public static int rodQuality(ItemStack stack) {
        if (!stack.is(Items.FISHING_ROD)) return 0;
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet().stream()
            .filter(entry -> !entry.getKey().is(EnchantmentTags.CURSE)).mapToInt(entry -> entry.getIntValue()).sum();
    }
    public static boolean gift(Villager body, ItemStack stack) {
        var profession = body.getVillagerData().profession();
        return stack.is(Items.EMERALD) || profession.is(VillagerProfession.FARMER) && crop(stack)
            || profession.is(VillagerProfession.FISHERMAN) && stack.is(Items.FISHING_ROD)
                && rodQuality(stack) > rodQuality(state(body).rod);
    }
    /** Credit only accepted dropped items, never a mere offer or failed pickup. */
    public static boolean acceptGift(ServerLevel level, Villager body, ItemEntity entity) {
        var stack = entity.getItem();
        var donor = ((GiftDonor)entity).naturality$giftDonor();
        if (donor == null || !gift(body, stack)) return false;
        int amount;
        int reward;
        if (stack.is(Items.FISHING_ROD)) {
            state(body).rod = stack.copyWithCount(1); amount = 1; reward = 3;
        } else {
            var remainder = body.getInventory().addItem(stack.copy());
            amount = stack.getCount() - remainder.getCount();
            if (amount == 0) return true;
            reward = stack.is(Items.EMERALD) ? amount / 20 : 3;
        }
        body.take(entity, amount);
        stack.shrink(amount);
        if (stack.isEmpty()) entity.discard(); else entity.setItem(stack);
        credit(level, donor, reward);
        body.makeSound(SoundEvents.VILLAGER_YES);
        level.broadcastEntityEvent(body, (byte)14);
        return true;
    }
    public static void nitwit(ServerLevel level, Villager body, Player player) {
        int reputation = score(player);
        player.sendOverlayMessage(Component.literal("Reputation: " + reputation));
        if (reputation > 50) { body.setUnhappyCounter(0); body.makeSound(SoundEvents.VILLAGER_YES); level.broadcastEntityEvent(body, NOD_EVENT); }
        else { body.setUnhappyCounter(40); body.makeSound(SoundEvents.VILLAGER_NO); }
    }
    public static void react(ServerLevel level, Mob mob) {
        if (mob instanceof Villager body) {
            var tradingPlayer = body.getTradingPlayer();
            if (body.isTrading() && tradingPlayer instanceof ServerPlayer serverPlayer && tradeBlocked(body, tradingPlayer))
                serverPlayer.closeContainer();
            var player = level.players().stream()
                .filter(other -> other.isAlive() && !other.isCreative() && !other.isSpectator()
                    && body.distanceToSqr(other) <= 144 && score(other) == 0)
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElseGet(() -> null);
            var state = state(body);
            if (player != null) {
                if (body.isSleeping()) body.stopSleeping();
                if (body.getTradingPlayer() instanceof ServerPlayer trader) trader.closeContainer();
                state.fleeing = true;
                state.fearedPlayer = player.getUUID();
                body.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, player);
                body.getBrain().setActiveActivityIfPossible(Activity.PANIC);
                if (state.escapeTick != body.tickCount && (state.escapeTarget == null || body.tickCount % 10 == 0)) {
                    state.escapeTick = body.tickCount;
                    var away = DefaultRandomPos.getPosAway(body, 16, 7, player.position());
                    if (away == null) away = fallbackEscape(level, body, player);
                    if (away != null) {
                        state.escapeTarget = away;
                        body.getNavigation().moveTo(away.x, away.y, away.z, .85);
                    }
                }
                if (state.escapeTarget != null)
                    body.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(state.escapeTarget, .85F, 0));
            } else if (state.fleeing) {
                state.fleeing = false;
                body.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE)
                    .filter(threat -> threat.getUUID().equals(state.fearedPlayer))
                    .ifPresent(_ -> body.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE));
                body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                    .filter(target -> state.escapeTarget != null && target.getTarget().currentBlockPosition()
                        .equals(net.minecraft.core.BlockPos.containing(state.escapeTarget)))
                    .ifPresent(_ -> { body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); body.getNavigation().stop(); });
                state.escapeTarget = null;
                state.fearedPlayer = null;
                if (!body.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                        && !body.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE))
                    body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            }
        } else if (mob instanceof IronGolem golem && !golem.isPlayerCreated() && mob.tickCount % 20 == 0) {
            var candidate = level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(32),
                other -> other.isAlive() && !other.isCreative() && !other.isSpectator() && score(other) == 0 && golem.canAttack(other))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr));
            var player = candidate.isPresent() ? candidate.orElseThrow() : null;
            var state = state(golem);
            if (player != null) { golem.setTarget(player); state.reputationTarget = player.getUUID(); }
            else if (golem.getTarget() instanceof Player current && current.getUUID().equals(state.reputationTarget) && score(current) > 0) {
                golem.setTarget(null); state.reputationTarget = null;
            }
        }
    }
    private static net.minecraft.world.phys.Vec3 fallbackEscape(ServerLevel level, Villager body, Player player) {
        var direction = body.position().subtract(player.position()).multiply(1, 0, 1).normalize();
        if (direction.lengthSqr() < .001) direction = new net.minecraft.world.phys.Vec3(1, 0, 0);
        for (int distance : new int[]{12, 8, 4}) {
            var center = net.minecraft.core.BlockPos.containing(body.position().add(direction.scale(distance)));
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                var pos = center.offset(0, dy, 0);
                if (!naturality.api.worlds.LoadedChunks.has(level, pos) || !level.getFluidState(pos).isEmpty()
                    || !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
                    || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) continue;
                var path = body.getNavigation().createPath(pos, 0);
                if (path != null && path.canReach()) return net.minecraft.world.phys.Vec3.atBottomCenterOf(pos);
            }
        }
        return null;
    }
    private Reputation() { }

    /** One world-wide score per player and one cure reward per villager lineage. */
    public static final class ReputationData extends SavedData {
        public static final SavedDataType<ReputationData> TYPE = new SavedDataType<>(Naturality.id("reputation"),
            ReputationData::new, Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(ReputationData::new, data -> data.values), DataFixTypes.LEVEL);
        private final Map<String, Integer> values;
        public ReputationData() { values = new HashMap<>(); }
        private ReputationData(Map<String, Integer> values) { this.values = new HashMap<>(values); }
        public static ReputationData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
        public int score(UUID player) { return Math.clamp(values.getOrDefault("player:" + player, 50), 0, 100); }
        public int change(UUID player, int amount) {
            int score = Math.clamp(score(player) + amount, 0, 100);
            values.put("player:" + player, score); setDirty(); return score;
        }
        public void queueHero(UUID player) { values.put("hero:" + player, 1); setDirty(); }
        public boolean takeHero(UUID player) { boolean pending = values.remove("hero:" + player) != null; if (pending) setDirty(); return pending; }
        public boolean firstCure(UUID lineage) {
            if (values.putIfAbsent("cured:" + lineage, 1) != null) return false;
            setDirty(); return true;
        }
    }

    public static final class ReputationState {
        public UUID lineage = UUID.randomUUID();
        public ItemStack rod = ItemStack.EMPTY;
        public long wakeUntil = -1;
        public long lastSleep = -1, recoveryUntil = -1;
        public boolean seekingBed;
        public boolean sleepCoreOnly;
        public net.minecraft.core.BlockPos sleepBed;
        public int bedSearchTick = -1;
        public int nodStart = -100;
        public int gossipStart = -100;
        public byte gossipReaction;
        public UUID conversationPartner;
        public boolean fleeing;
        public UUID fearedPlayer;
        public net.minecraft.world.phys.Vec3 escapeTarget;
        public int escapeTick = -1;
        public UUID reputationTarget;
        public void copyFrom(ReputationState other) { lineage = other.lineage; rod = other.rod.copy(); wakeUntil = other.wakeUntil; lastSleep = other.lastSleep; recoveryUntil = other.recoveryUntil; }
        public void save(ValueOutput output) {
            output.store("NaturalityVillagerLineage", UUIDUtil.CODEC, lineage);
            output.putLong("NaturalityWokenUntil", wakeUntil);
            output.putLong("NaturalityLastSleep", lastSleep);
            output.putLong("NaturalityRecoverySleepUntil", recoveryUntil);
            if (!rod.isEmpty()) output.store("NaturalityFishingRod", ItemStack.CODEC, rod);
        }
        public void load(ValueInput input) {
            lineage = input.read("NaturalityVillagerLineage", UUIDUtil.CODEC).orElse(lineage);
            wakeUntil = input.getLongOr("NaturalityWokenUntil", -1);
            lastSleep = input.getLongOr("NaturalityLastSleep", -1);
            recoveryUntil = input.getLongOr("NaturalityRecoverySleepUntil", -1);
            rod = input.read("NaturalityFishingRod", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        }
    }

    public interface ReputationHolder { ReputationState naturality$reputationState(); }

    public static final class ReputationCommand {
        public static void initialize() {
            CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
                var root = Commands.literal("reputation").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                    .executes(c -> {
                        var player = c.getSource().getPlayerOrException();
                        c.getSource().sendSuccess(() -> Component.literal("Reputation: " + Reputation.score(player)), false);
                        return Reputation.score(player);
                    });
                root.then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                    var player = c.getSource().getPlayerOrException();
                    Reputation.set(player, IntegerArgumentType.getInteger(c, "value"));
                    c.getSource().sendSuccess(() -> Component.literal("Reputation: " + Reputation.score(player)), true);
                    return 1;
                }));
                root.then(Commands.literal("set").then(Commands.argument("players", EntityArgument.players())
                    .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(c -> {
                        var players = EntityArgument.getPlayers(c, "players");
                        int value = IntegerArgumentType.getInteger(c, "value");
                        players.forEach(player -> Reputation.set(player, value));
                        c.getSource().sendSuccess(() -> Component.literal("Set reputation to " + value + " for " + players.size() + " player(s)."), true);
                        return players.size();
                    }))));
                dispatcher.register(root);
            });
        }
        private ReputationCommand() {}
    }

    public interface GiftDonor { @Nullable UUID naturality$giftDonor(); }

    public static final class GossipReactions {
        public static final byte LISTEN_POSITIVE = 68, LISTEN_NEGATIVE = 69, TELL_POSITIVE = 70, TELL_NEGATIVE = 71, CONVERSE = 72;
        public static int duration(byte reaction) {
            return reaction == CONVERSE ? 100 : reaction >= LISTEN_POSITIVE && reaction <= TELL_NEGATIVE ? 180 : 0;
        }
        public static void focus(ServerLevel level, Villager body) {
            var state = Reputation.state(body);
            if (state.conversationPartner == null) return;
            var partner = level.getEntity(state.conversationPartner);
            int age = body.tickCount - state.gossipStart;
            if (!naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled() || age < 0 || age >= duration(state.gossipReaction)
                    || partner == null || !partner.isAlive() || body.distanceToSqr(partner) > 36
                    || body.isSleeping() || body.isTrading() || state.fleeing
                    || body.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE)
                    || body.getBrain().hasMemoryValue(net.minecraft.world.entity.ai.memory.MemoryModuleType.HURT_BY)) {
                state.conversationPartner = null;
                return;
            }
            body.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET,
                new net.minecraft.world.entity.ai.behavior.EntityTracker(partner, true));
            body.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET,
                (net.minecraft.world.entity.LivingEntity)partner);
            body.getLookControl().setLookAt(partner, 30, 30);
            if (body.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.IDLE)
                    || body.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.MEET)) {
                body.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                body.getNavigation().stop();
            }
        }
        public static Map<UUID, Map<GossipType, Integer>> snapshot(GossipContainer gossip) {
            Map<UUID, Map<GossipType, Integer>> result = new HashMap<>();
            gossip.getGossipEntries().forEach((id, entries) -> {
                Map<GossipType, Integer> copy = new HashMap<>();
                entries.object2IntEntrySet().forEach(entry -> copy.put(entry.getKey(), entry.getIntValue()));
                result.put(id, copy);
            });
            return result;
        }
        public static void transferred(ServerLevel level, Villager listener, Villager speaker,
                Map<UUID, Map<GossipType, Integer>> before) {
            if (!naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled()) return;
            int positive = 0, negative = 0;
            for (var player : listener.getGossips().getGossipEntries().entrySet())
                for (var entry : player.getValue().object2IntEntrySet()) {
                    int change = entry.getIntValue() - before.getOrDefault(player.getKey(), Map.of()).getOrDefault(entry.getKey(), 0);
                    if (change <= 0) continue;
                    int weight = change * entry.getKey().weight;
                    if (weight > 0) positive += weight; else negative -= weight;
                }
            if (positive == 0 && negative == 0) return;
            boolean good = positive > negative;
            begin(level, listener, speaker, good ? LISTEN_POSITIVE : LISTEN_NEGATIVE);
            begin(level, speaker, listener, good ? TELL_POSITIVE : TELL_NEGATIVE);
        }
        public static void speaking(ServerLevel level, Villager body) {
            if (!naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled() || body.isSleeping() || Reputation.state(body).fleeing) return;
            var partner = body.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.INTERACTION_TARGET).orElse(null);
            if (!(partner instanceof Villager other) || !partner.isAlive() || body.distanceToSqr(partner) > 16) return;
            neutral(level, body, other);
            neutral(level, other, body);
        }
        private static void neutral(ServerLevel level, Villager body, Villager partner) {
            var state = Reputation.state(body);
            if (body.tickCount - state.gossipStart < duration(state.gossipReaction)) return;
            state.gossipStart = body.tickCount;
            state.gossipReaction = CONVERSE;
            state.conversationPartner = partner.getUUID();
            level.broadcastEntityEvent(body, CONVERSE);
        }
        private static void begin(ServerLevel level, Villager body, Villager partner, byte event) {
            var state = Reputation.state(body);
            if (body.tickCount - state.gossipStart < duration(state.gossipReaction) && state.gossipReaction != CONVERSE) return;
            state.gossipStart = body.tickCount;
            state.gossipReaction = event;
            state.conversationPartner = partner.getUUID();
            level.broadcastEntityEvent(body, event);
            sound(level, body, event >= TELL_POSITIVE ? SoundEvents.VILLAGER_AMBIENT
                : event == LISTEN_POSITIVE ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO);
        }
        public static void tick(ServerLevel level, Villager body) {
            if (!naturality.config.startup.FeatureConfig.Feature.REPUTATION.enabled()) return;
            var state = Reputation.state(body);
            int age = body.tickCount - state.gossipStart;
            if (state.gossipReaction >= TELL_POSITIVE && state.gossipReaction <= TELL_NEGATIVE && age > 0 && age < duration(state.gossipReaction) && age % 30 == 0
                    && !body.isSleeping() && !state.fleeing)
                sound(level, body, age % 60 == 0 ? SoundEvents.VILLAGER_AMBIENT
                    : state.gossipReaction == TELL_POSITIVE ? SoundEvents.VILLAGER_YES : SoundEvents.VILLAGER_NO);
        }
        private static void sound(ServerLevel level, Villager body, net.minecraft.sounds.SoundEvent sound) {
            level.playSound(null, body.blockPosition(), sound, SoundSource.NEUTRAL, .25F, .95F + body.getRandom().nextFloat() * .1F);
        }
        private GossipReactions() {}
    }

    public interface VillageChest {
        boolean naturality$isVillageChest();
        default boolean naturality$isVillageSlot(int slot) { return naturality$isVillageChest(); }
        default void naturality$markVillageChest(boolean marked) { }
    }
}
