package naturality.systems.enchanting.framework;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import naturality.Naturality;
import naturality.systems.enchanting.deciphering.Deciphering;
import naturality.api.worlds.LoadedChunks;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Authoritative paired enchantments and persistent table transactions. */
@org.jspecify.annotations.NullMarked
public final class Enchanting {
    public static final int DECIPHER = 0, READY = 1, WRITING = 2, FAILED = 3, EMPOWERING = 4, COMPLETE = 5;
    public static final int SUCCEED = 0, FAIL = 1, RETRY = 2, EMPOWER = 3, IMBUE = 4, PROGRESS = 5, NEXT = 6, DECLINE = 7, REROLL = 8;
    public static final int WRITING_LAPIS = 1;
    public static final DataComponentType<Integer> EMPOWER_COUNT = component("empower_count", Codec.intRange(0, 255));

    public static final class EmpowerRules {
        private EmpowerRules() {}
        public static int cost(int successes, boolean book, boolean treasure) {
            return Math.min(30, (book ? treasure ? 25 : 10 : 3) + 3 * Math.clamp(successes, 0, 255));
        }
        public static float curseChance(int successes, boolean book, int level) {
            return book && level == 4 ? 1f : Math.min(1f, .1f * (Math.clamp(successes, 0, 255) + 1));
        }
    }
    public static final net.minecraft.sounds.SoundEvent STAMP_SOUND = sound("enchanting_stamp");
    public static final net.minecraft.sounds.SoundEvent LAPIS_SOUND = sound("lapis_crush");
    public static final net.minecraft.sounds.SoundEvent WRITE_SOUND = sound("enchanting_write");
    public static final net.minecraft.sounds.SoundEvent HUM_SOUND = sound("enchanting_hum");
    public static final net.minecraft.sounds.SoundEvent PAGE_FLIP_SOUND = sound("enchanting_pageflip");

    private static net.minecraft.sounds.SoundEvent sound(String name) {
        var id = Naturality.id(name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, net.minecraft.sounds.SoundEvent.createVariableRangeEvent(id));
    }
    public static final DataComponentType<Long> WRITING_ID = component("imbuing_id", Codec.LONG);
    public static final DataComponentType<String> WRITING_TARGET = component("imbuing_target", Codec.STRING);
    public static final DataComponentType<String> WRITING_DRAFT = component("imbuing_draft", Codec.STRING);
    public static final DataComponentType<List<Pair>> PAIRS = component("enchantment_pairs", Pair.CODEC.listOf());
    public static final DataComponentType<Work> WORK = component("enchanting_work", Work.CODEC);
    public static final DataComponentType<Boolean> REVEALED = component("book_deciphered", Codec.BOOL);
    public static final DataComponentType<ItemEnchantments> CREATIVE_ENCHANTMENTS = component("creative_enchantments", ItemEnchantments.CODEC);
    public static final DataComponentType<ItemEnchantments> LIBRARY_SINGLE = component("library_single_offer", ItemEnchantments.CODEC);
    public static final DataComponentType<Integer> CAPACITY = component("enchantment_slots", Codec.intRange(0, 64));
    public static final MenuType<Menu> MENU = Registry.register(BuiltInRegistries.MENU, Naturality.id("enchanting"),
        new MenuType<>(Menu::new, FeatureFlags.VANILLA_SET));
    private Enchanting() {}

