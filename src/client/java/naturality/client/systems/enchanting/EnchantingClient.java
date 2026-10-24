package naturality.client.systems.enchanting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import naturality.Naturality;
import naturality.client.systems.enchanting.deciphering.DecipheringPuzzle;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.Items;
import java.util.Optional;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.EnchantmentTags;

/** Temporary controls exercise the real saved transactions until puzzles are designed. */
@org.jspecify.annotations.NullMarked
public final class EnchantingClient {
    private EnchantingClient() {}
    public static void initialize() {
        naturality.client.systems.enchanting.bookshelves.BookshelfRendering.initialize();
        MenuScreens.register(Enchanting.MENU, Screen::new);
        ClientPlayNetworking.registerGlobalReceiver(naturality.systems.enchanting.deciphering.Deciphering.Words.TYPE,
            (payload, _) -> naturality.systems.enchanting.deciphering.Deciphering.receiveWords(payload));
        ClientPlayNetworking.registerGlobalReceiver(Enchanting.LibraryPayload.TYPE, (payload, context) -> {
            var player = context.client().player;
            if (player != null && player.containerMenu instanceof Enchanting.Menu menu) menu.receiveLibrary(payload);
        });
    }

    public interface TableAudioAccess {
        TableAudio naturality$tableAudio();
    }

    /** Positional overlapping hum clips and page audio, independent of inventory or menu state. */
    public static final class TableAudio {
        private final List<HumLoop> hums = new ArrayList<>();
        private int pageSoundCooldown;

        public void tick(net.minecraft.world.level.block.entity.EnchantingTableBlockEntity table) {
            var client = Minecraft.getInstance();
            if (pageSoundCooldown > 0) pageSoundCooldown--;
            var level = table.getLevel();
            // Sound at the midpoint of each page turn, away from the integer
            // targets where the animation eases to a stop.
            if (level != null && audible(client, table) && table.open >= .5f && pageSoundCooldown == 0
                    && Math.abs(table.flip - table.oFlip) > .01f
                    && Math.floor(table.flip + .5f) != Math.floor(table.oFlip + .5f)) {
                var position = table.getBlockPos();
                level.playLocalSound(position.getX() + .5, position.getY() + 1, position.getZ() + .5,
                    Enchanting.PAGE_FLIP_SOUND, net.minecraft.sounds.SoundSource.BLOCKS,
                    .5f, .95f + level.getRandom().nextFloat() * .1f, false);
                pageSoundCooldown = 6;
            }
            if (!audible(client, table)) {
                for (var sound : hums) client.getSoundManager().stop(sound);
                hums.clear();
            } else {
                hums.removeIf(sound -> sound.isStopped() || sound.age > 5 && !client.getSoundManager().isActive(sound));
                if (hums.isEmpty() || hums.getLast().age >= HumLoop.DURATION - HumLoop.FADE) {
                    var started = new HumLoop(table);
                    hums.add(started);
                    client.getSoundManager().play(started);
                }
                float total = 0;
                for (var sound : hums) total += sound.envelope();
                float pulse = .8f + .2f * (float)Math.cos(table.time * (Math.PI * 2 / 240));
                for (var sound : hums) sound.setGain(.5f * pulse / Math.max(1f, total));
            }
        }

        private static boolean audible(Minecraft client, net.minecraft.world.level.block.entity.EnchantingTableBlockEntity table) {
            var level = client.level;
            var camera = client.getCameraEntity();
            return level != null && camera != null && table.getLevel() == level && !table.isRemoved()
                && camera.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(table.getBlockPos())) < 16 * 16
                && naturality.api.worlds.LoadedChunks.has(level, table.getBlockPos()) && level.getBlockEntity(table.getBlockPos()) == table;
        }

        private static final class HumLoop extends net.minecraft.client.resources.sounds.AbstractTickableSoundInstance {
            // Supplied hum.ogg: 1,814,400 samples at 44,100 Hz. End just before its physical seam.
            private static final int DURATION = 822, FADE = 40;
            private final net.minecraft.world.level.block.entity.EnchantingTableBlockEntity table;
            private int age;

            HumLoop(net.minecraft.world.level.block.entity.EnchantingTableBlockEntity table) {
                super(Enchanting.HUM_SOUND, net.minecraft.sounds.SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
                this.table = table;
                looping = false;
                delay = 0;
                volume = 0;
                pitch = 1f;
                relative = false;
                attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
                x = table.getBlockPos().getX() + .5;
                y = table.getBlockPos().getY() + .5;
                z = table.getBlockPos().getZ() + .5;
            }

            @Override public boolean canStartSilent() { return true; }
            private float envelope() { return Math.max(0, Math.min(1f, Math.min(age, DURATION - age) / (float)FADE)); }
            private void setGain(float gain) { volume = gain * envelope(); }
            @Override public void tick() {
                if (!audible(Minecraft.getInstance(), table) || ++age >= DURATION) { stop(); return; }
            }
        }
    }

    public static Component label(Holder<Enchantment> enchantment, int level) {
        return label(enchantment, level, false);
    }

