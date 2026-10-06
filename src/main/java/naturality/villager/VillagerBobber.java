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
        // Choose a launch direction and pitch, never solve a trajectory to a destination.
        Vec3 waterDirection = Vec3.atCenterOf(target).subtract(start);
        double yaw = waterFacingYaw(level, owner, target, waterDirection);
        double pitch = Math.toRadians(15 + owner.getRandom().nextDouble() * 35);
        double speed = .45 + owner.getRandom().nextDouble() * .2;
        setDeltaMovement(Math.cos(yaw) * Math.cos(pitch) * speed,
            Math.sin(pitch) * speed, Math.sin(yaw) * Math.cos(pitch) * speed);
        getEntityData().set(OWNER, owner.getId());
    }

    private double waterFacingYaw(ServerLevel level, Villager owner, BlockPos water, Vec3 direction) {
        double heading = Math.atan2(direction.z, direction.x);
        // Randomize within a forward cone and prefer directions crossing water.
        // This checks the direction only: flight still determines the landing point.
        for (int attempt = 0; attempt < 16; attempt++) {
            double yaw = heading + (owner.getRandom().nextDouble() - .5) * Math.toRadians(70);
            int wetSamples = 0;
            for (int distance = 2; distance <= 6; distance++) {
                var sample = BlockPos.containing(start.x + Math.cos(yaw) * distance,
                    water.getY(), start.z + Math.sin(yaw) * distance);
                if (naturality.util.LoadedChunks.has(level, sample) && ProfessionWork.restingWater(level, sample)) wetSamples++;
            }
            if (wetSamples >= 2) return yaw;
        }
        return heading;
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
            var water = blockPosition();
            if (level().getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)) {
                target = water.immutable();
                setDeltaMovement(Vec3.ZERO);
                getEntityData().set(PHASE, BOBBING);
                phaseTicks = 0;
            } else if (phaseTicks >= 80 || horizontalCollision || onGround()) {
                discard();
            }
        } else if (phase == BOBBING || phase == BITING) {
            double surface = target.getY() + .87;
            Vec3 velocity = getDeltaMovement();
            double force = getY() + velocity.y - surface;
            if (Math.abs(force) < .01) force += Math.copySign(.1, force == 0 ? 1 : force);
            double vertical = velocity.y - force * random.nextFloat() * .2;
            setDeltaMovement(0, vertical * .9, 0);
            setPos(getX(), getY() + vertical, getZ());
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
