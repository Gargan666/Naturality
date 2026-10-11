package naturality.villager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.npc.villager.Villager;

public final class GossipReactions {
    public static final byte LISTEN_POSITIVE = 68, LISTEN_NEGATIVE = 69, TELL_POSITIVE = 70, TELL_NEGATIVE = 71, CONVERSE = 72;
    public static int duration(byte reaction) {
        return reaction == CONVERSE ? 100 : reaction >= LISTEN_POSITIVE && reaction <= TELL_NEGATIVE ? 180 : 0;
    }
    public static void focus(ServerLevel level, Villager body) {
        var state = Reputation.state(body);
        if (state.conversationPartner == null) return;
        var partner = level.getEntity(state.conversationPartner);
        int age = body.tickCount - state.gossipStart;
        if (!naturality.config.Feature.REPUTATION.enabled() || age < 0 || age >= duration(state.gossipReaction)
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
        if (!naturality.config.Feature.REPUTATION.enabled()) return;
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
        if (!naturality.config.Feature.REPUTATION.enabled() || body.isSleeping() || Reputation.state(body).fleeing) return;
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
        if (!naturality.config.Feature.REPUTATION.enabled()) return;
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
