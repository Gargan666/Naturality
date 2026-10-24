package naturality.client.ui.inventory;

import naturality.Naturality;
import naturality.ui.inventory.TrashSlot;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jspecify.annotations.NullMarked;

@NullMarked
public final class TrashSlotRendering {
    private static final Identifier PANEL = Naturality.id("textures/gui/trashslot.png");

    private TrashSlotRendering() {}

    public static void background(GuiGraphicsExtractor graphics, AbstractContainerMenu menu) {
        TrashSlot trash = TrashSlot.find(menu);
        if (trash == null || !trash.isActive()) return;
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL,
                trash.x - TrashSlot.PANEL_INSET, trash.y - TrashSlot.PANEL_INSET, 0, 0,
                TrashSlot.PANEL_SIZE, TrashSlot.PANEL_SIZE, TrashSlot.PANEL_SIZE, TrashSlot.PANEL_SIZE);
    }

    public static boolean contains(AbstractContainerMenu menu, double mouseX, double mouseY, int left, int top) {
        TrashSlot trash = TrashSlot.find(menu);
        if (trash == null || !trash.isActive()) return false;
        int x = left + trash.x - TrashSlot.PANEL_INSET;
        int y = top + trash.y - TrashSlot.PANEL_INSET;
        return mouseX >= x && mouseX < x + TrashSlot.PANEL_SIZE && mouseY >= y && mouseY < y + TrashSlot.PANEL_SIZE;
    }
}
