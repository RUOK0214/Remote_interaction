package io.github.ruok0214.remoteinteraction;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class RemoteService {
    private static final ThreadLocal<Boolean> REMOTE_USE = ThreadLocal.withInitial(() -> false);
    private static final Map<UUID, Long> LAST_ACTION = new HashMap<>(), LAST_LIST = new HashMap<>();
    private RemoteService() {}
    public static boolean remoteUse() { return REMOTE_USE.get(); }
    public static void forget(UUID player) { LAST_ACTION.remove(player); LAST_LIST.remove(player); }
    public static boolean supported(BlockState state) {
        var b = state.getBlock();
        return b instanceof ButtonBlock || b instanceof LeverBlock || b instanceof FenceGateBlock
                || b instanceof RepeaterBlock || b instanceof ComparatorBlock || b instanceof NoteBlock
                || b instanceof BellBlock || b instanceof DaylightDetectorBlock
                || b instanceof DoorBlock door && door.type().canOpenByHand()
                || b instanceof TrapDoorBlock && b != Blocks.IRON_TRAPDOOR;
    }
    public static void attach(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        var level = player.level();
        var pos = hit.getBlockPos();
        if (!player.getItemInHand(hand).is(RemoteInteraction.RECEIVER) || !player.isAlive()
                || player.isSpectator() || !player.mayBuild() || !player.isWithinBlockInteractionRange(pos, 0)
                || !player.mayInteract(level, pos) || !level.mayInteract(player, pos)
                || !level.getWorldBorder().isWithinBounds(pos)) { tell(player, "denied"); return; }
        REMOTE_USE.set(true);
        try {
            if (UseBlockCallback.EVENT.invoker().interact(player, level, hand, hit) != InteractionResult.PASS) {
                tell(player, "denied"); return;
            }
        } finally { REMOTE_USE.remove(); }
        var state = level.getBlockState(pos);
        if (!supported(state)) { tell(player, "unsupported"); return; }
        if (state.getBlock() instanceof DoorBlock && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
            pos = pos.below(); state = level.getBlockState(pos);
        }
        var data = ReceiverData.get(level.getServer());
        if (data.at(level.dimension().identifier(), pos) != null) { tell(player, "already_attached"); return; }
        boolean paid = !player.getAbilities().instabuild;
        var receiver = new Receiver(UUID.randomUUID(), player.getUUID(), level.dimension().identifier(), pos.immutable(),
                hit.getDirection(), BuiltInRegistries.BLOCK.getKey(state.getBlock()), "", paid, false);
        if (!data.add(receiver)) { tell(player, "limit"); return; }
        if (paid) player.getItemInHand(hand).shrink(1);
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
                    status.equals("ready") ? state(player.level().getBlockState(r.pos())) : ""));
        }
        ServerPlayNetworking.send(player, new RemotePayloads.Snapshot(List.copyOf(entries), message));
    }
    public static void request(ServerPlayer player, RemotePayloads.Request request) {
        long tick = player.level().getServer().getTickCount();
        var timer = request.action() == RemotePayloads.Action.LIST ? LAST_LIST : LAST_ACTION;
        int interval = request.action() == RemotePayloads.Action.LIST ? 10 : 4;
        Long last = timer.get(player.getUUID());
        if (last != null && tick - last < interval) return;
        timer.put(player.getUUID(), tick);
        if (request.action() == RemotePayloads.Action.LIST) { sync(player, ""); return; }
        var data = ReceiverData.get(player.level().getServer());
        var receiver = data.get(request.id());
        if (receiver == null || !receiver.owner().equals(player.getUUID()) || player.isSpectator()
                || !player.isAlive() || !player.mayBuild()) { sync(player, "denied"); return; }
        switch (request.action()) {
            case ACTIVATE -> sync(player, activate(player, receiver));
            case RENAME -> { data.update(receiver.renamed(request.name())); sync(player, "renamed"); }
            case REMOVE -> {
                // Delete first: a repeated/stale packet cannot refund the receiver twice.
                data.remove(receiver.id());
                if (receiver.paid()) {
                    var stack = new ItemStack(RemoteInteraction.RECEIVER);
                    if (!player.getInventory().add(stack)) player.drop(stack, false);
                }
                sync(player, "removed");
            }
            default -> { }
        }
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
        REMOTE_USE.set(true);
        try {
            // Give protection mods using the Fabric interaction event the opportunity to veto.
            var result = UseBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, hit);
            if (result != InteractionResult.PASS) return "denied";
            result = state.useWithoutItem(level, player, hit);
            return result.consumesAction() ? "activated" : "no_change";
        } finally { REMOTE_USE.remove(); }
    }
}
