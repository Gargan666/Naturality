package naturality.client.systems.enchanting.deciphering;

import naturality.Naturality;
import naturality.systems.enchanting.deciphering.Deciphering;
import naturality.systems.enchanting.framework.Enchanting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Page interaction is separate from the table's inventory and transaction controls. */
@org.jspecify.annotations.NullMarked
public final class DecipheringPuzzle extends AbstractWidget {
    private static final FontDescription ALT = new FontDescription.Resource(Identifier.withDefaultNamespace("alt"));
    private final Enchanting.Menu menu;
    private final int left, top;
    private long puzzleId;
    private int revealed;
    private final long[] revealedAt = new long[26];
    private final float[] hoverScale = new float[26];
    private long hoverTime;
    private int start = -1, end = -1;
    private boolean dragging, moved;

    public DecipheringPuzzle(Enchanting.Menu menu, int left, int top) {
        super(left + 20, top + 36, 120, Deciphering.ROWS * 9, Component.empty());
        this.menu = menu;
        this.left = left;
        this.top = top;
    }

    public void update() {
        var puzzle = Deciphering.puzzle(menu);
        visible = active = (menu.phase() == Enchanting.DECIPHER || menu.phase() == Enchanting.EMPOWERING) && puzzle != null;
        if (puzzle == null || puzzle.id() != puzzleId) {
            start = end = -1;
            dragging = moved = false;
            java.util.Arrays.fill(revealedAt, 0);
            java.util.Arrays.fill(hoverScale, 0);
            hoverTime = 0;
            revealed = puzzle == null ? 0 : puzzle.revealed();
            if (puzzle != null) puzzleId = puzzle.id();
        }
    }

    private void updateReveals(Deciphering.Puzzle puzzle) {
        update();
        int added = puzzle.revealed() & ~revealed;
        for (int letter = 0; letter < 26; letter++)
            if ((added & (1 << letter)) != 0) revealedAt[letter] = System.nanoTime();
        revealed = puzzle.revealed();
    }

    public boolean canSubmit() { return visible && start >= 0 && end >= 0 && moved && !dragging; }
    public void submit() { if (canSubmit()) send(true); }

    private void send(boolean submit) {
        var puzzle = Deciphering.puzzle(menu);
        if (puzzle != null) ClientPlayNetworking.send(new Deciphering.Action(
            menu.containerId, puzzle.id(), puzzle.revision(), submit, start, end));
    }

    private int cell(double x, double y) {
        return Math.clamp((int)((y - getY()) / 9), 0, Deciphering.ROWS - 1) * Deciphering.COLUMNS
            + Math.clamp((int)((x - getX()) / 6), 0, Deciphering.COLUMNS - 1);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!active || event.button() != 1 || !isMouseOver(event.x(), event.y())) return false;
        start = end = cell(event.x(), event.y());
        dragging = true;
        moved = false;
        return true;
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging || event.button() != 1) return false;
        end = start / Deciphering.COLUMNS * Deciphering.COLUMNS + cell(event.x(), event.y()) % Deciphering.COLUMNS;
        moved |= start != end;
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (!dragging || event.button() != 1) return false;
        dragging = false;
        if (!moved) { send(false); start = end = -1; }
        return true;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        var puzzle = Deciphering.puzzle(menu);
        if (puzzle == null) return;
        updateReveals(puzzle);
        var font = Minecraft.getInstance().font;
        int hovered = isMouseOver(mouseX, mouseY) ? cell(mouseX, mouseY) : -1;
        long now = System.nanoTime();
        float dt = hoverTime == 0 ? 0 : Math.min(.1f, (now - hoverTime) / 1_000_000_000f);
        hoverTime = now;
        char hoveredLetter = hovered < 0 ? ' ' : puzzle.text().charAt(hovered);
        float ease = 1f - (float)Math.exp(-16f * dt);
        for (int letter = 0; letter < 26; letter++) {
            float target = hoveredLetter == 'a' + letter ? 1f : 0f;
            hoverScale[letter] += (target - hoverScale[letter]) * ease;
        }
        for (int i = 0; i < Deciphering.CELLS; i++) {
            int x = getX() + i % Deciphering.COLUMNS * 6, y = getY() + i / Deciphering.COLUMNS * 9;
            if (start >= 0 && i >= Math.min(start, end) && i <= Math.max(start, end))
                graphics.fill(x, y - 1, x + 6, y + 8, 0x88FFFF00);
            char letter = puzzle.text().charAt(i);
            if (letter == ' ') continue;
            var text = Component.literal(String.valueOf(letter));
            if (!puzzle.visible(letter)) text.withStyle(style -> style.withFont(ALT));
            long stamp = revealedAt[letter - 'a'];
            float elapsed = stamp == 0 ? 1 : Math.clamp((System.nanoTime() - stamp) / 1_000_000_000f, 0, 1);
            float revealShrink = (1f - elapsed) * (1f - elapsed);
            float scale = .75f * (1f + .2f * hoverScale[letter - 'a'])
                * (puzzle.visible(letter) ? 1f - .2f * revealShrink : 1f);
            int backgroundAlpha = hovered == i ? 160 : Math.round(160 * (1 - elapsed));
            if (backgroundAlpha > 0) graphics.fill(x, y - 1, x + 6, y + 8, backgroundAlpha << 24 | 0xFFFF00);
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 3, y + 3);
            graphics.pose().scale(scale, scale);
            int glyphX = -font.width(text) / 2;
            int glyphAlpha = puzzle.visible(letter) ? Math.round(255 * (1f - .3f * elapsed)) : 255;
            graphics.text(font, text, glyphX, -4, glyphAlpha << 24 | 0x493625, false);
            graphics.pose().popMatrix();
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, Naturality.id("textures/gui/enchanting_library.png"),
            left - 36, top + 182, 0, 0, 106, 94, 144, 178, 144, 178);
        graphics.text(font, Component.literal("Find:"), left - 31, top + 190, 0xFF493625, false);
        graphics.pose().pushMatrix();
        graphics.pose().translate(left - 31, top + 201);
        var target = Component.literal(puzzle.target());
        float wordScale = Math.min(1.65f, 96f / Math.max(1, font.width(target)));
        graphics.pose().scale(wordScale, wordScale);
        graphics.text(font, target, 0, 0, 0xFF493625, false);
        graphics.pose().popMatrix();
        graphics.pose().pushMatrix();
        graphics.pose().translate(left - 31, top + 218);
        graphics.pose().scale(.75f, .75f);
        String instruction = puzzle.attempts() == 0
            ? "To submit a word, click, drag and highlight the word you want to attempt."
            : "Attempts left: " + puzzle.attempts();
        graphics.textWithWordWrap(font, Component.literal(instruction), 0, 0, 128, 0xFF493625, false);
        graphics.pose().popMatrix();
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
