package io.github.ruok0214.remoteinteraction;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.*;

public final class ReceiverData extends SavedData {
    public static final int MAX_PER_PLAYER = 128;
    public static final Codec<ReceiverData> CODEC = Receiver.CODEC.listOf().xmap(ReceiverData::new, ReceiverData::all);
    public static final SavedDataType<ReceiverData> TYPE = new SavedDataType<>(RemoteInteraction.id("receivers"),
            ReceiverData::new, CODEC, null);
    private final Map<UUID, Receiver> entries = new LinkedHashMap<>();
    private final Map<Target, UUID> targets = new HashMap<>();
    private record Target(Identifier dimension, BlockPos pos) {}
    public ReceiverData() {}
    private ReceiverData(List<Receiver> values) { for (var r : values) insert(r); }
    public static ReceiverData get(MinecraftServer server) { return server.getDataStorage().computeIfAbsent(TYPE); }
    public List<Receiver> all() { return List.copyOf(entries.values()); }
    public List<Receiver> owned(UUID owner) { return entries.values().stream().filter(r -> r.owner().equals(owner)).toList(); }
    public Receiver get(UUID id) { return entries.get(id); }
    public Receiver at(Identifier dimension, BlockPos pos) { return entries.get(targets.get(new Target(dimension, pos))); }
    private void insert(Receiver value) {
        var old = entries.put(value.id(), value);
        if (old != null) targets.remove(new Target(old.dimension(), old.pos()), old.id());
        if (!value.lost()) targets.put(new Target(value.dimension(), value.pos()), value.id());
    }
    public boolean add(Receiver value) {
        if (entries.containsKey(value.id()) || at(value.dimension(), value.pos()) != null
                || owned(value.owner()).size() >= MAX_PER_PLAYER) return false;
        insert(value); setDirty(); return true;
    }
    public void update(Receiver value) { if (entries.containsKey(value.id())) { insert(value); setDirty(); } }
    public Receiver remove(UUID id) {
        var r = entries.remove(id);
        if (r != null) { targets.remove(new Target(r.dimension(), r.pos()), r.id()); setDirty(); }
        return r;
    }
    public void removeOwned(UUID owner) {
        // Remove only this player's registrations, including missing/unloaded targets and other dimensions.
        for (var receiver : owned(owner)) remove(receiver.id());
    }
    public void targetRemoved(Identifier dimension, BlockPos pos) {
        var r = at(dimension, pos);
        if (r != null) update(r.missing());
    }
}
