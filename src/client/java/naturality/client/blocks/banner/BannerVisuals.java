package naturality.client.blocks.banner;

import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import java.util.List;
import java.util.Map;
import naturality.blocks.banner.BannerPlacement;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.banner.BannerModel;
import net.minecraft.client.renderer.blockentity.state.BannerRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import org.joml.Vector3f;

/** Keeps vanilla cloth and pattern geometry, changing only attachment and display transforms. */
@org.jspecify.annotations.NullMarked
public final class BannerVisuals {
    private static final BannerModel NO_STAND = new BannerModel(new ModelPart(List.of(), Map.of()));
    private static final ThreadLocal<ItemDisplayContext> CONTEXT = ThreadLocal.withInitial(() -> ItemDisplayContext.NONE);

    private BannerVisuals() {}

    public static ItemDisplayContext context(ItemDisplayContext value) {
        ItemDisplayContext previous = CONTEXT.get();
        CONTEXT.set(value);
        return previous;
    }

    public static BannerModel stand(BannerModel original) {
        return CONTEXT.get() == ItemDisplayContext.HEAD ? original : NO_STAND;
    }

    public static void transform(BannerBlockEntity entity, BannerRenderState state) {
        var block = entity.getBlockState();
        var level = entity.getLevel();
        if (block.getBlock() instanceof WallBannerBlock && level != null) {
            var facing = block.getValue(WallBannerBlock.FACING);
            float offset = (float)(3.0 / 32.0 + BannerPlacement.CLEARANCE
                - BannerPlacement.inset(block, level, entity.getBlockPos()));
            // Vanilla wall cloth pivots at y=-20.5, z=10.5, with its back at z=9.5.
            // Unit scale makes one texture pixel 1/16 block; top aligns to y=1.
            state.transformation = new Transformation(
                new Vector3f(0.5F + facing.getStepX() * offset, -9.0F / 32.0F, 0.5F + facing.getStepZ() * offset),
                Axis.YP.rotationDegrees(-facing.toYRot()), new Vector3f(1, -1, -1), null);
        } else {
            // Retain orientation for pre-existing standing banners without permitting new ones.
            state.transformation = new Transformation(new org.joml.Matrix4f(state.transformation.getMatrix()).scale(1.5F));
        }
    }
}
