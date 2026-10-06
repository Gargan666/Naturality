package naturality.villager;

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

public final class Reputation {
    public static final byte NOD_EVENT = 67;
    public static void initialize() {
        ReputationCommand.initialize();
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (ReputationData.get(server).takeHero(handler.player.getUUID()))
                handler.player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 1200, 0));
        });
    }
    public static void credit(ServerLevel level, java.util.UUID donor, int amount) {
        var player = level.getServer().getPlayerList().getPlayer(donor);
        if (player != null) { change(player, amount); return; }
        var data = ReputationData.get(level.getServer());
        if (amount > 0 && data.score(donor) == 100) data.queueHero(donor);
        data.change(donor, amount);
    }
    public static int score(Player player) {
        return player.level() instanceof ServerLevel level ? ReputationData.get(level.getServer()).score(player.getUUID()) : 50;
    }
    public static void set(Player player, int value) {
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = ReputationData.get(level.getServer());
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
        if (!(player.level() instanceof ServerLevel level)) return;
        var data = ReputationData.get(level.getServer());
        int before = data.score(player.getUUID()), after = data.change(player.getUUID(), amount);
        if (amount < 0) upset(level, origin);
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
            change(player, -1, body.position());
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
            if (body.isTrading() && body.getTradingPlayer() != null && tradeBlocked(body, body.getTradingPlayer()))
                ((ServerPlayer)body.getTradingPlayer()).closeContainer();
            var player = level.players().stream()
                .filter(other -> other.isAlive() && !other.isCreative() && !other.isSpectator()
                    && body.distanceToSqr(other) <= 144 && score(other) == 0)
                .min(Comparator.comparingDouble(body::distanceToSqr)).orElse(null);
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
                    .ifPresent(threat -> body.getBrain().eraseMemory(MemoryModuleType.NEAREST_HOSTILE));
                body.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                    .filter(target -> state.escapeTarget != null && target.getTarget().currentBlockPosition()
                        .equals(net.minecraft.core.BlockPos.containing(state.escapeTarget)))
                    .ifPresent(target -> { body.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET); body.getNavigation().stop(); });
                state.escapeTarget = null;
                state.fearedPlayer = null;
                if (!body.getBrain().hasMemoryValue(MemoryModuleType.HURT_BY)
                        && !body.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_HOSTILE))
                    body.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            }
        } else if (mob instanceof IronGolem golem && !golem.isPlayerCreated() && mob.tickCount % 20 == 0) {
            var player = level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(32),
                other -> other.isAlive() && !other.isCreative() && !other.isSpectator() && score(other) == 0 && golem.canAttack(other))
                .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
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
                if (!naturality.util.LoadedChunks.has(level, pos) || !level.getFluidState(pos).isEmpty()
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
}