    private static <T> DataComponentType<T> component(String name, Codec<T> codec) {
        return Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, Naturality.id(name),
            DataComponentType.<T>builder().persistent(codec).ignoreSwapAnimation().build());
    }

    // Zero curse level means no attached curse; old positive-level saves remain valid.
    public record Pair(Holder<Enchantment> benefit, int level, Holder<Enchantment> curse, int curseLevel) {
        public static final Codec<Pair> CODEC = RecordCodecBuilder.create(i -> i.group(
            Enchantment.CODEC.fieldOf("benefit").forGetter(Pair::benefit),
            Codec.intRange(1, 255).fieldOf("level").forGetter(Pair::level),
            Enchantment.CODEC.fieldOf("curse").forGetter(Pair::curse),
            Codec.intRange(0, 255).fieldOf("curse_level").forGetter(Pair::curseLevel)
        ).apply(i, Pair::new));
    }

    public record Work(Optional<Pair> offer, int phase, int progress, int quality) {
        public static final Codec<Work> CODEC = RecordCodecBuilder.create(i -> i.group(
            Pair.CODEC.optionalFieldOf("offer").forGetter(Work::offer),
            Codec.intRange(0, 5).fieldOf("phase").forGetter(Work::phase),
            Codec.intRange(0, 100).fieldOf("progress").forGetter(Work::progress),
            Codec.intRange(0, 15).fieldOf("quality").forGetter(Work::quality)
        ).apply(i, Work::new));
        public Work at(int nextPhase) { return new Work(offer, nextPhase, nextPhase == DECIPHER ? quality * 3 : 0, quality); }
    }

    public interface TableAccess { Table naturality$enchanting(); }

    /** Stable shelf/slot address and a read-only snapshot; source books never leave the shelf. */
    public record LibraryEntry(int shelfSlot, ItemStack book) {
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LibraryEntry::shelfSlot, ItemStack.STREAM_CODEC, LibraryEntry::book, LibraryEntry::new);
        public Optional<Pair> offer() {
            List<Pair> pairs = book.getOrDefault(PAIRS, List.of());
            return pairs.size() == 1 ? Optional.of(pairs.getFirst()) : Optional.empty();
        }
        public ItemEnchantments single() {
            var entries = book.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY);
            return entries.size() == 1 ? entries : ItemEnchantments.EMPTY;
        }
    }

    public record LibraryPayload(int containerId, int revision, List<LibraryEntry> entries) implements CustomPacketPayload {
        public static final Type<LibraryPayload> TYPE = new Type<>(Naturality.id("enchanting_library"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LibraryPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LibraryPayload::containerId, ByteBufCodecs.VAR_INT, LibraryPayload::revision,
            LibraryEntry.STREAM_CODEC.apply(ByteBufCodecs.list(192)), LibraryPayload::entries, LibraryPayload::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LibrarySelection(int containerId, int revision, int shelfSlot) implements CustomPacketPayload {
        public static final Type<LibrarySelection> TYPE = new Type<>(Naturality.id("enchanting_library_select"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LibrarySelection> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, LibrarySelection::containerId, ByteBufCodecs.VAR_INT, LibrarySelection::revision,
            ByteBufCodecs.VAR_INT, LibrarySelection::shelfSlot, LibrarySelection::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record Inscription(int containerId, long attempt, String text, boolean confirm) implements CustomPacketPayload {
        public static final Type<Inscription> TYPE = new Type<>(Naturality.id("imbuing_inscription"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Inscription> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Inscription::containerId, ByteBufCodecs.LONG, Inscription::attempt,
            ByteBufCodecs.stringUtf8(256), Inscription::text, ByteBufCodecs.BOOL, Inscription::confirm, Inscription::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void initialize() {
        Deciphering.initialize();
        PayloadTypeRegistry.serverboundPlay().register(Inscription.TYPE, Inscription.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Inscription.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof Menu menu && menu.containerId == payload.containerId())
                menu.inscribe(payload);
        });
        naturality.systems.enchanting.bookshelves.BookshelfBooks.initialize();
        PayloadTypeRegistry.clientboundPlay().register(LibraryPayload.TYPE, LibraryPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(LibrarySelection.TYPE, LibrarySelection.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(LibrarySelection.TYPE, (payload, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof Menu menu && menu.containerId == payload.containerId())
                menu.selectLibrary(payload.revision(), payload.shelfSlot());
        });
        // Migrate acquired/loaded books without requiring a table visit. No world scan.
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (var player : server.getPlayerList().getPlayers()) {
                var inventory = player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    var stack = inventory.getItem(i);
                    migrateCurseName(stack, server.registryAccess());
                    if (stack.is(Items.ENCHANTED_BOOK)) normalizeBook(stack, server.registryAccess());
                }
            }
        });
    }

    public static String name(Holder<Enchantment> enchantment) {
        return enchantment.unwrapKey().map(key -> key.identifier().toString()).orElse("unknown");
    }

    public static boolean creativeBook(ItemStack item) {
        return item.is(Items.ENCHANTED_BOOK) && item.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY).size() == 1;
    }

    public static void creativeBooks(CreativeModeTab.Output output, HolderLookup<Enchantment> registry,
            CreativeModeTab.TabVisibility visibility, boolean allLevels) {
        registry.listElements().forEach(enchantment -> {
            String replacement = replacement(enchantment);
            if (!replacement.isEmpty() && registry.get(ResourceKey.create(Registries.ENCHANTMENT, Naturality.id(replacement))).isPresent()) return;
            int max = enchantment.value().getMaxLevel();
            for (int level = allLevels ? enchantment.value().getMinLevel() : max; level <= max; level++) {
                var book = new ItemStack(Items.ENCHANTED_BOOK);
                var contents = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
                contents.set(enchantment, level);
                book.set(DataComponents.STORED_ENCHANTMENTS, contents.toImmutable());
                book.set(CREATIVE_ENCHANTMENTS, contents.toImmutable());
                book.set(REVEALED, true);
                output.accept(book, visibility);
            }
        });
    }

    public static boolean deadly(Holder<Enchantment> curse) {
        return name(curse).equals("minecraft:binding_curse") || name(curse).equals("minecraft:vanishing_curse")
            || curse.is(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT, Naturality.id("deadly")));
    }

    public static Holder<Enchantment> canonical(Holder<Enchantment> holder, RegistryAccess registries) {
        String replacement = replacement(holder);
        if (replacement.isEmpty()) return holder;
        return registries.lookupOrThrow(Registries.ENCHANTMENT)
            .get(ResourceKey.create(Registries.ENCHANTMENT, Naturality.id(replacement))).<Holder<Enchantment>>map(v -> v).orElse(holder);
    }

    private static String replacement(Holder<Enchantment> holder) {
        return switch (name(holder)) {
            case "minecraft:sharpness", "minecraft:density" -> "lethality";
            case "minecraft:fortune", "minecraft:looting" -> "harvesting";
            case "naturality:fragility" -> "fragility_curse";
            default -> "";
        };
    }

    public static int quality(Holder<Enchantment> enchantment) {
        return switch (name(enchantment)) {
            case "minecraft:mending" -> 100;
            case "minecraft:silk_touch", "minecraft:infinity" -> 90;
            case "minecraft:unbreaking", "naturality:harvesting" -> 80;
            case "naturality:lethality", "minecraft:protection", "minecraft:efficiency", "minecraft:power" -> 60;
            default -> 40;
        };
    }

    private static Holder<Enchantment> fallbackCurse(RegistryAccess registries) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, Naturality.id("fragility_curse")));
    }

    /** Pair-local conflicts deliberately do not use vanilla's item-wide exclusive_set. */
    public static boolean compatiblePair(Holder<Enchantment> benefit, Holder<Enchantment> curse) {
        var id = curse.unwrapKey().orElseThrow().identifier();
        return !benefit.is(net.minecraft.tags.TagKey.create(Registries.ENCHANTMENT,
            net.minecraft.resources.Identifier.fromNamespaceAndPath(id.getNamespace(), "curse_conflicts/" + id.getPath())));
    }

    private static List<Holder.Reference<Enchantment>> curseChoices(Holder<Enchantment> benefit, ItemStack item,
            RegistryAccess registries, int level, boolean nonDeadly) {
        return registries.lookupOrThrow(Registries.ENCHANTMENT).listElements()
            .filter(h -> h.is(EnchantmentTags.CURSE) && replacement(h).isEmpty())
            .filter(h -> compatiblePair(benefit, h) && (!nonDeadly || !deadly(h)))
            .filter(h -> item.is(Items.BOOK) || item.is(Items.ENCHANTED_BOOK)
                ? benefit.value().getSupportedItems().stream().anyMatch(h.value().getSupportedItems()::contains)
                : h.value().canEnchant(item))
            .filter(h -> deadly(h) || h.value().getMaxLevel() >= level).toList();
    }

    private static Pair pair(Holder<Enchantment> benefit, int level, ItemStack item,
            RegistryAccess registries, RandomSource random, boolean book) {
        var curses = curseChoices(benefit, item, registries, level, false);
        if (curses.isEmpty()) return new Pair(benefit, level, fallbackCurse(registries), 0);
        var curse = curses.get(random.nextInt(curses.size()));
        return new Pair(benefit, level, curse, Math.min(curse.value().getMaxLevel(), book ? Math.max(1, level / 2) : level));
    }

    public static void normalizeBook(ItemStack book, RegistryAccess registries) {
        if (!book.is(Items.ENCHANTED_BOOK)) return;
        migrateCurseName(book, registries);
        if (creativeBook(book)) return;
        var stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        List<Pair> pairs = book.getOrDefault(PAIRS, List.of());
        if (pairs.size() == 1 && stored.getLevel(pairs.getFirst().benefit()) == pairs.getFirst().level()
                && stored.getLevel(pairs.getFirst().curse()) == pairs.getFirst().curseLevel() && stored.size() == (pairs.getFirst().curseLevel() > 0 ? 2 : 1)) return;
        var best = stored.entrySet().stream().filter(e -> !e.getKey().is(EnchantmentTags.CURSE))
            .max(Comparator.<it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<Holder<Enchantment>>>comparingInt(e -> quality(canonical(e.getKey(), registries)))
                .thenComparingInt(e -> e.getIntValue()).thenComparing(e -> name(e.getKey())));
        if (best.isEmpty()) return;
        var entry = best.get();
        var benefit = canonical(entry.getKey(), registries);
        var chosen = new Pair(benefit, Math.min(entry.getIntValue(), benefit.value().getMaxLevel()), fallbackCurse(registries), 0);
        var oldCurse = stored.entrySet().stream().filter(e -> e.getKey().is(EnchantmentTags.CURSE))
            .filter(e -> compatiblePair(benefit, e.getKey()))
            .max(Comparator.comparingInt(e -> e.getIntValue()));
        if (oldCurse.isPresent()) chosen = new Pair(chosen.benefit(), chosen.level(), oldCurse.get().getKey(), oldCurse.get().getIntValue());
        applyPairs(book, List.of(chosen));
        book.remove(WORK);
        book.remove(Deciphering.PUZZLE);
        book.remove(REVEALED);
    }

    public static List<Pair> pairs(ItemStack item, RegistryAccess registries) {
        migrateCurseName(item, registries);
        var saved = item.get(PAIRS);
        if (saved != null) return saved;
        List<Pair> imported = new ArrayList<>();
        var enchantments = item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        var curses = enchantments.entrySet().stream().filter(e -> e.getKey().is(EnchantmentTags.CURSE))
            .sorted(Comparator.comparing(e -> name(e.getKey()))).toList();
        for (var entry : enchantments.entrySet()) {
            if (entry.getKey().is(EnchantmentTags.CURSE)) continue;
            if (item.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(entry.getKey()) > 0) continue;
            var benefit = canonical(entry.getKey(), registries);
            int duplicate = -1;
            for (int i = 0; i < imported.size(); i++) if (imported.get(i).benefit().equals(benefit)) duplicate = i;
            if (duplicate >= 0) {
                var previous = imported.get(duplicate);
                imported.set(duplicate, new Pair(benefit, Math.max(previous.level(), entry.getIntValue()), previous.curse(), previous.curseLevel()));
            } else if (imported.size() < curses.size()) {
                var curse = curses.get(imported.size());
                imported.add(new Pair(benefit, entry.getIntValue(), curse.getKey(),
                    compatiblePair(benefit, curse.getKey()) ? curse.getIntValue() : 0));
            } else imported.add(new Pair(benefit, entry.getIntValue(), fallbackCurse(registries), 0));
        }
        return List.copyOf(imported);
    }

    /** Keep saved books, pairs and unfinished work intact across the curse ID rename. */
    public static void migrateCurseName(ItemStack item, RegistryAccess registries) {
        for (var type : List.of(DataComponents.ENCHANTMENTS, DataComponents.STORED_ENCHANTMENTS, CREATIVE_ENCHANTMENTS, LIBRARY_SINGLE)) {
            var previous = item.get(type);
            if (previous == null || previous.keySet().stream().noneMatch(h -> name(h).equals("naturality:fragility"))) continue;
            var updated = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
            previous.entrySet().forEach(e -> updated.upgrade(renamedCurse(e.getKey(), registries), e.getIntValue()));
            item.set(type, updated.toImmutable());
        }
        var savedPairs = item.get(PAIRS);
        if (savedPairs != null) {
            var updated = savedPairs.stream().map(p -> renamedPair(p, registries)).toList();
            if (!updated.equals(savedPairs)) item.set(PAIRS, updated);
        }
        var work = item.get(WORK);
        if (work != null) {
            var updated = work.offer().map(p -> renamedPair(p, registries));
            if (!updated.equals(work.offer())) item.set(WORK, new Work(updated, work.phase(), work.progress(), work.quality()));
        }
    }

    private static Holder<Enchantment> renamedCurse(Holder<Enchantment> holder, RegistryAccess registries) {
        return name(holder).equals("naturality:fragility") ? canonical(holder, registries) : holder;
    }

    private static Pair renamedPair(Pair pair, RegistryAccess registries) {
        return new Pair(renamedCurse(pair.benefit(), registries), pair.level(), renamedCurse(pair.curse(), registries), pair.curseLevel());
    }

    public static boolean accepts(ItemStack item, Pair pair, RegistryAccess registries) {
        if (item.isEmpty() || item.is(Items.ENCHANTED_BOOK)) return false;
        if (pair.curseLevel() > 0 && !compatiblePair(pair.benefit(), pair.curse())) return false;
        var existing = pairs(item, registries);
        if (existing.size() + standaloneSlots(item) >= item.getOrDefault(CAPACITY, 3)) return false;
        if (!item.is(Items.BOOK) && !pair.benefit().value().canEnchant(item)) return false;
        if (pair.curseLevel() > 0 && !item.is(Items.BOOK) && !pair.curse().value().canEnchant(item)) return false;
        if (!item.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
                .filter(h -> !h.is(EnchantmentTags.CURSE))
                .allMatch(h -> !h.equals(pair.benefit()) && Enchantment.areCompatible(h, pair.benefit()))) return false;
        return existing.stream().allMatch(p -> !p.benefit().equals(pair.benefit())
            && Enchantment.areCompatible(p.benefit(), pair.benefit()));
    }

    public static int standaloneSlots(ItemStack item) {
        return (int)item.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY).keySet().stream()
            .filter(h -> !h.is(EnchantmentTags.CURSE)).count();
    }

    public static void applyPairs(ItemStack item, List<Pair> pairs) {
        item.set(PAIRS, List.copyOf(pairs));
        var enchantments = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        for (var pair : pairs) {
            enchantments.upgrade(pair.benefit(), pair.level());
            // Contributions stack only up to the curse's supported maximum.
            if (pair.curseLevel() == 0) continue;
            int current = enchantments.getLevel(pair.curse());
            enchantments.set(pair.curse(), Math.min(pair.curse().value().getMaxLevel(), current + pair.curseLevel()));
        }
        for (var entry : item.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
            enchantments.upgrade(entry.getKey(), entry.getIntValue());
        }
        // Legacy orphan curses must not disappear when a new pair is written.
        if (!item.is(Items.ENCHANTED_BOOK)) {
            for (var entry : item.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
                if (entry.getKey().is(EnchantmentTags.CURSE)) enchantments.upgrade(entry.getKey(), entry.getIntValue());
            }
        }
        item.set(item.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, enchantments.toImmutable());
    }

    public static ItemStack grind(ItemStack item) {
        var result = item.is(Items.ENCHANTED_BOOK) ? item.transmuteCopy(Items.BOOK) : item.copy();
        result.remove(EMPOWER_COUNT); result.remove(Deciphering.PUZZLE); result.remove(PAIRS); result.remove(WORK); result.remove(REVEALED); result.remove(CREATIVE_ENCHANTMENTS); result.remove(LIBRARY_SINGLE);
        result.remove(DataComponents.STORED_ENCHANTMENTS);
        result.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        result.set(DataComponents.REPAIR_COST, 0);
        if (result.isDamageableItem()) result.setDamageValue(result.getDamageValue() + Math.max(1, (int)Math.ceil(result.getMaxDamage() * 0.1)));
        return result;
    }

    /** The vanilla block entity owns this inventory; item components own each saved attempt. */
    public static final class Table extends SimpleContainer {
        private final @Nullable BlockEntity owner;
        private boolean loading;
        private final java.util.Map<String, String> lastClues = new java.util.HashMap<>();
        public @Nullable String lastClue(String enchantment) { return lastClues.get(enchantment); }
        public void rememberClue(String enchantment, String word) {
            if (!word.equals(lastClues.put(enchantment, word))) setChanged();
        }
        public Table(@Nullable BlockEntity owner) { super(3); this.owner = owner; }
        @Override public void setChanged() {
            super.setChanged();
            var entity = owner;
            if (entity != null) {
                entity.setChanged();
                var level = entity.getLevel();
                if (!loading && level != null && !level.isClientSide() && !entity.isRemoved()) {
                    var state = entity.getBlockState();
                    level.sendBlockUpdated(entity.getBlockPos(), state, state, 3);
                }
            }
        }
        public void save(ValueOutput output) {
            output.store("naturality_enchanting", ItemStack.OPTIONAL_CODEC.listOf(), List.of(getItem(0), getItem(1), getItem(2)));
            output.store("naturality_last_clues", Codec.unboundedMap(Codec.STRING, Codec.STRING), lastClues);
        }
        public void load(ValueInput input) {
            lastClues.clear();
            lastClues.putAll(input.read("naturality_last_clues", Codec.unboundedMap(Codec.STRING, Codec.STRING)).orElse(java.util.Map.of()));
            var items = input.read("naturality_enchanting", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
            loading = true;
            try {
                for (int i = 0; i < 3; i++) setItem(i, i < items.size() ? items.get(i) : ItemStack.EMPTY);
            } finally {
                loading = false;
            }
        }
        @Override public boolean stillValid(Player player) {
            var entity = owner;
            return entity == null || (!entity.isRemoved() && player.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(entity.getBlockPos())) <= 64);
        }
        public int shelves() {
            var entity = owner;
            if (entity == null) return 0;
            var level = entity.getLevel();
            if (level == null) return 0;
            return Math.min(15, (int)EnchantingTableBlock.BOOKSHELF_OFFSETS.stream().filter(offset -> {
                var shelfPos = entity.getBlockPos().offset(offset);
                if (!shelfAccessible(offset)) return false;
                return level.getBlockState(shelfPos).is(BlockTags.ENCHANTMENT_POWER_PROVIDER)
                    || level.getBlockEntity(shelfPos) instanceof ChiseledBookShelfBlockEntity shelf && !shelf.isEmpty();
            }).count());
        }

        private boolean shelfAccessible(BlockPos offset) {
            var entity = owner;
            if (entity == null) return false;
            var level = entity.getLevel();
            if (level == null) return false;
            var gap = entity.getBlockPos().offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2);
            return LoadedChunks.has(level, entity.getBlockPos().offset(offset)) && LoadedChunks.has(level, gap)
                && level.getBlockState(gap).is(BlockTags.ENCHANTMENT_POWER_TRANSMITTER);
        }

        public List<LibraryEntry> library() {
            var entity = owner;
            if (entity == null) return List.of();
            var level = entity.getLevel();
            if (level == null) return List.of();
            List<LibraryEntry> result = new ArrayList<>();
            var offsets = EnchantingTableBlock.BOOKSHELF_OFFSETS;
            for (int index = 0; index < offsets.size(); index++) {
                var offset = offsets.get(index);
                if (!shelfAccessible(offset) || !(level.getBlockEntity(entity.getBlockPos().offset(offset))
                        instanceof ChiseledBookShelfBlockEntity shelf)) continue;
                for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                    var source = shelf.getItem(slot);
                    if (!source.is(Items.ENCHANTED_BOOK) || !source.getOrDefault(REVEALED, false)) continue;
                    var book = source.copyWithCount(1);
                    migrateCurseName(book, level.registryAccess());
                    if (result.stream().noneMatch(entry -> ItemStack.isSameItemSameComponents(entry.book(), book)))
                        result.add(new LibraryEntry(index * 6 + slot, book));
                }
            }
            return List.copyOf(result);
        }
    }

    public static final class Menu extends AbstractContainerMenu {
        public static final int WIDTH = 320, HEIGHT = 276;
        public static final int ITEM_X = 221, LAPIS_X = 241, MATERIAL_Y = 136;
        public static final int INVENTORY_X = 80, INVENTORY_Y = 192;
        public final Table table;
        private final Player player;
        private List<LibraryEntry> library = List.of();
        private int libraryRevision;
        private boolean librarySent;
        private int libraryTicks;
        private boolean decipherResult;
        private boolean writingResult;
        private boolean finishingDeciphering;
        public Menu(int id, Inventory inventory) { this(id, inventory, new Table(null)); }
        public Menu(int id, Inventory inventory, Table table) {
            super(MENU, id);
            this.player = inventory.player;
            this.table = table;
            addSlot(new Slot(table, 0, ITEM_X, MATERIAL_Y) {
                @Override public int getMaxStackSize() { return 1; }
                @Override public boolean mayPlace(ItemStack stack) {
                    if (phase() == WRITING || stack.isEmpty()) return false;
                    if (stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK)) return true;
                    if (!hasFreeSlot(stack)) return false;
                    // ENCHANTABLE is vanilla table eligibility, not whether an item supports enchantments.
                    return player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements()
                        .anyMatch(enchantment -> enchantment.value().canEnchant(stack));
                }
                @Override public boolean mayPickup(Player player) { return phase() != WRITING; }
                @Override public @Nullable Identifier getNoItemIcon() { return Identifier.withDefaultNamespace("container/slot/sword"); }
                @Override public ItemStack remove(int amount) {
                    var removed = super.remove(amount);
                    clearPending(removed);
                    return removed;
                }
                @Override public void onTake(Player player, ItemStack stack) {
                    clearPending(stack);
                    super.onTake(player, stack);
                }
            });
            addSlot(new Slot(table, 2, LAPIS_X, MATERIAL_Y) {
                @Override public boolean mayPlace(ItemStack stack) { return stack.is(Items.LAPIS_LAZULI); }
                @Override public @Nullable Identifier getNoItemIcon() { return Identifier.withDefaultNamespace("container/slot/lapis_lazuli"); }
            });
            addStandardInventorySlots(inventory, INVENTORY_X, INVENTORY_Y);
            if (!player.level().isClientSide()) {
                // Old serialized slot is retained only for lossless save migration.
                var legacyBook = table.removeItemNoUpdate(1);
                if (!legacyBook.isEmpty()) {
                    inventory.placeItemBackInInventory(legacyBook, net.minecraft.util.Prediction.SERVER_ONLY);
                    table.setChanged();
                }
                prepare();
            }
        }

        public ItemStack subject() { return table.getItem(0); }
        public long gameTime() { return player.level().getGameTime(); }
        private boolean hasFreeSlot(ItemStack stack) {
            return pairs(stack.copy(), player.registryAccess()).size() + standaloneSlots(stack) < stack.getOrDefault(CAPACITY, 3);
        }
        public boolean hasFreeSlot() { return !subject().isEmpty() && hasFreeSlot(subject()); }
        public boolean canReroll() { return phase() == DECIPHER && !book() && hasFreeSlot(); }
        public List<LibraryEntry> library() { return library; }
        public int libraryRevision() { return libraryRevision; }
        public void receiveLibrary(LibraryPayload payload) {
            if (!player.level().isClientSide() || payload.containerId() != containerId) return;
            library = List.copyOf(payload.entries());
            libraryRevision = payload.revision();
        }

        public boolean canSelectLibrary(LibraryEntry entry) {
            if (subject().isEmpty() || book()) return false;
            var work = work();
            var puzzle = Deciphering.puzzle(this);
            if (puzzle != null && puzzle.revision() > 0) return false;
            if (work != null && work.phase() == FAILED && !pendingSingle().isEmpty()) return false;
            if (work != null && work.offer().isPresent() && work.offer().get().curseLevel() > 0 && work.phase() != COMPLETE) return false;
            if (work != null && work.phase() != FAILED && work.phase() != COMPLETE
                    && !(work.phase() == DECIPHER && work.progress() == work.quality() * 3)) return false;
            return entry.offer().filter(offer -> accepts(subject().copy(), offer, player.registryAccess())).isPresent()
                || canApplySingle(subject().copy(), entry.single());
        }

        private void refreshLibrary() {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            var updated = table.library();
            boolean changed = updated.size() != library.size();
            for (int i = 0; !changed && i < updated.size(); i++)
                changed = updated.get(i).shelfSlot() != library.get(i).shelfSlot()
                    || !ItemStack.matches(updated.get(i).book(), library.get(i).book());
            if (changed || !librarySent) {
                library = updated;
                libraryRevision++;
                librarySent = true;
                ServerPlayNetworking.send(serverPlayer, new LibraryPayload(containerId, libraryRevision, library));
            }
        }

        private void selectLibrary(int revision, int shelfSlot) {
            if (player.level().isClientSide() || !stillValid(player)) return;
            // Re-read the source and access gap on every click; stale UI cannot select a changed book.
            refreshLibrary();
            if (revision != libraryRevision) return;
            var selected = library.stream().filter(entry -> entry.shelfSlot() == shelfSlot).findFirst();
            if (selected.isEmpty() || !canSelectLibrary(selected.get())) return;
            int quality = table.shelves();
            subject().remove(Deciphering.PUZZLE);
            subject().remove(Deciphering.ATTEMPT_BUDGET);
            subject().remove(LIBRARY_SINGLE);
            var offer = selected.get().offer();
            if (offer.isEmpty()) {
                var single = selected.get().single();
                var entry = single.entrySet().iterator().next();
                if (entry.getKey().is(EnchantmentTags.CURSE)) subject().set(LIBRARY_SINGLE, single);
                else offer = Optional.of(new Pair(entry.getKey(), entry.getIntValue(), fallbackCurse(player.registryAccess()), 0));
            }
            subject().set(EMPOWER_COUNT, selected.get().book().getOrDefault(EMPOWER_COUNT, 0));
            subject().set(WORK, new Work(offer, DECIPHER, quality * 3, quality));
            table.setChanged();
            broadcastChanges();
        }
        public boolean book() { return subject().is(Items.ENCHANTED_BOOK); }
        public @Nullable Work work() { return subject().get(WORK); }
        public int phase() { var work = work(); return work == null ? creativeBook(subject()) ? READY : -1 : work.phase(); }
        public ItemEnchantments pendingSingle() { return subject().getOrDefault(LIBRARY_SINGLE, ItemEnchantments.EMPTY); }
        public int empowerCost() {
            var work = work();
            if (work == null) return 0;
            Holder<Enchantment> enchantment;
            if (pendingSingle().size() == 1) enchantment = pendingSingle().keySet().iterator().next();
            else if (work.offer().isPresent()) enchantment = work.offer().get().benefit();
            else return 0;
            return EmpowerRules.cost(subject().getOrDefault(EMPOWER_COUNT, 0), book(), enchantment.is(EnchantmentTags.TREASURE));
        }

        public boolean canEmpower() {
            var work = work();
            if (work == null || work.phase() != READY) return false;
            if (pendingSingle().size() == 1) {
                var entry = pendingSingle().entrySet().iterator().next();
                return entry.getIntValue() < entry.getKey().value().getMaxLevel();
            }
            if (work.offer().isEmpty()) return false;
            var offer = work.offer().get();
            if (offer.curseLevel() > 0 && !compatiblePair(offer.benefit(), offer.curse())) return false;
            if (!book() && !accepts(subject(), offer, player.registryAccess())) return false;
            int next = offer.level() + 1;
            int nextCurse = offer.curseLevel() + 1;
            return next <= offer.benefit().value().getMaxLevel() && (offer.curseLevel() == 0 || deadly(offer.curse())
                || nextCurse <= offer.curse().value().getMaxLevel()
                || !curseChoices(offer.benefit(), subject(), player.registryAccess(), nextCurse, true).isEmpty());
        }

        private void prepare() {
            var subject = subject();
            if (subject.isEmpty()) return;
            migrateCurseName(subject, player.registryAccess());
            if (book()) normalizeBook(subject, player.registryAccess());
            var savedWork = subject.get(WORK);
            var single = subject.getOrDefault(LIBRARY_SINGLE, ItemEnchantments.EMPTY);
            if (savedWork != null && single.size() == 1) {
                var entry = single.entrySet().iterator().next();
                if (!entry.getKey().is(EnchantmentTags.CURSE)) {
                    subject.set(WORK, new Work(Optional.of(new Pair(entry.getKey(), entry.getIntValue(),
                        fallbackCurse(player.registryAccess()), 0)), savedWork.phase(), savedWork.progress(), savedWork.quality()));
                    subject.remove(LIBRARY_SINGLE);
                    table.setChanged();
                }
            }
            if (subject.has(WORK)) {
                if (phase() == WRITING && !subject.has(WRITING_ID)) {
                    subject.set(WRITING_ID, player.getRandom().nextLong());
                    subject.set(WRITING_TARGET, inscriptionTarget());
                    table.setChanged();
                }
                Deciphering.prepare(this, player.getRandom());
                return;
            }
            if (book()) {
                if (creativeBook(subject)) {
                    var contents = subject.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY);
                    var entry = contents.entrySet().iterator().next();
                    if (entry.getKey().is(EnchantmentTags.CURSE)) {
                        subject.set(LIBRARY_SINGLE, contents);
                        subject.set(WORK, new Work(Optional.empty(), READY, 0, table.shelves()));
                    } else subject.set(WORK, new Work(Optional.of(new Pair(entry.getKey(), entry.getIntValue(),
                        fallbackCurse(player.registryAccess()), 0)), READY, 0, table.shelves()));
                    table.setChanged();
                    return;
                }
                List<Pair> pairs = subject.getOrDefault(PAIRS, List.of());
                if (!pairs.isEmpty()) {
                    int phase = subject.getOrDefault(REVEALED, false) ? READY : DECIPHER;
                    int quality = table.shelves();
                    subject.set(WORK, new Work(Optional.of(pairs.getFirst()), phase, phase == DECIPHER ? quality * 3 : 0, quality));
                }
            } else roll();
            Deciphering.prepare(this, player.getRandom());
            table.setChanged();
        }

        private void roll() {
            roll(null);
        }
        private void roll(@Nullable Holder<Enchantment> previous) {
            var item = subject();
            item.remove(Deciphering.ATTEMPT_BUDGET);
            item.remove(EMPOWER_COUNT);
            item.remove(Deciphering.PUZZLE);
            item.remove(LIBRARY_SINGLE);
            var registry = player.registryAccess();
            var candidates = registry.lookupOrThrow(Registries.ENCHANTMENT).listElements()
                .filter(h -> !h.is(EnchantmentTags.CURSE) && canonical(h, registry).equals(h))
                .filter(h -> !h.is(EnchantmentTags.TREASURE))
                .filter(h -> item.is(Items.BOOK) || h.value().canEnchant(item))
                .filter(h -> accepts(item, new Pair(h, 1, fallbackCurse(registry), 0), registry)).toList();
            if (candidates.isEmpty()) return;
            var alternatives = candidates.stream().filter(h -> !h.equals(previous)).toList();
            var choices = alternatives.isEmpty() ? candidates : alternatives;
            var selected = choices.get(player.getRandom().nextInt(choices.size()));
            int quality = table.shelves();
            item.set(WORK, new Work(Optional.of(new Pair(selected, 1, fallbackCurse(registry), 0)), DECIPHER, quality * 3, quality));
        }

        @Override public void broadcastChanges() {
            if (finishingDeciphering) return;
            if (!player.level().isClientSide()) {
                prepare();
                if (!librarySent || ++libraryTicks >= 10) { libraryTicks = 0; refreshLibrary(); }
            }
            super.broadcastChanges();
        }
        @Override public boolean stillValid(Player player) { return table.stillValid(player); }

        public boolean canAffordLevels(int levels) {
            return player.hasInfiniteMaterials() || player.experienceLevel >= levels;
        }

        private boolean pay(int levels) {
            if (!canAffordLevels(levels)) return false;
            if (player.hasInfiniteMaterials()) return true;
            player.giveExperienceLevels(-levels);
            return true;
        }

        public boolean canDecipher() {
            return !player.level().isClientSide() && stillValid(player) && (phase() == DECIPHER || phase() == EMPOWERING);
        }

        private void playTableSound(net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
            var owner = table.owner;
            player.level().playSound(null, owner == null ? player.blockPosition() : owner.getBlockPos(), sound,
                net.minecraft.sounds.SoundSource.BLOCKS, volume, pitch);
        }

        public void playRevealSound() {
            playTableSound(STAMP_SOUND, 1f, .95f + player.getRandom().nextFloat() * .1f);
        }

        public void finishDeciphering(boolean success) {
            if (!canDecipher()) return;
            int nextBudget = (int)Math.min(Integer.MAX_VALUE, (long)subject().getOrDefault(Deciphering.ATTEMPT_BUDGET, 3) + 1);
            boolean retry = phase() == DECIPHER;
            decipherResult = true;
            finishingDeciphering = true;
            try {
                if (clickMenuButton(player, success ? SUCCEED : FAIL)) {
                    if (success) playTableSound(net.minecraft.sounds.SoundEvents.VILLAGER_WORK_LIBRARIAN, 2f, 1f);
                    else {
                        subject().set(Deciphering.ATTEMPT_BUDGET, nextBudget);
                        if (retry) clickMenuButton(player, RETRY);
                        table.setChanged();
                        broadcastChanges();
                    }
                }
            } finally {
                decipherResult = false;
                finishingDeciphering = false;
                broadcastChanges();
            }
        }

        public String inscriptionTarget() {
            // Synchronize the exact server-resolved name, so client language cannot change the answer.
            var target = subject().get(WRITING_TARGET);
            if (target != null && phase() == WRITING) return target;
            var work = work();
            if (work == null) return "";
            Holder<Enchantment> enchantment;
            int level;
            if (pendingSingle().size() == 1) {
                var entry = pendingSingle().entrySet().iterator().next();
                enchantment = entry.getKey();
                level = entry.getIntValue();
            } else if (work.offer().isPresent()) {
                enchantment = work.offer().get().benefit();
                level = work.offer().get().level();
            } else return "";
            StringBuilder roman = new StringBuilder();
            int[] values = {100, 90, 50, 40, 10, 9, 5, 4, 1};
            String[] digits = {"C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
            for (int i = 0; i < values.length; i++) {
                while (level >= values[i]) { roman.append(digits[i]); level -= values[i]; }
            }
            return enchantment.value().description().getString() + " " + roman;
        }

        private void inscribe(Inscription inscription) {
            if (player.level().isClientSide() || !stillValid(player) || phase() != WRITING
                    || !subject().has(WRITING_ID) || subject().getOrDefault(WRITING_ID, 0L) != inscription.attempt()) return;
            boolean edited = !subject().getOrDefault(WRITING_DRAFT, "").equals(inscription.text());
            subject().set(WRITING_DRAFT, inscription.text());
            if (edited) playTableSound(WRITE_SOUND, 1f, 1f);
            table.setChanged();
            if (!inscription.confirm()) return;
            boolean correct = inscription.text().strip().replaceAll("\\s+", " ").equalsIgnoreCase(inscriptionTarget());
            writingResult = true;
            boolean applied;
            try { applied = clickMenuButton(player, correct ? SUCCEED : FAIL); }
            finally { writingResult = false; }
            if (applied && phase() != WRITING) {
                subject().remove(WRITING_ID);
                subject().remove(WRITING_TARGET);
                subject().remove(WRITING_DRAFT);
                table.setChanged();
                broadcastChanges();
            }
            if (correct && applied) playTableSound(net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE, 1f, 1f);
        }

        @Override public boolean clickMenuButton(Player player, int action) {
            if (player != this.player || player.level().isClientSide() || !stillValid(player)) return false;
            prepare();
            if (phase() == WRITING && !writingResult) return false;
            if ((phase() == DECIPHER || phase() == EMPOWERING) && !decipherResult && (action == SUCCEED || action == FAIL || action == PROGRESS)) return false;
            if (action == RETRY && phase() == FAILED) subject().remove(Deciphering.PUZZLE);
            var item = subject();
            if (action == REROLL) {
                if (!canReroll()) return false;
                var current = work();
                @Nullable Holder<Enchantment> previous = null;
                if (current != null && current.offer().isPresent()) previous = current.offer().get().benefit();
                item.remove(WORK);
                roll(previous);
                table.setChanged();
                broadcastChanges();
                return true;
            }
            if (pendingSingle().size() == 1) return clickSingle(action);
            var work = work();
            if (work == null) return false;
            int phase = work.phase();
            var offer = work.offer().orElse(null);
            if (action == RETRY && phase == FAILED) {
                if (offer != null) item.set(WORK, work.at(DECIPHER)); else {
                    int budget = item.getOrDefault(Deciphering.ATTEMPT_BUDGET, 3);
                    item.remove(WORK); roll();
                    item.set(Deciphering.ATTEMPT_BUDGET, budget);
                }
            } else if (action == NEXT && phase == COMPLETE && !book() && hasFreeSlot()) {
                item.remove(WORK);
                roll();
            } else if (action == DECLINE && phase == READY && !book()) {
                item.remove(Deciphering.PUZZLE);
                item.set(WORK, new Work(Optional.empty(), FAILED, 0, work.quality()));
            } else if (action == PROGRESS && (phase == DECIPHER || phase == WRITING || phase == EMPOWERING)) {
                item.set(WORK, new Work(work.offer(), phase, Math.min(100, work.progress() + 25), work.quality()));
            } else if (action == FAIL && (phase == DECIPHER || phase == WRITING || phase == EMPOWERING)) {
                if (phase == DECIPHER && offer != null) {
                    var punished = penalize(offer);
                    if (book()) applyPairs(item, List.of(punished));
                    item.set(WORK, new Work(Optional.of(punished), FAILED, 0, work.quality()));
                } else if (book() && phase == EMPOWERING) {
                    var blank = item.transmuteCopy(Items.BOOK);
                    blank.remove(PAIRS); blank.remove(REVEALED); blank.remove(DataComponents.STORED_ENCHANTMENTS);
                    blank.remove(CREATIVE_ENCHANTMENTS); blank.remove(EMPOWER_COUNT); blank.remove(LIBRARY_SINGLE);
                    blank.set(WORK, new Work(Optional.empty(), FAILED, 0, work.quality()));
                    table.setItem(0, blank);
                } else item.set(WORK, new Work(book() ? work.offer() : Optional.empty(), FAILED, 0, work.quality()));
            } else if (action == SUCCEED && offer != null) {
                if (phase == DECIPHER) {
                    item.set(WORK, work.at(READY));
                    if (book()) item.set(REVEALED, true);
                } else if (phase == EMPOWERING) {
                    var empowered = empower(offer);
                    item.set(EMPOWER_COUNT, Math.min(255, item.getOrDefault(EMPOWER_COUNT, 0) + 1));
                    if (book()) {
                        item.remove(CREATIVE_ENCHANTMENTS);
                        applyPairs(item, List.of(empowered));
                        item.set(REVEALED, true);
                    }
                    item.set(WORK, new Work(Optional.of(empowered), READY, 0, work.quality()));
                } else if (phase == WRITING) {
                    if (!commit(offer)) return false;
                } else return false;
            } else if (action == EMPOWER && phase == READY && offer != null) {
                if (!canEmpower() || !pay(empowerCost())) return false;
                item.remove(Deciphering.PUZZLE);
                item.remove(Deciphering.ATTEMPT_BUDGET);
                item.set(WORK, work.at(EMPOWERING));
            } else if (action == IMBUE && phase == READY && offer != null) {
                if (book() || !accepts(table.getItem(0), offer, player.registryAccess()) || !table.getItem(2).is(Items.LAPIS_LAZULI)
                        || table.getItem(2).getCount() < WRITING_LAPIS) return false;
                table.getItem(2).shrink(WRITING_LAPIS);
                playTableSound(LAPIS_SOUND, 1f, 1f);
                item.set(WRITING_ID, player.getRandom().nextLong());
                item.set(WRITING_DRAFT, "");
                item.set(WRITING_TARGET, inscriptionTarget());
                item.set(WORK, work.at(WRITING));
            } else return false;
            table.setChanged();
            broadcastChanges();
            return true;
        }

        private static void clearPending(ItemStack stack) {
            stack.remove(WORK);
            stack.remove(Deciphering.PUZZLE);
            stack.remove(Deciphering.ATTEMPT_BUDGET);
            stack.remove(Deciphering.READY_AT);
            stack.remove(WRITING_ID);
            stack.remove(WRITING_TARGET);
            stack.remove(WRITING_DRAFT);
            stack.remove(LIBRARY_SINGLE);
            if (!stack.is(Items.ENCHANTED_BOOK)) stack.remove(EMPOWER_COUNT);
        }

        private Pair empower(Pair offer) {
            int next = offer.level() + 1;
            var curse = offer.curse();
            int curseLevel = offer.curseLevel();
            if (curseLevel > 0) {
                if (curse.value().getMaxLevel() > 1 || !deadly(curse)) {
                    int nextCurse = curseLevel + 1;
                    if (!deadly(curse) && curse.value().getMaxLevel() < nextCurse) {
                        var replacements = curseChoices(offer.benefit(), subject(), player.registryAccess(), nextCurse, true);
                        if (replacements.isEmpty()) return offer;
                        curse = replacements.get(player.getRandom().nextInt(replacements.size()));
                    }
                    curseLevel = Math.min(curse.value().getMaxLevel(), nextCurse);
                }
            } else if (player.getRandom().nextFloat() < EmpowerRules.curseChance(
                    subject().getOrDefault(EMPOWER_COUNT, 0), book() || subject().is(Items.BOOK), offer.level())) {
                var choices = curseChoices(offer.benefit(), subject(), player.registryAccess(), 1, true);
                if (!choices.isEmpty()) {
                    curse = choices.get(player.getRandom().nextInt(choices.size()));
                    curseLevel = 1;
                }
            }
            return new Pair(offer.benefit(), next, curse, curseLevel);
        }

        private Pair penalize(Pair offer) {
            if (offer.curseLevel() == 0 || !compatiblePair(offer.benefit(), offer.curse()))
                return pair(offer.benefit(), offer.level(), subject(), player.registryAccess(), player.getRandom(), false);
            var curse = offer.curse();
            return new Pair(offer.benefit(), offer.level(), curse, Math.min(curse.value().getMaxLevel(), offer.curseLevel() + 1));
        }

        private boolean clickSingle(int action) {
            var work = work();
            if (work == null) return false;
            var item = subject();
            int phase = work.phase();
            if (action == PROGRESS && (phase == DECIPHER || phase == WRITING || phase == EMPOWERING)) {
                item.set(WORK, new Work(Optional.empty(), phase, Math.min(100, work.progress() + 25), work.quality()));
            } else if (action == RETRY && phase == FAILED) {
                item.set(WORK, work.at(DECIPHER));
            } else if (action == FAIL && phase == DECIPHER) {
                var entry = pendingSingle().entrySet().iterator().next();
                var updated = new ItemEnchantments.Mutable(pendingSingle());
                updated.set(entry.getKey(), Math.min(entry.getKey().value().getMaxLevel(), entry.getIntValue() + 1));
                item.set(LIBRARY_SINGLE, updated.toImmutable());
                item.set(WORK, work.at(FAILED));
            } else if ((action == FAIL && (phase == WRITING || phase == EMPOWERING))
                    || action == DECLINE && phase == READY) {
                item.remove(LIBRARY_SINGLE);
                if (book() && phase == EMPOWERING) {
                    var blank = item.transmuteCopy(Items.BOOK);
                    blank.remove(CREATIVE_ENCHANTMENTS); blank.remove(PAIRS); blank.remove(REVEALED);
                    blank.remove(DataComponents.STORED_ENCHANTMENTS); blank.remove(EMPOWER_COUNT);
                    blank.set(WORK, new Work(Optional.empty(), FAILED, 0, work.quality()));
                    table.setItem(0, blank);
                } else item.set(WORK, new Work(Optional.empty(), FAILED, 0, work.quality()));
            } else if (action == SUCCEED && phase == DECIPHER) {
                item.set(WORK, work.at(READY));
            } else if (action == EMPOWER && phase == READY) {
                if (!canEmpower() || !pay(empowerCost())) return false;
                item.remove(Deciphering.PUZZLE);
                item.remove(Deciphering.ATTEMPT_BUDGET);
                item.set(WORK, work.at(EMPOWERING));
            } else if (action == SUCCEED && phase == EMPOWERING) {
                var entry = pendingSingle().entrySet().iterator().next();
                var updated = new ItemEnchantments.Mutable(pendingSingle());
                updated.set(entry.getKey(), entry.getIntValue() + 1);
                item.set(LIBRARY_SINGLE, updated.toImmutable());
                item.set(EMPOWER_COUNT, Math.min(255, item.getOrDefault(EMPOWER_COUNT, 0) + 1));
                if (book()) {
                    item.set(CREATIVE_ENCHANTMENTS, updated.toImmutable());
                    applyPairs(item, List.of());
                    item.set(REVEALED, true);
                }
                item.set(WORK, work.at(READY));
            } else if (action == IMBUE && phase == READY) {
                if (book() || !canApplySingle(item.copy(), pendingSingle()) || !table.getItem(2).is(Items.LAPIS_LAZULI)
                        || table.getItem(2).getCount() < WRITING_LAPIS) return false;
                table.getItem(2).shrink(WRITING_LAPIS);
                playTableSound(LAPIS_SOUND, 1f, 1f);
                item.set(WRITING_ID, player.getRandom().nextLong());
                item.set(WRITING_DRAFT, "");
                item.set(WRITING_TARGET, inscriptionTarget());
                item.set(WORK, work.at(WRITING));
            } else if (action == SUCCEED && phase == WRITING) {
                if (!commitSingle(pendingSingle())) return false;
            } else return false;
            table.setChanged();
            broadcastChanges();
            return true;
        }

        private boolean commit(Pair offer) {
            var target = table.getItem(0);
            if (book() || subject() != target || !accepts(target, offer, player.registryAccess())) return false;
            var result = target.is(Items.BOOK) ? target.transmuteCopy(Items.ENCHANTED_BOOK) : target.copy();
            var pairs = new ArrayList<>(pairs(target, player.registryAccess()));
            pairs.add(offer);
            applyPairs(result, pairs);
            result.remove(WORK);
            result.remove(WRITING_ID);
            result.remove(WRITING_TARGET);
            result.remove(WRITING_DRAFT);
            result.remove(LIBRARY_SINGLE);
            if (result.is(Items.ENCHANTED_BOOK)) result.set(REVEALED, true);
            else {
                result.remove(EMPOWER_COUNT);
                result.set(WORK, new Work(Optional.empty(), COMPLETE, 0, table.shelves()));
            }
            table.setItem(0, result);
            return true;
        }

        private boolean canApplySingle(ItemStack target, ItemEnchantments enchantments) {
            if (enchantments.size() != 1 || target.isEmpty() || target.is(Items.ENCHANTED_BOOK)) return false;
            var entry = enchantments.entrySet().iterator().next();
            var enchantment = entry.getKey();
            if (!target.is(Items.BOOK) && !enchantment.value().canEnchant(target)) return false;
            var existing = pairs(target, player.registryAccess());
            var singles = target.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY);
            if (!enchantment.is(EnchantmentTags.CURSE)) {
                if (existing.size() + standaloneSlots(target) >= target.getOrDefault(CAPACITY, 3)) return false;
                if (existing.stream().anyMatch(p -> p.benefit().equals(enchantment) || !Enchantment.areCompatible(p.benefit(), enchantment))) return false;
                if (singles.keySet().stream().filter(h -> !h.is(EnchantmentTags.CURSE))
                        .anyMatch(h -> h.equals(enchantment) || !Enchantment.areCompatible(h, enchantment))) return false;
            }
            return true;
        }

        private boolean commitSingle(ItemEnchantments enchantments) {
            var target = table.getItem(0);
            if (!canApplySingle(target, enchantments)) return false;
            var entry = enchantments.entrySet().iterator().next();
            var enchantment = entry.getKey();
            var existing = pairs(target, player.registryAccess());
            var singles = target.getOrDefault(CREATIVE_ENCHANTMENTS, ItemEnchantments.EMPTY);
            var result = target.is(Items.BOOK) ? target.transmuteCopy(Items.ENCHANTED_BOOK) : target.copy();
            var updated = new ItemEnchantments.Mutable(singles);
            updated.upgrade(enchantment, entry.getIntValue());
            result.set(CREATIVE_ENCHANTMENTS, updated.toImmutable());
            applyPairs(result, existing);
            result.remove(WORK);
            result.remove(WRITING_ID);
            result.remove(WRITING_TARGET);
            result.remove(WRITING_DRAFT);
            result.remove(LIBRARY_SINGLE);
            if (result.is(Items.ENCHANTED_BOOK)) result.set(REVEALED, true);
            else {
                result.remove(EMPOWER_COUNT);
                result.set(WORK, new Work(Optional.empty(), COMPLETE, 0, table.shelves()));
            }
            table.setItem(0, result);
            return true;
        }

        @Override public ItemStack quickMoveStack(Player player, int index) {
            if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
            var slot = slots.get(index);
            if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
            var stack = slot.getItem();
            if (index == 0) {
                // Shift-click inserts copies directly into inventory before onTake, so sanitize the transfer first.
                var transfer = stack.copy();
                clearPending(transfer);
                var taken = transfer.copy();
                if (!moveItemStackTo(transfer, 2, slots.size(), true)) return ItemStack.EMPTY;
                slot.setByPlayer(transfer.isEmpty() ? ItemStack.EMPTY : transfer);
                slot.onTake(player, taken);
                return taken;
            }
            var original = stack.copy();
            boolean moved;
            if (index < 2) moved = moveItemStackTo(stack, 2, slots.size(), true);
            else if (stack.is(Items.LAPIS_LAZULI)) moved = moveItemStackTo(stack, 1, 2, false);
            else moved = moveItemStackTo(stack, 0, 1, false);
            if (!moved) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
            slot.onTake(player, stack);
            return original;
        }
    }
}
