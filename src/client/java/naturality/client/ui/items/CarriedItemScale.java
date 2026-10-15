package naturality.client.ui.items;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

@org.jspecify.annotations.NullMarked
public final class CarriedItemScale {
    private CarriedItemScale() {}
    public static void draw(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y,
                                            Operation<Void> original) {
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(x + 8, y + 8);
            graphics.pose().scale(1.2F, 1.2F);
            graphics.pose().translate(-(x + 8), -(y + 8));
            original.call(graphics, stack, x, y);
        } finally { graphics.pose().popMatrix(); }
    }
}