    public static Component label(Holder<Enchantment> enchantment, int level, boolean encrypted) {
        var name = encrypted
            ? enchantment.value().description().copy().withStyle(s -> s.withObfuscated(true))
                .withStyle(enchantment.is(EnchantmentTags.CURSE) ? ChatFormatting.RED : ChatFormatting.GRAY)
            : Enchantment.getFullname(enchantment, level).copy();
        var result = name.copy();
        if (encrypted && (level != 1 || enchantment.value().getMaxLevel() != 1)) {
            result.append(Component.literal(" ").append(Component.translatable("enchantment.level." + level))
                .withStyle(s -> s.withObfuscated(false)));
        }
        if (!encrypted && Enchanting.deadly(enchantment)) result.append(Component.literal(" [Deadly]")
            .withStyle(ChatFormatting.DARK_RED));
        return result;
    }

    public static Component description(Holder<Enchantment> enchantment, int level) {
        String id = Enchanting.name(enchantment);
        String key = "enchantment." + id.replace(':', '.') + ".desc";
        String text = Component.translatableWithFallback(key, "No description available.").getString();
        // Resolve the current language on each request, so resource reloads pick up edited wording.
        return Component.literal(DescriptionFormula.expand(text, level));
    }

    /** Small arithmetic grammar for authored descriptions; never executes scripts or game code. */
    private static final class DescriptionFormula {
        private static final Pattern FORMULA = Pattern.compile("\\{([^{}]*)\\}");
        private static final MathContext PRECISION = MathContext.DECIMAL64;
        private final String expression;
        private final BigDecimal level;
        private int position;

        private DescriptionFormula(String expression, int level) {
            this.expression = expression;
            this.level = BigDecimal.valueOf(level);
        }

        static String expand(String text, int level) {
            return FORMULA.matcher(text).replaceAll(match -> {
                String replacement = match.group();
                String expression = match.group(1);
                // Bounds both arithmetic work and recursion from nested parentheses/signs.
                if (expression.length() <= 256) {
                    try {
                        var parser = new DescriptionFormula(expression, level);
                        BigDecimal value = parser.sum();
                        parser.whitespace();
                        if (parser.position == expression.length())
                            replacement = value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                    } catch (IllegalArgumentException | ArithmeticException ignored) {
                        // Keep authoring mistakes (including division by zero) visible in the tooltip.
                    }
                }
                return Matcher.quoteReplacement(replacement);
            });
        }

        private BigDecimal sum() {
            BigDecimal value = product();
            while (true) {
                if (take('+')) value = value.add(product(), PRECISION);
                else if (take('-')) value = value.subtract(product(), PRECISION);
                else return value;
            }
        }

        private BigDecimal product() {
            BigDecimal value = factor();
            while (true) {
                if (take('*') || take('x') || take('×')) value = value.multiply(factor(), PRECISION);
                else if (take('/')) value = value.divide(factor(), PRECISION);
                else return value;
            }
        }

        private BigDecimal factor() {
            if (take('+')) return factor();
            if (take('-')) return factor().negate();
            if (take('(')) {
                BigDecimal value = sum();
                if (!take(')')) throw new IllegalArgumentException("Missing closing parenthesis");
                return value;
            }
            if (expression.startsWith("level", position)) {
                position += "level".length();
                return level;
            }
            int start = position;
            while (position < expression.length()) {
                char character = expression.charAt(position);
                if ((character < '0' || character > '9') && character != '.') break;
                position++;
            }
            if (start == position) throw new IllegalArgumentException("Expected number or level");
            return new BigDecimal(expression.substring(start, position), PRECISION);
        }

        private boolean take(char token) {
            whitespace();
            if (position >= expression.length() || expression.charAt(position) != token) return false;
            position++;
            return true;
        }

