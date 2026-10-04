package naturality.client.villager;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.phys.Vec3;

public final class VillagerBobberRenderState extends EntityRenderState {
    public Vec3 lineOriginOffset = Vec3.ZERO;
    public final ItemStackRenderState caughtItem = new ItemStackRenderState();
    public boolean retrieving;
    public boolean glint;
    public boolean firstPersonOwner;
    public int fishingOwnerId = -1;
}
