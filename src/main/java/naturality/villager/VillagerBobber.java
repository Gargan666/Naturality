package naturality.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Server-controlled hook with vanilla-sized float and an independent entity renderer. */
public final class VillagerBobber extends Entity {
    public static final int FLYING = 0, BOBBING = 1, BITING = 2, RETRIEVING = 3;
    private static final EntityDataAccessor<Integer> OWNER = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<ItemStack> CATCH = SynchedEntityData.defineId(VillagerBobber.class, EntityDataSerializers.ITEM_STACK);
    private BlockPos target = BlockPos.ZERO;
    private Vec3 start = Vec3.ZERO;
    private Vec3 retrievalStart = Vec3.ZERO;
    private int phaseTicks;

    public VillagerBobber(EntityType<VillagerBobber> type, Level level) { super(type, level); }
    public VillagerBobber(ServerLevel level, Villager owner, BlockPos target) {
        this(VillagerBobbers.TYPE, level);
        this.target = target.immutable();
        this.start = owner.getEyePosition().add(owner.getLookAngle().scale(0.35)).add(0, -0.4, 0);
        setPos(start.x, start.y, start.z);
        Vec3 end = Vec3.atCenterOf(target).add(0, 0.37, 0);
        double drag = 0.92;
        int flightTicks = 12;
        double travel = (1 - Math.pow(drag, flightTicks)) / (1 - drag);
        double gravityLoss = 0.03 / (1 - drag) * (flightTicks - travel);
        Vec3 distance = end.subtract(start);
        setDeltaMovement(distance.x / travel, (distance.y + gravityLoss) / travel, distance.z / travel);
        getEntityData().set(OWNER, owner.getId());
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(OWNER, 0);
        builder.define(PHASE, FLYING);
        builder.define(CATCH, ItemStack.EMPTY);
    }
    @Override protected void readAdditionalSaveData(ValueInput input) { }
    @Override protected void addAdditionalSaveData(ValueOutput output) { }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 4096.0; }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) { return false; }

    public @Nullable Villager owner() {
        Entity entity = level().getEntity(getEntityData().get(OWNER));
        return entity instanceof Villager villager ? villager : null;
    }
    public int phase() { return getEntityData().get(PHASE); }
    public ItemStack catchItem() { return getEntityData().get(CATCH); }
    public void bite(ItemStack item) {
        getEntityData().set(CATCH, item.copyWithCount(1));
        getEntityData().set(PHASE, BITING);
        phaseTicks = 0;
        setDeltaMovement(getDeltaMovement().x, -0.35, getDeltaMovement().z);
    }
    public void retrieve() {
        getEntityData().set(PHASE, RETRIEVING);
        retrievalStart = position();
        phaseTicks = 0;
    }

    @Override public void tick() {
        super.tick();
        if (level().isClientSide()) return;
        Villager owner = owner();
        if (owner == null || !owner.isAlive()) { discard(); return; }
        int phase = phase();
        phaseTicks++;
        if (phase == FLYING) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.92).add(0, -0.03, 0));
            if (phaseTicks >= 12 || horizontalCollision) {
                setPos(target.getX() + .5, target.getY() + .87, target.getZ() + .5);
                setDeltaMovement(Vec3.ZERO);
                getEntityData().set(PHASE, BOBBING);
                phaseTicks = 0;
            }
        } else if (phase == BOBBING || phase == BITING) {
            double surface = target.getY() + .87;
            Vec3 velocity = getDeltaMovement();
            double force = getY() + velocity.y - surface;
            if (Math.abs(force) < .01) force += Math.copySign(.1, force == 0 ? 1 : force);
            double vertical = velocity.y - force * random.nextFloat() * .2;
            setDeltaMovement(0, vertical * .9, 0);
            setPos(target.getX() + .5, getY() + vertical, target.getZ() + .5);
        } else if (phase == RETRIEVING) {
            float t = Mth.clamp(phaseTicks / 5.0F, 0, 1);
            Vec3 destination = owner.getEyePosition().add(0, -0.4, 0);
            Vec3 next = retrievalStart.lerp(destination, t).add(0, Math.sin(t * Math.PI) * 0.4, 0);
            setPos(next.x, next.y, next.z);
            if (t >= 1) {
                discard();
            }
        }
    }
}