        private void whitespace() {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position))) position++;
        }
    }

    private record Entry(Holder<Enchantment> enchantment, int level, boolean encrypted) {
        ChatFormatting tint() { return enchantment.is(EnchantmentTags.CURSE) ? ChatFormatting.RED : ChatFormatting.GRAY; }
        Component title() {
            return enchantment.value().description().copy()
                .withStyle(tint())
                .withStyle(s -> s.withObfuscated(encrypted));
        }
        List<Component> details() {
            if (encrypted) return List.of();
            Component description = EnchantingClient.description(enchantment, level);
            List<Component> details = new ArrayList<>();
            if (enchantment.value().getMaxLevel() > 1)
                details.add(Component.translatable("tooltip.naturality.enchantment.level", Component.translatable("enchantment.level." + level)));
            if (Enchanting.deadly(enchantment))
                details.add(Component.translatable("tooltip.naturality.enchantment.deadly"));
            details.add(description);
            return details;
        }
    }

    public static Optional<TooltipComponent> tooltip(ItemStack item, Optional<TooltipComponent> original) {
        var display = item.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT);
        if (display.hideTooltip()) return original;
        List<Entry> entries = new ArrayList<>();
        for (var type : List.of(DataComponents.STORED_ENCHANTMENTS, DataComponents.ENCHANTMENTS)) {
            if (!display.shows(type)) continue;
            entries.addAll(tooltipEntries(item, type));
        }
        return entries.isEmpty() ? original : Optional.of(new EnchantmentTooltip(List.copyOf(entries), original.map(ClientTooltipComponent::create)));
    }

    private static List<Entry> tooltipEntries(ItemStack item, DataComponentType<ItemEnchantments> type) {
        var preview = type == DataComponents.STORED_ENCHANTMENTS ? bookPreview(item) : item;
        boolean encrypted = preview.is(Items.ENCHANTED_BOOK) && !preview.getOrDefault(Enchanting.REVEALED, false);
        var enchantments = preview.getOrDefault(type, ItemEnchantments.EMPTY);
        List<Entry> entries = new ArrayList<>();
        var paired = new HashSet<Holder<Enchantment>>();
        // Pair data belongs to stored enchantments on books, and applied enchantments on equipment.
        if ((type == DataComponents.STORED_ENCHANTMENTS) == preview.is(Items.ENCHANTED_BOOK)) {
            List<Enchanting.Pair> pairs = preview.getOrDefault(Enchanting.PAIRS, List.of());
            for (var pair : pairs) {
                if (enchantments.getLevel(pair.benefit()) <= 0) continue;
                entries.add(new Entry(pair.benefit(), pair.level(), encrypted));
                // Repeated curses appear under each benefit with that pair's contribution, not the total.
                if (pair.curseLevel() > 0) entries.add(new Entry(pair.curse(), pair.curseLevel(), encrypted));
                paired.add(pair.benefit());
                if (pair.curseLevel() > 0) paired.add(pair.curse());
            }
        }
        // Creative and legacy unpaired entries remain visible without inventing relationships.
        enchantments.entrySet().stream().filter(e -> !paired.contains(e.getKey()))
            .sorted(java.util.Comparator.comparing(e -> Enchanting.name(e.getKey())))
            .forEach(e -> entries.add(new Entry(e.getKey(), e.getIntValue(), encrypted)));
        return entries;
    }

    public static List<Component> tooltipLabels(ItemStack item, DataComponentType<ItemEnchantments> type) {
        return tooltipEntries(item, type).stream()
            .map(entry -> entry.encrypted() ? entry.title() : label(entry.enchantment(), entry.level())).toList();
    }

    /** Native tooltip component gives each icon real space instead of stretching a font glyph. */
    public record EnchantmentTooltip(List<Entry> entries, Optional<ClientTooltipComponent> original)
            implements TooltipComponent, ClientTooltipComponent {
        private static final float DETAIL_SCALE = .75F;
        private static final int INSET = 20;
        private static final int DETAIL_WIDTH = 240;

        private List<net.minecraft.util.FormattedCharSequence> lines(Entry entry, Font font) {
            List<net.minecraft.util.FormattedCharSequence> result = new ArrayList<>();
            for (var detail : entry.details())
                result.addAll(font.split(Component.literal("• ").append(detail).withStyle(entry.tint()), DETAIL_WIDTH));
            return result;
        }
        private int detailTop(Font font) { return (16 - font.lineHeight) / 2 + font.lineHeight + 1; }
        private int entryHeight(Entry entry, Font font) { return Math.max(16, detailTop(font) + lines(entry, font).size() * 8) + 2; }
        private int entriesHeight(Font font) { return entries.stream().mapToInt(e -> entryHeight(e, font)).sum(); }
        @Override public int getHeight(Font font) { return entriesHeight(font) + original.map(c -> c.getHeight(font)).orElse(0); }
        @Override public int getWidth(Font font) {
            int width = original.map(c -> c.getWidth(font)).orElse(0);
            for (var entry : entries) {
                width = Math.max(width, INSET + font.width(entry.title()));
                for (var line : lines(entry, font)) width = Math.max(width, INSET + (int)Math.ceil(font.width(line) * DETAIL_SCALE));
            }
            return width;
        }
        @Override public boolean showTooltipWithItemInHand() { return original.map(ClientTooltipComponent::showTooltipWithItemInHand).orElse(false); }
        @Override public void extractText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
            int top = y;
            for (var entry : entries) {
                graphics.text(font, entry.title(), x + INSET, top + (16 - font.lineHeight) / 2, -1);
                graphics.pose().pushMatrix();
                graphics.pose().translate(x + INSET, top + detailTop(font));
                graphics.pose().scale(DETAIL_SCALE, DETAIL_SCALE);
                int row = 0;
                for (var line : lines(entry, font)) {
                    graphics.text(font, line, 0, Math.round(row * 8 / DETAIL_SCALE), -1);
                    row++;
                }
                graphics.pose().popMatrix();
                top += entryHeight(entry, font);
            }
            int originalY = top;
            original.ifPresent(c -> c.extractText(graphics, font, x, originalY));
        }
        @Override public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
            int top = y;
            for (var entry : entries) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, EnchantmentIcons.texture(entry.enchantment(), entry.encrypted()),
                    x, top, 0, 0, 16, 16, 16, 16);
                top += entryHeight(entry, font);
            }
            int originalY = top;
            original.ifPresent(c -> c.extractImage(font, x, originalY, width, height, graphics));
        }
    }

    public static ItemEnchantments bookTooltip(ItemStack book) {
        return bookPreview(book).getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    private static ItemStack bookPreview(ItemStack book) {
        // A legacy chest book may not have reached the server's inventory migration yet.
        // Preview the same deterministic pair without changing the synchronized stack.
        var level = Minecraft.getInstance().level;
        if (book.is(Items.ENCHANTED_BOOK) && level != null && !Enchanting.creativeBook(book)) {
            var preview = book.copy();
            Enchanting.normalizeBook(preview, level.registryAccess());
            return preview;
        }
        return book;
    }

    public static String clue(Holder<Enchantment> enchantment) {
        return naturality.systems.enchanting.deciphering.Deciphering.clue(enchantment);
    }

    public static final class Screen extends AbstractContainerScreen<Enchanting.Menu> {
        private static final net.minecraft.resources.Identifier BACKGROUND = Naturality.id("textures/gui/enchanting.png");
        private static final net.minecraft.resources.Identifier LIBRARY_BACKGROUND = Naturality.id("textures/gui/enchanting_library.png");
        private static final net.minecraft.resources.Identifier LIBRARY_TAB = Naturality.id("textures/gui/enchanting_library_tab.png");
        private static final net.minecraft.resources.Identifier LIBRARY_TAB_OPEN = Naturality.id("textures/gui/enchanting_library_tab_open.png");
        private static final net.minecraft.resources.Identifier LIBRARY_EMPTY = Naturality.id("textures/gui/enchanting_library_empty.png");
        private static final int LIBRARY_WIDTH = 144, LIBRARY_HEIGHT = 178, LIBRARY_ROWS = 4;
        private static final int INK = 0xFF493625;
        private static final int LEFT_TEXT = 20, RIGHT_TEXT = 176, TEXT_WIDTH = 124;
        private final List<Control> controls = new ArrayList<>();
        private final List<LibraryRow> libraryRows = new ArrayList<>();
        private @org.jspecify.annotations.Nullable DecipheringPuzzle deciphering;
        private @org.jspecify.annotations.Nullable Button submitPuzzle;
        private net.minecraft.client.gui.components.@org.jspecify.annotations.Nullable EditBox inscription;
        private @org.jspecify.annotations.Nullable Button confirmInscription;
        private final ImbuingSilhouette silhouette = new ImbuingSilhouette();
        private long writingAttempt;
        private boolean writingVisible;
        // Keep this player-local UI preference across screen instances during this client session.
        private static boolean rememberedLibraryOpen;
        private boolean libraryOpen = rememberedLibraryOpen;
        private int libraryPage;
        private float layoutScale = 1f;
        private record Control(Button button, int action) {}
        public Screen(Enchanting.Menu menu, Inventory inventory, Component title) {
            super(menu, inventory, title, Enchanting.Menu.WIDTH, Enchanting.Menu.HEIGHT);
            inventoryLabelX = Enchanting.Menu.INVENTORY_X;
            inventoryLabelY = Enchanting.Menu.INVENTORY_Y - 11;
        }
        @Override protected void init() {
            super.init();
            // Include the bookmark, clue tab (and its failure shake), and inventory.
            int leftExtension = libraryOpen ? LIBRARY_WIDTH : 46;
            int fullWidth = imageWidth + leftExtension;
            int fullHeight = imageHeight + 20;
            layoutScale = Math.min(1f, Math.min(Math.max(1, width - 8) / (float)fullWidth,
                Math.max(1, height - 8) / (float)fullHeight));
            leftPos = leftExtension + (int)((width / layoutScale - fullWidth) / 2);
            topPos = 20 + (int)((height / layoutScale - fullHeight) / 2);
            controls.clear();
            libraryRows.clear();
            var bookmark = new Button(leftPos + 20, topPos - 20, 24, 20, Component.literal("Library"), _ -> {
                libraryOpen = !libraryOpen;
                rememberedLibraryOpen = libraryOpen;
                rebuildWidgets();
            }, narration -> narration.get()) {
                // The full bookmark is drawn behind the book in extractBackground.
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}
            };
            bookmark.setTooltip(Tooltip.create(Component.literal("Library")));
            addRenderableWidget(bookmark);
            if (libraryOpen) {
                for (int row = 0; row < LIBRARY_ROWS; row++) {
                    var button = new LibraryRow(row);
                    libraryRows.add(button);
                    addRenderableWidget(button);
                }
                addRenderableWidget(Button.builder(Component.literal("<"), _ -> changeLibraryPage(-1))
                    .bounds(leftPos - LIBRARY_WIDTH + 8, topPos + 160, 20, 14).build());
                addRenderableWidget(Button.builder(Component.literal(">"), _ -> changeLibraryPage(1))
                    .bounds(leftPos - 28, topPos + 160, 20, 14).build());
            }
            button("Retry", Enchanting.RETRY);
            button("Empower", Enchanting.EMPOWER);
            button("Imbue", Enchanting.IMBUE);
            button("Decline", Enchanting.DECLINE);
            button("Next enchantment", Enchanting.NEXT);
            button("Reroll", Enchanting.REROLL);
            var page = deciphering;
            if (page == null) page = new DecipheringPuzzle(menu, leftPos, topPos);
            else page.moveTo(leftPos, topPos);
            deciphering = addRenderableWidget(page);
            var puzzlePage = page;
            submitPuzzle = addRenderableWidget(Button.builder(Component.literal("Submit"), _ -> puzzlePage.submit())
                .bounds(leftPos - 14, topPos + 254, 62, 16).build());
            var input = inscription;
            if (input == null) input = new net.minecraft.client.gui.components.EditBox(font, leftPos + LEFT_TEXT, topPos + 134,
                TEXT_WIDTH, 16, Component.literal("Imbuement"));
            else input.setPosition(leftPos + LEFT_TEXT, topPos + 134);
            input.setMaxLength(256);
            inscription = addRenderableWidget(input);
            var inscriptionInput = input;
            input.setResponder(value -> {
                inscriptionInput.setSuggestion(value.isEmpty() ? menu.inscriptionTarget() : null);
                sendInscription(false);
            });
            confirmInscription = addRenderableWidget(Button.builder(Component.literal("Confirm"), _ -> sendInscription(true))
                .bounds(leftPos + LEFT_TEXT, topPos + 153, TEXT_WIDTH, 16).build());
            updateControls();
        }
        private void sendInscription(boolean confirm) {
            var input = inscription;
            if (input == null || !writingVisible || menu.phase() != Enchanting.WRITING) return;
            ClientPlayNetworking.send(new Enchanting.Inscription(menu.containerId, writingAttempt, input.getValue(), confirm));
        }
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
            updateControls();
            if (writingVisible) {
                if (event.key() == 257 || event.key() == 335) { sendInscription(true); return true; }
                var input = inscription;
                if (input != null && input.isFocused() && event.key() != 256) {
                    input.keyPressed(event);
                    return true;
                }
            }
            return super.keyPressed(event);
        }
        private void button(String text, int action) {
            var button = new Button(leftPos + RIGHT_TEXT, topPos + 96, TEXT_WIDTH, 20, Component.literal(text), _ -> {
                var gameMode = minecraft.gameMode;
                if (gameMode != null) gameMode.handleInventoryButtonClick(menu.containerId, action);
            }, narration -> narration.get()) {
                @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                    extractDefaultSprite(graphics);
                    if (action != Enchanting.EMPOWER && action != Enchanting.IMBUE) {
                        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
                        return;
                    }
                    var label = Component.literal(text);
                    boolean lapis = action == Enchanting.IMBUE;
                    var cost = Component.literal(Integer.toString(lapis ? Enchanting.WRITING_LAPIS : menu.empowerCost()));
                    int color = active ? 0xFFFFFFFF : 0xFFFF5555;
                    if (!active) { label.withStyle(ChatFormatting.STRIKETHROUGH); cost.withStyle(ChatFormatting.STRIKETHROUGH); }
                    int total = font.width(label) + 8 + font.width(cost) + (lapis ? 18 : 10);
                    int x = getX() + (getWidth() - total) / 2, y = getY() + (getHeight() - 9) / 2;
                    graphics.text(font, label, x, y, color, true);
                    x += font.width(label) + 8;
                    graphics.text(font, cost, x, y, active ? (lapis ? 0xFF5555FF : 0xFF80FF20) : color, true);
                    if (lapis) {
                        graphics.item(new ItemStack(Items.LAPIS_LAZULI), x + font.width(cost) + 2, getY() + 2);
                        return;
                    }
                    // Reuse only the orb portion of vanilla's level sprite, so arbitrary costs are supported.
                    graphics.blit(RenderPipelines.GUI_TEXTURED,
                        net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/sprites/container/enchanting_table/level_1.png"),
                        x + font.width(cost) + 2, y, 3, 3, 8, 9, 8, 9, 16, 16);
                }
            };
            addRenderableWidget(button);
            controls.add(new Control(button, action));
        }
        @Override public void containerTick() { super.containerTick(); updateControls(); }
        @Override public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            // Slot/payload updates may arrive between ticks. Widgets and labels must
            // consume the same state before any of this frame's widgets are extracted.
            updateControls();
            graphics.pose().pushMatrix();
            graphics.pose().scale(layoutScale, layoutScale);
            super.extractContents(graphics, (int)(mouseX / layoutScale), (int)(mouseY / layoutScale), partialTick);
            graphics.pose().popMatrix();
        }
        @Override public void extractCarriedItem(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            graphics.pose().pushMatrix();
            graphics.pose().scale(layoutScale, layoutScale);
            super.extractCarriedItem(graphics, (int)(mouseX / layoutScale), (int)(mouseY / layoutScale));
            graphics.pose().popMatrix();
        }
        private net.minecraft.client.input.MouseButtonEvent layoutEvent(net.minecraft.client.input.MouseButtonEvent event) {
            return new net.minecraft.client.input.MouseButtonEvent(event.x() / layoutScale, event.y() / layoutScale, event.buttonInfo());
        }
        @Override public void mouseMoved(double x, double y) {
            super.mouseMoved(x / layoutScale, y / layoutScale);
        }
        @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
            updateControls();
            return super.mouseClicked(layoutEvent(event), doubleClick);
        }
        private void updateControls() {
            libraryPage = Math.clamp(libraryPage, 0, Math.max(0, (menu.library().size() - 1) / LIBRARY_ROWS));
            for (var row : libraryRows) row.update();
            int phase = menu.phase();
            var input = inscription;
            var confirm = confirmInscription;
            boolean writing = phase == Enchanting.WRITING;
            if (input != null && confirm != null) {
                long attempt = menu.subject().getOrDefault(Enchanting.WRITING_ID, 0L);
                if (writing && (!writingVisible || writingAttempt != attempt)) {
                    writingVisible = false;
                    writingAttempt = attempt;
                    input.setValue(menu.subject().getOrDefault(Enchanting.WRITING_DRAFT, ""));
                    setFocused(input);
                }
                input.visible = writing;
                input.active = writing;
                // Vanilla hints disappear on focus; suggestions keep the preview visible while typing can begin.
                input.setSuggestion(writing && input.getValue().isEmpty() ? menu.inscriptionTarget() : null);
                confirm.visible = writing;
                writingVisible = writing;
            }
            var page = deciphering;
            var submit = submitPuzzle;
            if (page != null && submit != null) {
                page.update();
                submit.visible = page.visible && page.canSubmit();
                submit.active = page.canSubmit();
            }
            for (var control : controls) {
                control.button().visible = switch (control.action()) {
                    case Enchanting.RETRY -> phase == Enchanting.FAILED;
                    case Enchanting.EMPOWER -> phase == Enchanting.READY && menu.canEmpower();
                    case Enchanting.IMBUE -> phase == Enchanting.READY && !menu.book();
                    case Enchanting.DECLINE -> phase == Enchanting.READY && !menu.book();
                    case Enchanting.NEXT -> phase == Enchanting.COMPLETE && menu.hasFreeSlot() && !menu.book();
                    case Enchanting.REROLL -> menu.canReroll();
                    default -> false;
                };
                control.button().active = control.button().visible;
                if (control.action() == Enchanting.EMPOWER) {
                    control.button().setMessage(Component.literal("Empower " + menu.empowerCost()));
                    control.button().active = control.button().visible && menu.canAffordLevels(menu.empowerCost());
                }
                if (control.action() == Enchanting.IMBUE) control.button().setMessage(Component.literal("Imbue " + Enchanting.WRITING_LAPIS));
            }
            int visible = (int)controls.stream().filter(control -> control.button().visible).count();
            int row = 0;
            for (var control : controls) {
                if (control.button().visible) control.button().setPosition(leftPos + RIGHT_TEXT,
                    topPos + 96 - (visible - 1 - row++) * 22);
            }
        }
        @Override public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
            graphics.pose().pushMatrix();
            graphics.pose().scale(layoutScale, layoutScale);
            graphics.blit(RenderPipelines.GUI_TEXTURED, libraryOpen ? LIBRARY_TAB_OPEN : LIBRARY_TAB,
                leftPos + 20, topPos - 20, 0, 0, 24, 32, 24, 32);
            if (libraryOpen) graphics.blit(RenderPipelines.GUI_TEXTURED, LIBRARY_BACKGROUND,
                leftPos - LIBRARY_WIDTH, topPos, 0, 0, LIBRARY_WIDTH, LIBRARY_HEIGHT, LIBRARY_WIDTH, LIBRARY_HEIGHT);
            graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0, 0,
                imageWidth, imageHeight, Enchanting.Menu.WIDTH, Enchanting.Menu.HEIGHT);
            graphics.pose().popMatrix();
        }
        @Override protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (libraryOpen) {
                text(graphics, "Library", -LIBRARY_WIDTH / 2 - font.width("Library") / 2, 12);
                if (menu.library().isEmpty()) {
                    var message = Component.literal("Your library is empty. Insert enchanted books into Chiseled Bookshelves around your enchantment table to begin your library.");
                    graphics.blit(RenderPipelines.GUI_TEXTURED, LIBRARY_EMPTY,
                        -LIBRARY_WIDTH + 6, 24, 0, 0, 132, 66, 132, 66);
                    graphics.textWithWordWrap(font, message, -LIBRARY_WIDTH + 12, 30, LIBRARY_WIDTH - 24, INK, false);
                }
                String page = (libraryPage + 1) + "/" + Math.max(1, (menu.library().size() + LIBRARY_ROWS - 1) / LIBRARY_ROWS);
                text(graphics, page, -LIBRARY_WIDTH / 2 - font.width(page) / 2, 163);
            }
            graphics.text(font, title, LEFT_TEXT, 16, INK, false);
            text(graphics, "Info", RIGHT_TEXT, 16);
            if (Enchanting.creativeBook(menu.subject()) && menu.phase() == Enchanting.READY) {
                var entries = bookTooltip(menu.subject()).entrySet().stream()
                    .map(entry -> new Entry(entry.getKey(), entry.getIntValue(), false)).toList();
                enchantmentDetails(graphics, entries);
                return;
            }
            var work = menu.work();
            if (work == null) {
                paragraph(graphics, "Insert an item.", LEFT_TEXT, 36);
                return;
            }
            String state = switch (work.phase()) {
                case Enchanting.DECIPHER -> "Decipher the inscription";
                case Enchanting.WRITING -> "Imbuing - lapis spent";
                case Enchanting.EMPOWERING -> "Empower - reward at risk";
                case Enchanting.READY -> "Inscription deciphered";
                case Enchanting.COMPLETE -> "Enchantment applied";
                default -> work.offer().isPresent() || !menu.pendingSingle().isEmpty() ? "Decipher the inscription" : "Pending enchantment lost";
            };
            if (work.phase() == Enchanting.WRITING) {
                graphics.textWithWordWrap(font, Component.literal(menu.inscriptionTarget()), RIGHT_TEXT, 40, TEXT_WIDTH, INK, false);
                // GUI item rendering uses the actual item model and its current texture.
                graphics.pose().pushMatrix();
                graphics.pose().translate(LEFT_TEXT + 14, 31);
                graphics.pose().scale(6f, 6f);
                graphics.item(menu.subject(), 0, 0);
                graphics.pose().popMatrix();
                var input = inscription;
                if (input != null) {
                    graphics.nextStratum();
                    var inscriptionText = Component.literal(input.getValue().toLowerCase(java.util.Locale.ROOT))
                        .withStyle(style -> style.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.withDefaultNamespace("alt"))));
                    var line = silhouette.fit(font.width(inscriptionText), font.lineHeight);
                    if (line != null) {
                        graphics.pose().pushMatrix();
                        graphics.pose().translate(LEFT_TEXT + 14 + line.x(), 31 + line.y());
                        graphics.pose().rotate(line.angle());
                        graphics.pose().scale(line.scale(), line.scale());
                        graphics.text(font, inscriptionText, -font.width(inscriptionText) / 2, -font.lineHeight / 2,
                            0xFF000000 | net.minecraft.world.item.DyeColor.BLUE.getTextColor(), false);
                        graphics.pose().popMatrix();
                    }
                }
                return;
            }
            boolean deciphering = work.phase() == Enchanting.DECIPHER || work.phase() == Enchanting.EMPOWERING;
            if (!deciphering && work.phase() == Enchanting.FAILED && (this.deciphering == null || !this.deciphering.visible))
                paragraph(graphics, state, LEFT_TEXT, 36);
            if (!deciphering && work.phase() != Enchanting.FAILED) {
                List<Entry> entries = new ArrayList<>();
                work.offer().ifPresent(p -> {
                    entries.add(new Entry(p.benefit(), p.level(), false));
                    if (p.curseLevel() > 0) entries.add(new Entry(p.curse(), p.curseLevel(), false));
                });
                for (var entry : menu.pendingSingle().entrySet()) entries.add(new Entry(entry.getKey(), entry.getIntValue(), false));
                enchantmentDetails(graphics, entries);
            }
        }
        public void captureInscriptionItem(net.minecraft.client.renderer.state.gui.GuiItemRenderState item,
                net.minecraft.client.gui.render.GuiItemAtlas.SlotView slot) {
            if (menu.phase() == Enchanting.WRITING && Math.abs(item.pose().m00()) >= 5.9f * layoutScale)
                silhouette.capture(item, slot);
        }

        private void changeLibraryPage(int delta) {
            libraryPage = Math.clamp(libraryPage + delta, 0, Math.max(0, (menu.library().size() - 1) / LIBRARY_ROWS));
            updateControls();
        }

        @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
            event = layoutEvent(event);
            dx /= layoutScale;
            dy /= layoutScale;
            var page = deciphering;
            if (page != null && page.mouseDragged(event, dx, dy)) return true;
            return super.mouseDragged(event, dx, dy);
        }
        @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
            event = layoutEvent(event);
            var page = deciphering;
            if (page != null && page.mouseReleased(event)) { updateControls(); return true; }
            return super.mouseReleased(event);
        }

        @Override public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
            x /= layoutScale;
            y /= layoutScale;
            if (libraryOpen && inLibrary(x, y)) {
                if (scrollY != 0) changeLibraryPage(scrollY > 0 ? -1 : 1);
                return true;
            }
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }

        private boolean inLibrary(double x, double y) {
            return x >= leftPos - LIBRARY_WIDTH && x < leftPos && y >= topPos && y < topPos + LIBRARY_HEIGHT;
        }

        @Override protected boolean hasClickedOutside(double x, double y, int left, int top) {
            if (libraryOpen && inLibrary(x, y)) return false;
            if (x >= leftPos + 20 && x < leftPos + 44 && y >= topPos - 20 && y < topPos) return false;
            return super.hasClickedOutside(x, y, left, top);
        }

        private final class LibraryRow extends Button {
            private final int row;
            private LibraryRow(int row) {
                super(leftPos - LIBRARY_WIDTH + 8, topPos + 24 + row * 34, 128, 30, Component.empty(),
                    _ -> chooseLibrary(row), DEFAULT_NARRATION);
                this.row = row;
            }
            private Optional<Enchanting.LibraryEntry> entry() {
                int index = libraryPage * LIBRARY_ROWS + row;
                return index < menu.library().size() ? Optional.of(menu.library().get(index)) : Optional.empty();
            }
            private void update() {
                var entry = entry();
                visible = entry.isPresent();
                active = entry.filter(menu::canSelectLibrary).isPresent();
                entry.ifPresent(value -> {
                    var names = Component.empty();
                    for (var label : tooltipLabels(value.book(), DataComponents.STORED_ENCHANTMENTS)) {
                        if (!names.getString().isEmpty()) names.append(" / ");
                        names.append(label);
                    }
                    setMessage(names);
                });
            }
            @Override protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                var entry = entry();
                if (entry.isEmpty()) return;
                var entries = tooltipEntries(entry.get().book(), DataComponents.STORED_ENCHANTMENTS);
                int nameWidth = entries.stream().mapToInt(e -> font.width(label(e.enchantment(), e.level()))).max().orElse(0);
                int variant = nameWidth <= 61 ? 3 : nameWidth <= 77 ? 2 : 1;
                int rowWidth = variant == 3 ? 80 : variant == 2 ? 96 : 128;
                graphics.blit(RenderPipelines.GUI_TEXTURED, Naturality.id("textures/gui/enchanting_library_row_" + variant + ".png"),
                    getX(), getY(), 0, 0, 128, 30, 128, 30);
                if (isHoveredOrFocused()) graphics.fill(getX() + 1, getY() + 1, getX() + rowWidth - 1, getY() + 29, 0x25FFFFFF);
                int y = getY() + (entries.size() == 1 ? 10 : 3);
                for (var enchantment : entries) {
                    if (y + 10 > getY() + 30) break;
                    graphics.blit(RenderPipelines.GUI_TEXTURED, EnchantmentIcons.texture(enchantment.enchantment(), false),
                        getX() + 3, y, 0, 0, 10, 10, 16, 16, 16, 16);
                    String name = label(enchantment.enchantment(), enchantment.level()).getString();
                    graphics.text(font, font.plainSubstrByWidth(name, 109), getX() + 16, y + 1,
                        active ? enchantment.enchantment().is(EnchantmentTags.CURSE) ? 0xFF8B3030 : INK : 0xFF777777, false);
                    y += 14;
                }
                if (isHovered()) graphics.setTooltipForNextFrame(font, entry.get().book(),
                    (int)(mouseX * layoutScale), (int)(mouseY * layoutScale));
            }
        }

        private void chooseLibrary(int row) {
            int index = libraryPage * LIBRARY_ROWS + row;
            if (index >= menu.library().size()) return;
            var entry = menu.library().get(index);
            if (!menu.canSelectLibrary(entry)) return;
            ClientPlayNetworking.send(new Enchanting.LibrarySelection(menu.containerId, menu.libraryRevision(), entry.shelfSlot()));
            libraryOpen = false;
            rememberedLibraryOpen = false;
            rebuildWidgets();
        }
        private void text(GuiGraphicsExtractor graphics, String text, int x, int y) {
            graphics.text(font, Component.literal(text), x, y, INK, false);
        }
        private void paragraph(GuiGraphicsExtractor graphics, String text, int x, int y) {
            graphics.textWithWordWrap(font, Component.literal(text), x, y, TEXT_WIDTH, INK, false);
        }
        private void enchantmentDetails(GuiGraphicsExtractor graphics, List<Entry> entries) {
            // Use the tooltip's authored details, level/deadly rules and icon/name/list hierarchy.
            int totalHeight = 0;
            for (var entry : entries) totalHeight += pageEntryHeight(entry);
            float scale = Math.min(1f, 122f / Math.max(1, totalHeight));
            graphics.pose().pushMatrix();
            graphics.pose().translate(LEFT_TEXT, 34);
            graphics.pose().scale(scale, scale);
            int y = 0;
            for (var entry : entries) {
                int color = entry.enchantment().is(EnchantmentTags.CURSE) ? 0xFF8B3030 : INK;
                graphics.blit(RenderPipelines.GUI_TEXTURED, EnchantmentIcons.texture(entry.enchantment(), false),
                    0, y, 0, 0, 16, 16, 16, 16);
                var names = font.split(entry.enchantment().value().description(), TEXT_WIDTH - 20);
                int lineY = y + Math.max(0, (16 - names.size() * font.lineHeight) / 2);
                for (var name : names) { graphics.text(font, name, 20, lineY, color, false); lineY += font.lineHeight; }
                graphics.pose().pushMatrix();
                graphics.pose().translate(20, y + Math.max(16, names.size() * font.lineHeight));
                graphics.pose().scale(.75f, .75f);
                int row = 0;
                for (var detail : entry.details()) {
                    for (var line : font.split(Component.literal("• ").append(detail), (int)((TEXT_WIDTH - 20) / .75f))) {
                        graphics.text(font, line, 0, row * 10, color, false);
                        row++;
                    }
                }
                graphics.pose().popMatrix();
                y += pageEntryHeight(entry);
            }
            graphics.pose().popMatrix();
        }
        private int pageEntryHeight(Entry entry) {
            int rows = 0;
            for (var detail : entry.details()) rows += font.split(Component.literal("• ").append(detail), (int)((TEXT_WIDTH - 20) / .75f)).size();
            return Math.max(16, font.split(entry.enchantment().value().description(), TEXT_WIDTH - 20).size() * font.lineHeight)
                + (int)Math.ceil(rows * 7.5f) + 4;
        }
    }
}

