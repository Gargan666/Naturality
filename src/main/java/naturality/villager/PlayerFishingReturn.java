package naturality.villager;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Brief visual continuation of a player hook after vanilla calculates its catch. */
public final class PlayerFishingReturn extends Entity {
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> CATCH = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Boolean> GLINT = SynchedEntityData.defineId(PlayerFishingReturn.class, EntityDataSerializers.BOOLEAN);
    private Vec3 start = Vec3.ZERO;
    private int travelTicks;

    public PlayerFishingReturn(EntityType<PlayerFishingReturn> type, Level level) { super(type, level); }

    public PlayerFishingReturn(ServerLevel level, Player owner, Vec3 hookPosition, ItemStack catchItem) {
        this(VillagerBobbers.PLAYER_RETURN, level);
        setPos(hookPosition.x, hookPosition.y, hookPosition.z);
        start = hookPosition;
        getEntityData().set(OWNER, owner.getId());
        getEntityData().set(CATCH, catchItem.copy());
        var rod = owner.getMainHandItem().is(net.minecraft.world.item.Items.FISHING_ROD)
            ? owner.getMainHandItem() : owner.getOffhandItem();
        getEntityData().set(GLINT, rod.hasFoil());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, 0);
        builder.define(CATCH, ItemStack.EMPTY);
        builder.define(GLINT, false);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

    public @Nullable Player owner() {
        Entity entity = level().getEntity(getEntityData().get(OWNER));
        return entity instanceof Player player ? player : null;
    }
    public ItemStack catchItem() { return getEntityData().get(CATCH); }
    public boolean rodGlint() { return getEntityData().get(GLINT); }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        Player owner = owner();
        if (owner == null || !owner.isAlive()) {
            if (!catchItem().isEmpty())
                level().addFreshEntity(new ItemEntity(level(), getX(), getY(), getZ(), catchItem().copy()));
            discard();
            return;
        }
        float t = Mth.clamp(++travelTicks / 5.0F, 0, 1);
        Vec3 end = owner.getEyePosition().add(0, -0.4, 0);
        Vec3 next = start.lerp(end, t).add(0, Math.sin(t * Math.PI) * .25, 0);
        setPos(next.x, next.y, next.z);
        if (t >= 1) {
            ItemStack item = catchItem().copy();
            owner.getInventory().add(item);
            if (!item.isEmpty()) level().addFreshEntity(new ItemEntity(level(), owner.getX(), owner.getY() + .5, owner.getZ(), item));
            discard();
        }
    }
}
