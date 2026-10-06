package naturality.villager;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import naturality.Naturality;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/** One world-wide score per player and one cure reward per villager lineage. */
public final class ReputationData extends SavedData {
    public static final SavedDataType<ReputationData> TYPE = new SavedDataType<>(Naturality.id("reputation"),
        ReputationData::new, Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(ReputationData::new, data -> data.values), DataFixTypes.LEVEL);
    private final Map<String, Integer> values;
    public ReputationData() { values = new HashMap<>(); }
    private ReputationData(Map<String, Integer> values) { this.values = new HashMap<>(values); }
    public static ReputationData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public int score(UUID player) { return Math.clamp(values.getOrDefault("player:" + player, 50), 0, 100); }
    public int change(UUID player, int amount) {
        int score = Math.clamp(score(player) + amount, 0, 100);
        values.put("player:" + player, score); setDirty(); return score;
    }
    public void queueHero(UUID player) { values.put("hero:" + player, 1); setDirty(); }
    public boolean takeHero(UUID player) { boolean pending = values.remove("hero:" + player) != null; if (pending) setDirty(); return pending; }
    public boolean firstCure(UUID lineage) {
        if (values.putIfAbsent("cured:" + lineage, 1) != null) return false;
        setDirty(); return true;
    }
}
