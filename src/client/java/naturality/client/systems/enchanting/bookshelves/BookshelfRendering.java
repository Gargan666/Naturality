package naturality.client.systems.enchanting.bookshelves;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Locale;
import naturality.client.api.rendering.AtlasLocations;
import naturality.client.systems.enchanting.EnchantingClient;
import naturality.systems.enchanting.framework.Enchanting;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import naturality.Naturality;
import naturality.api.worlds.LoadedChunks;
import naturality.client.api.rendering.PlacementGlow;
import naturality.systems.enchanting.bookshelves.BookshelfBooks;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Native per-slot glint, crosshair item tooltips and short, event-only placement pulses. */
@org.jspecify.annotations.NullMarked
public final class BookshelfRendering implements BlockEntityRenderer<ChiseledBookShelfBlockEntity, BookshelfRendering.State> {
    private record Key(BlockPos pos, int slot) {}
    private static final Map<Key, Long> FLASHES = new LinkedHashMap<>();
    private static final Map<BlockPos, Long> SHELVES = new LinkedHashMap<>();
    private static final List<net.minecraft.client.particle.Particle> LETTERS = new ArrayList<>();
    private static @Nullable Word word;
    private static long nextWordTick, lastWordTick = Long.MIN_VALUE;
    private static final class Word {
        final Key source;
        final net.minecraft.world.item.ItemStack book;
        final BlockPos table;
        final Vec3 origin, destination;
        final String text;
        int index;
        long nextLetterTick;
        Word(Key source, net.minecraft.world.item.ItemStack book, BlockPos table, Vec3 origin, Vec3 destination, String text) {
            this.source = source; this.book = book.copy(); this.table = table;
            this.origin = origin; this.destination = destination; this.text = text;
        }
    }
    private record Glyph(TextureAtlasSprite sprite) implements net.minecraft.client.particle.SpriteSet {
        @Override public TextureAtlasSprite get(int index, int max) { return sprite; }
        @Override public TextureAtlasSprite get(net.minecraft.util.RandomSource random) { return sprite; }
        @Override public TextureAtlasSprite first() { return sprite; }
    }
    private static @Nullable ClientLevel currentLevel;
    private record Book(BookshelfVisuals.Slot geometry, float life) {}
    public static final class State extends BlockEntityRenderState {
        private final List<Book> books = new ArrayList<>();
        private Direction front = Direction.NORTH;
    }

    public static void initialize() {
        BookshelfVisuals.initialize();
        BlockEntityRenderers.register(BlockEntityTypes.CHISELED_BOOKSHELF, _ -> new BookshelfRendering());
        ClientTickEvents.END_CLIENT_TICK.register(BookshelfRendering::tick);
        ClientPlayNetworking.registerGlobalReceiver(BookshelfBooks.Placement.TYPE, (payload, context) -> {
            var client = context.client();
            tick(client);
            var level = client.level;
            if (level == null || payload.slot() < 0 || payload.slot() >= 6 || !LoadedChunks.has(level, payload.pos())) return;
            var key = new Key(payload.pos().immutable(), payload.slot());
            if (FLASHES.size() >= 128 && !FLASHES.containsKey(key)) FLASHES.remove(FLASHES.keySet().iterator().next());
            FLASHES.put(key, level.getGameTime());
        });
        HudElementRegistry.attachElementAfter(VanillaHudElements.SUBTITLES, Naturality.id("bookshelf_book_tooltip"),
            (graphics, _) -> tooltip(graphics));
    }

    private static void tick(Minecraft client) {
        var level = client.level;
        if (currentLevel != level) {
            FLASHES.clear(); SHELVES.clear(); clearWord(); currentLevel = level;
            lastWordTick = Long.MIN_VALUE; nextWordTick = level == null ? 0 : level.getGameTime() + 60;
        }
        if (level == null) return;
        if (!client.isPaused() && lastWordTick != level.getGameTime()) {
            lastWordTick = level.getGameTime();
            tickWords(client, level);
        }
        FLASHES.entrySet().removeIf(entry -> {
            long age = level.getGameTime() - entry.getValue();
            var key = entry.getKey();
            if (age >= PlacementGlow.DURATION_TICKS || !LoadedChunks.has(level, key.pos())) return true;
            if (!(level.getBlockEntity(key.pos()) instanceof ChiseledBookShelfBlockEntity shelf)) return true;
            // Placement packets may precede the native inventory update by a tick.
            return age > 3 && (shelf.getItem(key.slot()).isEmpty() || !shelf.getItem(key.slot()).hasFoil());
        });
    }

    private static void clearWord() {
        for (var particle : LETTERS) particle.remove();
        LETTERS.clear(); word = null;
    }

