package naturality.client.items.durability;

import com.mojang.math.Transformation;
import naturality.client.items.durability.mixins.DurabilityMixins.DurabilityModelFactory;
import naturality.client.items.durability.mixins.DurabilityMixins.DurabilityTextureSlotsAccess;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.world.item.Items;
import org.joml.Matrix4fc;

/** Bakes the broken texture through vanilla geometry generation, including hole sidewalls. */
@org.jspecify.annotations.NullMarked
public final class UselessItemRendering {
    private UselessItemRendering() {}

    public static ItemModel bake(ItemModel intact, CuboidItemModelWrapper.Unbaked unbaked,
            ItemModel.BakingContext context, Matrix4fc transformation) {
        var baker = context.blockModelBaker();
        var resolved = baker.getModel(unbaked.model());
        var originalSlots = resolved.getTopTextureSlots();
        var textures = new TextureSlots.Data.Builder();
        ((DurabilityTextureSlotsAccess)originalSlots).naturality$textures().forEach((slot, material) ->
            textures.addTexture(slot, new Material(UselessSprites.id(material.sprite()), material.forceTranslucent())));
        var brokenSlots = new TextureSlots.Resolver().addLast(textures.build()).resolve(resolved);
        // Supplying real materials before extrusion builds every new exposed pixel edge.
        var geometry = resolved.bakeTopGeometry(brokenSlots, baker, BlockModelRotation.IDENTITY);
        var properties = ModelRenderProperties.fromResolvedModel(baker, resolved, brokenSlots);
        var broken = DurabilityModelFactory.naturality$newModel(unbaked.tints(), geometry, properties,
            Transformation.compose(transformation, unbaked.transformation()));
        // Distinct baked model identities also keep GUI icon caching correct.
        return (output, stack, resolver, displayContext, level, owner, seed) -> {
            var selected = stack.isBroken() && !stack.is(Items.ELYTRA) ? broken : intact;
            selected.update(output, stack, resolver, displayContext, level, owner, seed);
        };
    }
}
