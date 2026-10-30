package io.github.ruok0214.remoteinteraction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public final class RemotePayloads {
    private RemotePayloads() {}
    public enum Action { LIST, ACTIVATE, RENAME, REMOVE }
    public record Request(Action action, UUID id, String name) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(RemoteInteraction.id("request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (b, v) -> { b.writeEnum(v.action); b.writeUUID(v.id); b.writeUtf(v.name, Receiver.MAX_NAME); },
                b -> new Request(b.readEnum(Action.class), b.readUUID(), b.readUtf(Receiver.MAX_NAME)));
        public static Request list() { return new Request(Action.LIST, new UUID(0, 0), ""); }
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Entry(UUID id, Identifier dimension, BlockPos pos, Direction face, Identifier block,
                        String name, String status, String state, List<Integer> slots) {}
    public record Attach(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Attach> TYPE = new Type<>(RemoteInteraction.id("attach"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Attach> CODEC = StreamCodec.of(
                (b, v) -> b.writeBlockPos(v.pos), b -> new Attach(b.readBlockPos()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record SetSlot(UUID id, int slot, boolean enabled) implements CustomPacketPayload {
        public static final Type<SetSlot> TYPE = new Type<>(RemoteInteraction.id("set_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SetSlot> CODEC = StreamCodec.of(
                (b, v) -> { b.writeUUID(v.id); b.writeVarInt(v.slot); b.writeBoolean(v.enabled); },
                b -> new SetSlot(b.readUUID(), b.readVarInt(), b.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record ActivateSlot(int slot) implements CustomPacketPayload {
        public static final Type<ActivateSlot> TYPE = new Type<>(RemoteInteraction.id("activate_slot"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ActivateSlot> CODEC = StreamCodec.of(
                (b, v) -> b.writeVarInt(v.slot), b -> new ActivateSlot(b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Snapshot(List<Entry> entries, String message) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE = new Type<>(RemoteInteraction.id("snapshot_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of((b, v) -> {
            b.writeUtf(v.message, 64); b.writeVarInt(v.entries.size());
            for (var e : v.entries) {
                b.writeUUID(e.id); b.writeIdentifier(e.dimension); b.writeBlockPos(e.pos); b.writeEnum(e.face);
                b.writeIdentifier(e.block); b.writeUtf(e.name, Receiver.MAX_NAME);
                b.writeUtf(e.status, 64); b.writeUtf(e.state, 128);
                b.writeVarInt(e.slots.size()); for (int slot : e.slots) b.writeVarInt(slot);
            }
        }, b -> {
            String message = b.readUtf(64);
            int size = b.readVarInt();
            if (size < 0 || size > ReceiverData.MAX_PER_PLAYER) throw new IllegalArgumentException("Invalid receiver count");
            var entries = new ArrayList<Entry>(size);
            for (int i = 0; i < size; i++) entries.add(new Entry(b.readUUID(), b.readIdentifier(), b.readBlockPos(),
                    b.readEnum(Direction.class), b.readIdentifier(), b.readUtf(Receiver.MAX_NAME), b.readUtf(64), b.readUtf(128), readSlots(b)));
            return new Snapshot(List.copyOf(entries), message);
        });
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    private static List<Integer> readSlots(RegistryFriendlyByteBuf b) {
        int count = b.readVarInt();
        if (count < 0 || count > Receiver.SLOT_COUNT) throw new IllegalArgumentException("Invalid slot count");
        var slots = new ArrayList<Integer>();
        for (int i = 0; i < count; i++) {
            int slot = b.readVarInt();
            if (slot < 0 || slot >= Receiver.SLOT_COUNT) throw new IllegalArgumentException("Invalid slot");
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}