    private static void tickWords(Minecraft client, ClientLevel level) {
        long now = level.getGameTime();
        SHELVES.entrySet().removeIf(e -> now - e.getValue() > 100 || !LoadedChunks.has(level, e.getKey()));
        LETTERS.removeIf(p -> !p.isAlive());
        if (client.options.particles().get() == ParticleStatus.MINIMAL) { clearWord(); return; }
        var active = word;
        if (active != null) {
            var offset = active.source.pos().subtract(active.table);
            var gap = active.table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            var camera = client.getCameraEntity();
            if (camera == null || active.source.pos().distToCenterSqr(camera.position()) > 4096
                    || !LoadedChunks.has(level, active.source.pos()) || !LoadedChunks.has(level, active.table)
                    || !LoadedChunks.has(level, gap) || !level.getBlockState(active.table).is(Blocks.ENCHANTING_TABLE)
                    || !level.getBlockState(gap).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)
                    || !(level.getBlockEntity(active.source.pos()) instanceof ChiseledBookShelfBlockEntity shelf)
                    || !net.minecraft.world.item.ItemStack.matches(active.book, shelf.getItem(active.source.slot()))) {
                clearWord(); nextWordTick = now + 60; return;
            }
            if (active.index < active.text.length() && now >= active.nextLetterTick) {
                char letter = active.text.charAt(active.index++);
                // Vanilla enchant starts at anchor + displacement and sinks 1.2 blocks on arrival.
                var anchor = active.destination.add(0, 1.2, 0);
                var delta = active.origin.subtract(anchor);
                var particle = client.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    anchor.x, anchor.y, anchor.z, delta.x, delta.y, delta.z);
                if (particle instanceof SingleQuadParticle quad) {
                    var sprite = client.getAtlasManager().get(new SpriteId(AtlasLocations.PARTICLES,
                        Identifier.withDefaultNamespace("sga_" + letter)));
                    quad.setSpriteFromAge(new Glyph(sprite));
                    LETTERS.add(particle);
                }
                active.nextLetterTick = now + 5;
            }
            if (active.index == active.text.length() && LETTERS.isEmpty()) {
                word = null; nextWordTick = now + 40 + client.particleEngine.getRandom().nextInt(81);
            }
            return;
        }
        if (now < nextWordTick || SHELVES.isEmpty()) return;
        nextWordTick = now + 40 + client.particleEngine.getRandom().nextInt(81);
        var positions = new ArrayList<>(SHELVES.keySet());
        int start = client.particleEngine.getRandom().nextInt(positions.size());
        for (int i = 0; i < positions.size(); i++) {
            var pos = positions.get((start + i) % positions.size());
            int slotStart = client.particleEngine.getRandom().nextInt(6);
            for (int slot = 0; slot < 6; slot++) {
                var chosen = chooseWord(client, level, new Key(pos, (slotStart + slot) % 6));
                if (chosen != null) { word = chosen; return; }
            }
        }
    }

    private static @Nullable Word chooseWord(Minecraft client, ClientLevel level, Key key) {
        if (!LoadedChunks.has(level, key.pos())
                || !(level.getBlockEntity(key.pos()) instanceof ChiseledBookShelfBlockEntity shelf)) return null;
        var book = shelf.getItem(key.slot());
        if (!book.is(Items.ENCHANTED_BOOK)) return null;
        var enchantments = EnchantingClient.bookTooltip(book);
        var enchantment = enchantments.keySet().stream()
            .sorted(Comparator.comparingInt((Holder<Enchantment> h) ->
                h.is(EnchantmentTags.CURSE) ? -1 : Enchanting.quality(h)).reversed())
            .findFirst();
        if (enchantment.isEmpty()) return null;
        var camera = client.getCameraEntity();
        if (camera == null || key.pos().distToCenterSqr(camera.position()) > 4096
                || client.options.particles().get() == ParticleStatus.MINIMAL) return null;
        BlockPos target = null;
        double nearest = Double.MAX_VALUE;
        for (var offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            var table = key.pos().subtract(offset);
            var gap = table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            if (!LoadedChunks.has(level, table) || !LoadedChunks.has(level, gap)
                    || !level.getBlockState(table).is(Blocks.ENCHANTING_TABLE)
                    || !level.getBlockState(gap).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER)) continue;
            double distance = table.distSqr(key.pos());
            if (distance < nearest) { nearest = distance; target = table; }
        }
        if (target == null) return null;
        var pixels = BookshelfVisuals.slots(client, shelf.getBlockState()).get(key.slot()).pixels();
        if (pixels.isEmpty()) return null;
        Vec3 origin = Vec3.ZERO;
        for (var pixel : pixels) for (var corner : pixel.corners()) origin = origin.add(corner);
        var front = shelf.getBlockState().getValue(ChiseledBookShelfBlock.FACING);
        origin = origin.scale(1.0 / (pixels.size() * 4)).add(Vec3.atLowerCornerOf(key.pos()))
            .add(front.getStepX() * .03, 0, front.getStepZ() * .03);
        var destination = Vec3.atLowerCornerOf(target).add(.5, 1.1, .5);
        String[] words = EnchantingClient.clue(enchantment.get()).toLowerCase(Locale.ROOT).split("\\s+");
        String text = words[client.particleEngine.getRandom().nextInt(words.length)].replaceAll("[^a-z]", "");
        if (text.isEmpty()) return null;
        return new Word(key, book, target, origin, destination, text.substring(0, Math.min(32, text.length())));
    }

    private static void tooltip(GuiGraphicsExtractor graphics) {
        var client = Minecraft.getInstance();
        var level = client.level;
        if (level == null || client.player == null || client.gui.screen() != null
                || !(client.hitResult instanceof BlockHitResult hit) || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK
                || !LoadedChunks.has(level, hit.getBlockPos())) return;
        var state = level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof ChiseledBookShelfBlock block)
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof ChiseledBookShelfBlockEntity shelf)) return;
        var slot = block.getHitSlot(hit, state.getValue(ChiseledBookShelfBlock.FACING));
        if (slot.isEmpty()) return;
        var book = shelf.getItem(slot.getAsInt());
        if (!book.isEmpty()) {
            int x = graphics.guiWidth() / 2 + 12, y = graphics.guiHeight() / 2 + 12;
            graphics.nextStratum();
            graphics.setTooltipForNextFrame(client.font, book, x, y);
            // HUD rendering has no Screen to flush the deferred native tooltip.
            graphics.extractDeferredElements(x, y, 0);
        }
    }

    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(@Nullable ChiseledBookShelfBlockEntity shelf, @Nullable State state, float partialTick,
            Vec3 camera, ModelFeatureRenderer.@Nullable CrumblingOverlay breaking) {
        if (state == null) return;
        state.books.clear();
        if (shelf == null) return;
        BlockEntityRenderer.super.extractRenderState(shelf, state, partialTick, camera, breaking);
        state.front = shelf.getBlockState().getValue(ChiseledBookShelfBlock.FACING);
        var level = shelf.getLevel();
        if (level == null) return;
        if (currentLevel != Minecraft.getInstance().level) tick(Minecraft.getInstance());
        var pos = shelf.getBlockPos().immutable();
        if (SHELVES.size() >= 128 && !SHELVES.containsKey(pos)) SHELVES.remove(SHELVES.keySet().iterator().next());
        SHELVES.put(pos, level.getGameTime());
        var center = Vec3.atCenterOf(shelf.getBlockPos());
        if (camera.subtract(center).dot(Vec3.atLowerCornerOf(state.front.getUnitVec3i())) <= .5) return;
        var slots = BookshelfVisuals.slots(Minecraft.getInstance(), shelf.getBlockState());
        for (int slot = 0; slot < 6; slot++) {
            var item = shelf.getItem(slot);
            if (item.isEmpty() || !item.hasFoil()) continue;
            var start = FLASHES.get(new Key(shelf.getBlockPos(), slot));
            float life = start == null ? 0 : PlacementGlow.envelope(level.getGameTime() - start + partialTick);
            state.books.add(new Book(slots.get(slot), life));
        }
    }

    @Override public void submit(@Nullable State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state == null || state.books.isEmpty()) return;
        var front = state.front;
        var books = List.copyOf(state.books);
        collector.submitCustomGeometry(pose, BookshelfVisuals.GLINT, (submitted, buffer) -> {
            for (var book : books) for (var pixel : book.geometry().pixels()) for (int i = 0; i < 4; i++) {
                var p = pixel.corners().get(i);
                buffer.addVertex(submitted, (float)p.x + front.getStepX() / 1024f, (float)p.y, (float)p.z + front.getStepZ() / 1024f)
                    .setUv(pixel.x() + (i == 1 || i == 2 ? 1 : 0), pixel.y() + (i >= 2 ? 1 : 0));
            }
        });
        if (books.stream().noneMatch(book -> book.life() > 0)) return;
        collector.submitCustomGeometry(pose, BookshelfVisuals.GLOW, (submitted, buffer) -> {
            Vec3 normal = new Vec3(front.getStepX(), 0, front.getStepZ());
            for (var book : books) {
                if (book.life() <= 0) continue;
                for (var edge : book.geometry().edges()) strip(buffer, submitted, edge, normal, book.life());
            }
        });
    }

    private static void strip(VertexConsumer buffer, PoseStack.Pose pose, BookshelfVisuals.Edge edge, Vec3 normal, float life) {
        for (int i = 0; i < 4; i++) {
            float reach = i == 1 || i == 2 ? (float)Math.ceil(.27 / edge.pixelSize()) * edge.pixelSize() : 0;
            var p = (i < 2 ? edge.start() : edge.end()).add(normal.scale(reach + 2.0 / 1024));
            buffer.addVertex(pose, (float)p.x, (float)p.y, (float)p.z)
                .setUv(edge.phase() + (i < 2 ? 0 : edge.pixelSize()), reach)
                .setColor(ARGB.colorFromFloat(life, life, 1, 1)).setUv2(Math.round(1 / edge.pixelSize()), 0);
        }
    }
}

