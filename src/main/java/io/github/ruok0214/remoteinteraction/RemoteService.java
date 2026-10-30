package io.github.ruok0214.remoteinteraction;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class RemoteService {
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>(), LAST_LIST = new HashMap<>(), LAST_EDIT = new HashMap<>();
    private RemoteService() {}
    public static void forget(UUID player) { LAST_ACTION.remove(player); LAST_LIST.remove(player); LAST_EDIT.remove(player); }
    private static boolean allow(ServerPlayer player, Map<UUID, Long> timer, int interval) {
        long tick = player.level().getServer().getTickCount();
        Long last = timer.get(player.getUUID());
        if (last != null && tick - last < interval) return false;
        timer.put(player.getUUID(), tick); return true;
    }
    private static boolean editable(ServerPlayer player) { return player.isAlive() && !player.isSpectator() && player.mayBuild(); }
    public static boolean supported(BlockState state) {
        var b = state.getBlock();
        return b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof FenceGateBlock
                || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof NoteBlock
                || b instanceof BellBlock || b instanceof DaylightDetectorBlock
                || b instanceof DoorBlock door && door.type().canOpenByHand()
                || b instanceof TrapDoorBlock && b != Blocks.IRON_TRAPDOOR;
    }
    public static void attach(ServerPlayer player, BlockPos requested) {
        if (!allow(player, LAST_EDIT, 1)) return;
        var level = player.level();
        // A requested coordinate must match the server's current sight line and ordinary interaction reach.
        var eye = player.getEyePosition();
        var hit = level.clip(new ClipContext(eye, eye.add(player.getViewVector(1).scale(player.blockInteractionRange())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        var pos = hit.getBlockPos();
        if (hit.getType() != HitResult.Type.BLOCK || !pos.equals(requested) || !level.hasChunkAt(pos) || !player.isAlive()
                || player.isSpectator() || !player.mayBuild() || !player.isWithinBlockInteractionRange(pos, 0)
                || !player.mayInteract(level, pos) || !level.mayInteract(player, pos)
                || !level.getWorldBorder().isWithinBounds(pos)) { tell(player, "denied"); return; }
        if (UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit) != InteractionResult.PASS) {
            tell(player, "denied"); return;
        }
        var state = level.getBlockState(pos);
        if (!supported(state)) { tell(player, "unsupported"); return; }
        if (state.getBlock() instanceof DoorBlock && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            var door = state.getBlock(); pos = pos.below(); state = level.getBlockState(pos);
            if (!state.is(door) || !player.mayInteract(level, pos) || !level.mayInteract(player, pos)) { tell(player, "denied"); return; }
        }
        var data = ReceiverData.get(level.getServer());
        if (data.at(level.dimension().identifier(), pos) != null) { tell(player, "already_attached"); return; }
        var receiver = new Receiver(UUID.randomUUID(), player.getUUID(), level.dimension().identifier(), pos.immutable(),
                hit.getDirection(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), "", false, false);
        if (!data.add(receiver)) { tell(player, "limit"); return; }
        sync(player, "attached"); tell(player, "attached");
    }
    private static void tell(ServerPlayer player, String key) {
        player.sendOverlayMessage(Component.translatable("remote_interaction.message." + key));
    }
    public static String status(ServerPlayer player, Receiver r) {
        if (r.lost()) return "missing";
        if (!player.level().dimension().identifier().equals(r.dimension())) return "dimension";
        var level = player.level();
        if (!level.hasChunkAt(r.pos())) return "unloaded";
        if (!BuiltInRegistries.BLOCK.getKey(level.getBlockState(r.pos()).getBlock()).equals(r.block())) return "missing";
        if (!player.isAlive() || player.isSpectator() || !player.mayBuild() || !player.mayInteract(level, r.pos())
                || !level.mayInteract(player, r.pos()) || !level.getWorldBorder().isWithinBounds(r.pos())) return "denied";
        return "ready";
    }
    public static String state(BlockState s) {
        if (s.hasProperty(BlockStateProperties.OPEN)) return s.getValue(BlockStateProperties.OPEN) ? "open" : "closed";
        if (s.hasProperty(BlockStateProperties.DELAY)) return "delay_" + s.getValue(BlockStateProperties.DELAY);
        if (s.hasProperty(BlockStateProperties.MODE_COMPARATOR)) return s.getValue(BlockStateProperties.MODE_COMPARATOR).getSerializedName();
        if (s.hasProperty(BlockStateProperties.NOTE)) return "note_" + s.getValue(BlockStateProperties.NOTE);
        if (s.hasProperty(BlockStateProperties.INVERTED)) return s.getValue(BlockStateProperties.INVERTED) ? "inverted" : "normal";
        if (s.hasProperty(BlockStateProperties.POWERED)) return s.getValue(BlockStateProperties.POWERED) ? "on" : "off";
        return "ready";
    }
    public static void sync(ServerPlayer player, String message) {
        if (!ServerPlayNetworking.canSend(player, RemotePayloads.Snapshot.TYPE)) return;
        var entries = new ArrayList<RemotePayloads.Entry>();
        for (var r : ReceiverData.get(player.level().getServer()).owned(player.getUUID())) {
            String status = status(player, r);
            entries.add(new RemotePayloads.Entry(r.id(), r.dimension(), r.pos(), r.face(), r.block(), r.name(), status,
                    status.equals("ready") ? state(player.level().getBlockState(r.pos())) : "", r.slots()));
        }
        ServerPlayNetworking.send(player, new RemotePayloads.Snapshot(List.copyOf(entries), message));
    }
    public static void request(ServerPlayer player, RemotePayloads.Request request) {
        var timer = request.action() == RemotePayloads.Action.LIST ? LAST_LIST : request.action() == RemotePayloads.Action.ACTIVATE ? LAST_ACTION : LAST_EDIT;
        int interval = request.action() == RemotePayloads.Action.LIST ? 10 : request.action() == RemotePayloads.Action.ACTIVATE ? 4 : 1;
        if (!allow(player, timer, interval)) return;
        if (request.action() == RemotePayloads.Action.LIST) { sync(player, ""); return; }
        var data = ReceiverData.get(player.level().getServer());
        var receiver = data.get(request.id());
        if (receiver == null || !receiver.owner().equals(player.getUUID()) || player.isSpectator()
                || !player.isAlive() || !player.mayBuild()) { sync(player, "denied"); return; }
        switch (request.action()) {
            case ACTIVATE -> sync(player, activate(player, receiver));
            case RENAME -> { data.update(receiver.renamed(request.name())); sync(player, "renamed"); }
            case REMOVE -> {
                data.remove(receiver.id());
                sync(player, "removed");
            }
            default -> { }
        }
    }
    public static void setSlot(ServerPlayer player, RemotePayloads.SetSlot request) {
        if (!allow(player, LAST_EDIT, 1)) return;
        var data = ReceiverData.get(player.level().getServer());
        var receiver = data.get(request.id());
        if (!editable(player) || request.slot() < 0 || request.slot() >= Receiver.SLOT_COUNT || receiver == null
                || !receiver.owner().equals(player.getUUID())) { sync(player, "denied"); return; }
        data.update(receiver.withSlot(request.slot(), request.enabled())); sync(player, "slot_saved");
    }
    public static void removeAll(ServerPlayer player) {
        if (!allow(player, LAST_EDIT, 1)) return;
        if (!editable(player)) { sync(player, "denied"); return; }
        ReceiverData.get(player.level().getServer()).removeOwned(player.getUUID());
        sync(player, "all_removed");
    }
    public static void activateSlot(ServerPlayer player, int slot) {
        if (!allow(player, LAST_ACTION, 4)) return;
        if (!editable(player) || slot < 0 || slot >= Receiver.SLOT_COUNT) { sync(player, "denied"); return; }
        int total = 0, succeeded = 0;
        // The server resolves the whole saved group, so cooldowns cannot discard later members.
        for (var receiver : ReceiverData.get(player.level().getServer()).owned(player.getUUID())) {
            if (!receiver.slots().contains(slot)) continue;
            total++;
            if (activate(player, receiver).equals("activated")) succeeded++;
        }
        sync(player, succeeded == 0 ? "group_unavailable" : succeeded == total ? "group_activated" : "group_partial");
    }
    public static String activate(ServerPlayer player, Receiver receiver) {
        // Re-resolve the server-owned entry; never trust coordinates or ownership from the client.
        var stored = ReceiverData.get(player.level().getServer()).get(receiver.id());
        if (stored == null || !stored.owner().equals(player.getUUID())) return "denied";
        receiver = stored;
        String status = status(player, receiver);
        if (!status.equals("ready")) return status;
        ServerLevel level = player.level();
        BlockState state = level.getBlockState(receiver.pos());
        if (!supported(state)) return "unsupported";
        // Bells only accept a horizontal hit along their mounting axis, even if the receiver was attached on top.
        var face = state.getBlock() instanceof BellBlock ? state.getValue(BellBlock.FACING) : receiver.face();
        var hit = new BlockHitResult(Vec3.atCenterOf(receiver.pos()).relative(face, .5), face, receiver.pos(), false);
        var result = UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
        if (result != InteractionResult.PASS) return "denied";
        result = state.useWithoutItem(level, player, hit);
        return result.consumesAction() ? "activated" : "no_change";
    }
}
