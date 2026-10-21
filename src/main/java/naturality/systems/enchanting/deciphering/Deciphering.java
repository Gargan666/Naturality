package naturality.systems.enchanting.deciphering;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import naturality.Naturality;
import naturality.systems.enchanting.framework.Enchanting;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import org.jspecify.annotations.Nullable;

/** Saved word-search puzzle and authoritative reveal/submission rules. */
@org.jspecify.annotations.NullMarked
public final class Deciphering {
    public static final int COLUMNS = 20, ROWS = 13, CELLS = COLUMNS * ROWS;
    public static final int TYPE_TICKS = 48, FADE_TICKS = 24;
    public static final DataComponentType<Integer> ATTEMPT_BUDGET = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
        Naturality.id("deciphering_attempt_budget"), DataComponentType.<Integer>builder().persistent(Codec.intRange(3, Integer.MAX_VALUE)).build());
    public static final DataComponentType<Long> READY_AT = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
        Naturality.id("deciphering_ready_at"), DataComponentType.<Long>builder().persistent(Codec.LONG).build());
    public static final DataComponentType<Puzzle> PUZZLE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE,
        Naturality.id("deciphering_puzzle"), DataComponentType.<Puzzle>builder().persistent(Puzzle.CODEC).ignoreSwapAnimation().build());
    private static final net.minecraft.resources.Identifier WORDS_FILE = Naturality.id("enchanting/related_words.json");
    private static final Vocabulary DEFAULT_WORDS = defaults();
    private static volatile Vocabulary serverWords = DEFAULT_WORDS;
    private static volatile Vocabulary clientWords = DEFAULT_WORDS;

    private record Vocabulary(java.util.Map<String, List<String>> entries, List<String> filler) {
        static Vocabulary of(java.util.Map<String, List<String>> entries) {
            // Keep the two filler pools separate so their sizes do not affect the 50/50 choice.
            return new Vocabulary(java.util.Map.copyOf(entries), entries.entrySet().stream()
                .filter(entry -> !entry.getKey().equals("gibberish"))
                .flatMap(entry -> entry.getValue().stream()).distinct().toList());
        }
    }

    private static Vocabulary defaults() {
        try (var stream = Deciphering.class.getResourceAsStream("/data/naturality/enchanting/related_words.json")) {
            if (stream == null) throw new IllegalStateException("Missing bundled enchanting related words");
            try (var reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                return Vocabulary.of(parseWords(reader));
            }
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read bundled enchanting related words", exception);
        }
    }

    private static java.util.Map<String, List<String>> parseWords(java.io.Reader reader) {
        var root = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        var entries = new java.util.LinkedHashMap<String, List<String>>();
        for (var entry : root.entrySet()) {
            boolean gibberish = entry.getKey().equals("gibberish");
            if (!gibberish && (net.minecraft.resources.Identifier.tryParse(entry.getKey()) == null || !entry.getKey().contains(":")))
                throw new IllegalArgumentException("Expected namespaced enchantment ID: " + entry.getKey());
            var words = new java.util.ArrayList<String>();
            for (var element : entry.getValue().getAsJsonArray()) {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("Expected word string for " + entry.getKey());
                String word = element.getAsString().strip().toLowerCase(Locale.ROOT);
                if (!word.matches("[a-z]{1," + COLUMNS + "}"))
                    throw new IllegalArgumentException("Words must contain 1-" + COLUMNS + " English letters: " + entry.getKey());
                if (!words.contains(word)) words.add(word);
            }
            if (words.isEmpty() && !gibberish) throw new IllegalArgumentException("Empty word list for " + entry.getKey());
            entries.put(entry.getKey(), List.copyOf(words));
        }
        return entries;
    }

    private static void reloadWords(net.minecraft.server.MinecraftServer server,
            net.minecraft.server.packs.resources.ResourceManager resources) {
        var entries = new java.util.LinkedHashMap<>(DEFAULT_WORDS.entries());
        try {
            for (var resource : resources.getResourceStack(WORDS_FILE)) {
                try (var reader = resource.openAsReader()) { entries.putAll(parseWords(reader)); }
            }
            var updated = Vocabulary.of(entries);
            var payload = wordsPayload(updated);
            if (payload.json().length() > 65536) throw new IllegalArgumentException("Related word data exceeds 65536 characters");
            serverWords = updated;
            for (var player : server.getPlayerList().getPlayers()) ServerPlayNetworking.send(player, payload);
        } catch (java.io.IOException | RuntimeException exception) {
            Naturality.LOGGER.warn("Cannot load {}; keeping previous enchanting words", WORDS_FILE, exception);
        }
    }

    public record Words(String json) implements CustomPacketPayload {
        public static final Type<Words> TYPE = new Type<>(Naturality.id("enchanting_related_words"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Words> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(65536), Words::json, Words::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    private static Words wordsPayload(Vocabulary vocabulary) {
        var root = new com.google.gson.JsonObject();
        vocabulary.entries().forEach((id, words) -> {
            var array = new com.google.gson.JsonArray();
            words.forEach(array::add);
            root.add(id, array);
        });
        return new Words(root.toString());
    }

    public static void receiveWords(Words payload) {
        try { clientWords = Vocabulary.of(parseWords(new java.io.StringReader(payload.json()))); }
        catch (RuntimeException exception) { Naturality.LOGGER.warn("Invalid enchanting related words from server", exception); }
    }

    private Deciphering() {}

    public record Puzzle(long id, String text, String target, int revealed, int attempts, int revision) {
        public static final Codec<Puzzle> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("id").forGetter(Puzzle::id),
            Codec.STRING.fieldOf("text").forGetter(Puzzle::text),
            Codec.STRING.fieldOf("target").forGetter(Puzzle::target),
            Codec.intRange(0, (1 << 26) - 1).fieldOf("revealed").forGetter(Puzzle::revealed),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("attempts").forGetter(Puzzle::attempts),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf("revision").forGetter(Puzzle::revision)
        ).apply(i, Puzzle::new));
        public boolean visible(char letter) { return letter >= 'a' && letter <= 'z' && (revealed & (1 << (letter - 'a'))) != 0; }
        public boolean valid() {
            return text.length() == CELLS && text.chars().allMatch(c -> c == ' ' || c >= 'a' && c <= 'z')
                && !target.isEmpty() && target.length() <= COLUMNS && target.chars().allMatch(c -> c >= 'a' && c <= 'z');
        }
        public boolean matches(int start, int end) {
            int first = Math.min(start, end), last = Math.max(start, end);
            return first >= 0 && last < text.length() && first / COLUMNS == last / COLUMNS
                && (first % COLUMNS == 0 || text.charAt(first - 1) == ' ')
                && (last % COLUMNS == COLUMNS - 1 || text.charAt(last + 1) == ' ')
                && text.substring(first, last + 1).equals(target);
        }
    }

    public record Action(int menu, long puzzle, int revision, boolean submit, int start, int end) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(Naturality.id("deciphering_action"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Action::menu, ByteBufCodecs.LONG, Action::puzzle,
            ByteBufCodecs.VAR_INT, Action::revision, ByteBufCodecs.BOOL, Action::submit,
            ByteBufCodecs.VAR_INT, Action::start, ByteBufCodecs.VAR_INT, Action::end, Action::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(Words.TYPE, Words.CODEC);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            serverWords = DEFAULT_WORDS;
            reloadWords(server, server.getResourceManager());
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resources, success) -> {
            if (success) reloadWords(server, resources);
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, _, _) ->
            ServerPlayNetworking.send(handler.player, wordsPayload(serverWords)));
        PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE, (action, context) -> {
            var player = context.player();
            if (player.containerMenu instanceof Enchanting.Menu menu && menu.containerId == action.menu())
                act(menu, action);
        });
    }

    public static String clue(Holder<Enchantment> enchantment) {
        return String.join(" ", words(enchantment, clientWords));
    }

    private static List<String> words(Holder<Enchantment> enchantment, Vocabulary vocabulary) {
        String id = Enchanting.name(enchantment);
        var configured = vocabulary.entries().get(id);
        if (configured != null) return configured;
        return Arrays.stream(id.substring(id.indexOf(':') + 1).split("[^a-z]+"))
            .filter(word -> !word.isEmpty()).map(word -> word.substring(0, Math.min(COLUMNS, word.length()))).distinct().toList();
    }

    public static @Nullable Puzzle puzzle(Enchanting.Menu menu) {
        var puzzle = menu.subject().get(PUZZLE);
        return puzzle != null && puzzle.valid() ? puzzle : null;
    }

    public static void prepare(Enchanting.Menu menu, RandomSource random) {
        if (menu.phase() != Enchanting.DECIPHER && menu.phase() != Enchanting.EMPOWERING) {
            if (menu.subject().has(PUZZLE)) { menu.subject().remove(PUZZLE); menu.table.setChanged(); }
            return;
        }
        var work = menu.work();
        if (work == null) return;
        Holder<Enchantment> benefit;
        if (work.offer().isPresent()) benefit = work.offer().orElseThrow().benefit();
        else if (menu.pendingSingle().size() == 1) benefit = menu.pendingSingle().keySet().iterator().next();
        else return;
        String enchantment = Enchanting.name(benefit);
        var existing = puzzle(menu);
        if (existing != null) {
            menu.table.rememberClue(enchantment, existing.target());
            // Untouched saved puzzles may still contain the retired automatic shelf reveals.
            if (existing.revision() == 0 && existing.revealed() != 0) {
                menu.subject().set(PUZZLE, new Puzzle(existing.id(), existing.text(), existing.target(), 0, existing.attempts(), 0));
                menu.table.setChanged();
            }
            return;
        }
        var candidates = words(benefit, serverWords);
        if (candidates.isEmpty()) return;
        var generated = generate(random, candidates, menu.table.lastClue(enchantment));
        int budget = menu.subject().getOrDefault(ATTEMPT_BUDGET, 3);
        generated = new Puzzle(generated.id(), generated.text(), generated.target(), 0, budget, 0);
        menu.subject().set(READY_AT, menu.gameTime() + TYPE_TICKS + (budget > 3 ? FADE_TICKS : 0));
        menu.subject().set(PUZZLE, generated);
        menu.table.rememberClue(enchantment, generated.target());
        menu.table.setChanged();
    }

    public static Puzzle generate(RandomSource random, List<String> candidates) {
        return generate(random, candidates, null);
    }

    public static Puzzle generate(RandomSource random, List<String> candidates, @Nullable String previous) {
        var alternatives = candidates.stream().filter(word -> !word.equals(previous)).toList();
        var choices = alternatives.isEmpty() ? candidates : alternatives;
        String target = choices.get(random.nextInt(choices.size()));
        var vocabulary = serverWords;
        var gibberish = vocabulary.entries().getOrDefault("gibberish", List.of()).stream()
            .filter(word -> !candidates.contains(word)).toList();
        var filler = vocabulary.filler().stream()
            .filter(word -> !candidates.contains(word) && !gibberish.contains(word)).toList();
        char[] text = new char[CELLS];
        Arrays.fill(text, ' ');
        int targetRow = random.nextInt(ROWS);
        int targetColumn = random.nextInt(COLUMNS - target.length() + 1);
        for (int row = 0; row < ROWS; row++) {
            int cursor = row * COLUMNS;
            while (cursor < (row + 1) * COLUMNS) {
                if (row == targetRow && cursor % COLUMNS >= Math.max(0, targetColumn - 1)
                        && cursor % COLUMNS <= targetColumn + target.length()) { cursor++; continue; }
                int available = (row + 1) * COLUMNS - cursor;
                if (row == targetRow && cursor % COLUMNS < targetColumn) available = Math.min(available, targetColumn - 1 - cursor % COLUMNS);
                if (available <= 0) { cursor++; continue; }
                int room = available;
                var fitting = filler.stream().filter(word -> word.length() <= room).toList();
                var fittingGibberish = gibberish.stream().filter(word -> word.length() <= room).toList();
                var pool = fittingGibberish.isEmpty() ? fitting
                    : fitting.isEmpty() || random.nextBoolean() ? fittingGibberish : fitting;
                if (pool.isEmpty()) { cursor += available; continue; }
                String word = pool.get(random.nextInt(pool.size()));
                word.getChars(0, word.length(), text, cursor);
                cursor += word.length() + 1;
            }
        }
        target.getChars(0, target.length(), text, targetRow * COLUMNS + targetColumn);
        return new Puzzle(random.nextLong(), new String(text), target, 0, 3, 0);
    }

    public static void act(Enchanting.Menu menu, Action action) {
        if (!menu.canDecipher() || action.menu() != menu.containerId) return;
        if (menu.gameTime() < menu.subject().getOrDefault(READY_AT, 0L)) return;
        var puzzle = puzzle(menu);
        if (puzzle == null || puzzle.id() != action.puzzle() || puzzle.revision() != action.revision()) return;
        if (action.start() < 0 || action.start() >= CELLS || action.end() < 0 || action.end() >= CELLS) return;
        if (action.submit()) {
            if (puzzle.matches(action.start(), action.end())) menu.finishDeciphering(true);
            else if (puzzle.attempts() == 0) menu.finishDeciphering(false);
            else spendAttempt(menu, puzzle, puzzle.revealed());
            return;
        }
        char letter = puzzle.text().charAt(action.start());
        if (puzzle.attempts() == 0 || action.start() != action.end() || letter < 'a' || letter > 'z' || puzzle.visible(letter)) return;
        spendAttempt(menu, puzzle, puzzle.revealed() | (1 << (letter - 'a')));
        menu.playRevealSound();
    }

    private static void spendAttempt(Enchanting.Menu menu, Puzzle puzzle, int revealed) {
        menu.subject().set(PUZZLE, new Puzzle(puzzle.id(), puzzle.text(), puzzle.target(),
            revealed, puzzle.attempts() - 1, puzzle.revision() + 1));
        menu.table.setChanged();
        menu.broadcastChanges();
    }
}
