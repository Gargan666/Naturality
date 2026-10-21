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
    private int left, top;
    private long puzzleId;
    private int revealed;
    private final long[] revealedAt = new long[26];
    private final float[] hoverScale = new float[26];
    private long hoverTime;
    private int start = -1, end = -1;
    private boolean dragging, moved;
    private Deciphering.@org.jspecify.annotations.Nullable Puzzle displayed;
    private Deciphering.@org.jspecify.annotations.Nullable Puzzle fading;
    private long animationStarted, failureStarted;
    private int lastBudget = 3;
    private boolean failed;

    private float secondsSince(long time) { return (System.nanoTime() - time) / 1_000_000_000f; }
    private float typingStart() { return fading == null ? 0 : Deciphering.FADE_TICKS / 20f; }

    public DecipheringPuzzle(Enchanting.Menu menu, int left, int top) {
        super(left + 20, top + 36, 120, Deciphering.ROWS * 9, Component.empty());
        this.menu = menu;
        this.left = left;
        this.top = top;
    }

    public void moveTo(int left, int top) {
        this.left = left;
        this.top = top;
        setPosition(left + 20, top + 36);
    }

    public void update() {
        var puzzle = Deciphering.puzzle(menu);
        boolean deciphering = menu.phase() == Enchanting.DECIPHER || menu.phase() == Enchanting.EMPOWERING;
        int budget = menu.subject().getOrDefault(Deciphering.ATTEMPT_BUDGET, 3);
        if (menu.phase() == Enchanting.FAILED && displayed != null && !failed) {
            failed = true;
            fading = displayed;
            failureStarted = animationStarted = System.nanoTime();
        }
        if (puzzle != null && deciphering && (displayed == null || puzzle.id() != puzzleId)) {
            boolean failure = failed || displayed != null && budget > lastBudget;
            fading = failure ? displayed : null;
            failed = failure;
            animationStarted = System.nanoTime();
            if (displayed == null && !failure) {
                // The saved deadline belongs to the puzzle, not this screen instance.
                // Reopening resumes its presentation instead of starting it over.
                long remaining = menu.subject().getOrDefault(Deciphering.READY_AT, 0L) - menu.gameTime();
                long elapsed = Deciphering.TYPE_TICKS - Math.clamp(remaining, 0L, Deciphering.TYPE_TICKS);
                animationStarted -= elapsed * 50_000_000L;
            }
            if (failure && failureStarted == 0) failureStarted = animationStarted;
            start = end = -1;
            dragging = moved = false;
            java.util.Arrays.fill(revealedAt, 0);
            java.util.Arrays.fill(hoverScale, 0);
            hoverTime = 0;
            revealed = puzzle.revealed();
            puzzleId = puzzle.id();
            displayed = puzzle;
        } else if (puzzle != null && deciphering) displayed = puzzle;
        if (!deciphering && menu.phase() != Enchanting.FAILED) {
            displayed = fading = null;
            failed = false;
            failureStarted = 0;
        }
        lastBudget = budget;
        visible = displayed != null;
        active = visible && deciphering && menu.gameTime() >= menu.subject().getOrDefault(Deciphering.READY_AT, 0L)
            && secondsSince(animationStarted) >= typingStart() + Deciphering.TYPE_TICKS / 20f;
        if (active) { failed = false; failureStarted = 0; }
    }

    private void updateReveals(Deciphering.Puzzle puzzle) {
        update();
        int added = puzzle.revealed() & ~revealed;
        for (int letter = 0; letter < 26; letter++)
            if ((added & (1 << letter)) != 0) revealedAt[letter] = System.nanoTime();
        revealed = puzzle.revealed();
    }

    public boolean canSubmit() { return active && start >= 0 && end >= 0 && moved && !dragging; }
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
        if (!active || !dragging || event.button() != 1) return false;
        end = start / Deciphering.COLUMNS * Deciphering.COLUMNS + cell(event.x(), event.y()) % Deciphering.COLUMNS;
        moved |= start != end;
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (!active || !dragging || event.button() != 1) return false;
        dragging = false;
        if (!moved) { send(false); start = end = -1; }
        return true;
    }

    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        update();
        float age = secondsSince(animationStarted);
        boolean fadingOut = fading != null && (age < typingStart() || menu.phase() == Enchanting.FAILED);
        var puzzle = fadingOut ? fading : displayed;
        if (puzzle == null) return;
        if (!fadingOut) updateReveals(puzzle);
        var font = Minecraft.getInstance().font;
        int hovered = active && isMouseOver(mouseX, mouseY) ? cell(mouseX, mouseY) : -1;
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
            float duration = (fadingOut ? Deciphering.FADE_TICKS : Deciphering.TYPE_TICKS) / 20f;
            float position = (fadingOut ? Deciphering.CELLS - 1 - i : i) / (float)Deciphering.CELLS;
            float letterFade = Math.clamp((age - (fadingOut ? 0 : typingStart()) - position * (duration - .15f)) / .15f, 0, 1);
            glyphAlpha = Math.round(glyphAlpha * (fadingOut ? 1 - letterFade : letterFade));
            if (glyphAlpha > 0) graphics.text(font, text, glyphX, -4, glyphAlpha << 24 | 0x493625, false);
            graphics.pose().popMatrix();
        }
        int shakeStep = failureStarted == 0 ? 10 : (int)(secondsSince(failureStarted) / .07f);
        int shake = shakeStep >= 10 ? 0 : (10 - shakeStep) * (shakeStep % 2 == 0 ? -1 : 1);
        graphics.pose().pushMatrix();
        graphics.pose().translate(shake, 0);
        graphics.blit(RenderPipelines.GUI_TEXTURED, Naturality.id("textures/gui/enchanting_decipher.png"),
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
        if (failed) graphics.blit(RenderPipelines.GUI_TEXTURED, Naturality.id("textures/gui/enchanting_decipher_fail.png"),
            left - 36, top + 182, 0, 0, 106, 94, 144, 178, 144, 178);
        graphics.pose().popMatrix();
    }

    @Override protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
