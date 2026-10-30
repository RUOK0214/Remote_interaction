package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import java.util.*;

public final class RemoteClient implements ClientModInitializer {
    private static List<RemotePayloads.Entry> entries = List.of();
    private static volatile List<AABB> markers = List.of();
    private static boolean requested;
    private static boolean placing;
    private static ShortcutSettings shortcuts;
    private static QuickLayoutSettings layout;
    private static int ticks;
    public static List<RemotePayloads.Entry> entries() { return entries; }
    public static ShortcutSettings shortcuts() { return shortcuts; }
    public static QuickLayoutSettings layout() { return layout; }
    public static boolean available() { return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(RemotePayloads.Attach.TYPE); }
    public static void send(CustomPacketPayload request) { if (available()) ClientPlayNetworking.send(request); }
    public static boolean placing() { return placing; }
    public static void cancelPlacing() { placing = false; }
    public static void startPlacing() {
        var mc = Minecraft.getInstance();
        if (!available()) { if (mc.player != null) mc.player.sendOverlayMessage(RemoteScreen.tr("server_required")); return; }
        placing = !placing; mc.gui.setScreen(null);
        mc.player.sendOverlayMessage(RemoteScreen.tr(placing ? "place_hint" : "place_cancelled"));
    }
    public static boolean consumeUse() {
        var mc = Minecraft.getInstance();
        if (!placing || mc.player == null || mc.gui.screen() != null) return false;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            send(new RemotePayloads.Attach(hit.getBlockPos())); placing = false;
        } else mc.player.sendOverlayMessage(RemoteScreen.tr("look_at_block"));
        return true;
    }
    @Override public void onInitializeClient() {
        shortcuts = ShortcutSettings.load();
        layout = QuickLayoutSettings.load();
        ClientPlayNetworking.registerGlobalReceiver(RemotePayloads.Snapshot.TYPE, (payload, context) -> {
            entries = payload.entries();
            if (context.client().gui.screen() instanceof RemoteScreen screen) screen.updated(payload.message());
            if (context.client().gui.screen() instanceof QuickSettingsScreen screen) screen.updated();
            if (context.client().gui.screen() instanceof QuickScreen screen) screen.updated();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { entries = List.of(); markers = List.of(); requested = placing = false; });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("remoteinteraction").executes(c -> { requested = true; return 1; }));
            dispatcher.register(ClientCommands.literal("ri").executes(c -> { requested = true; return 1; }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!client.isWindowActive() || client.player == null || !client.player.isAlive()) {
                placing = false;
                if (client.gui.screen() instanceof QuickScreen) client.gui.setScreen(null);
            }
            if (requested) {
                requested = false;
                if (client.player != null) client.gui.setScreen(new RemoteScreen(null));
            }
            // Only poll while the panel is open, or periodically for the small world markers.
            boolean open = client.gui.screen() instanceof RemoteScreen || client.gui.screen() instanceof QuickScreen || client.gui.screen() instanceof QuickSettingsScreen || client.gui.screen() instanceof QuickLayoutScreen;
            if (++ticks % (open ? 10 : 100) == 0) send(RemotePayloads.Request.list());
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> markers = extractMarkers());
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (var box : markers) Gizmos.cuboid(box, GizmoStyle.stroke(0xFF43E7D4, 2.5f));
            }
        });
    }
    public static List<AABB> extractMarkers() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return List.of();
        var result = new ArrayList<AABB>();
        for (var e : entries) {
            if (e.status().equals("missing") || !e.dimension().equals(mc.level.dimension().identifier())
                    || !mc.level.hasChunkAt(e.pos()) || e.pos().distToCenterSqr(mc.player.position()) > 64 * 64) continue;
            var state = mc.level.getBlockState(e.pos());
            if (!BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(e.block())) continue;
            var shape = state.getShape(mc.level, e.pos());
            if (shape.isEmpty()) continue;
            var bounds = shape.bounds();
            var center = bounds.getCenter();
            double x = center.x, y = center.y, z = center.z;
            switch (e.face()) {
                case UP -> y = bounds.maxY + .015;
                case DOWN -> y = bounds.minY - .015;
                case EAST -> x = bounds.maxX + .015;
                case WEST -> x = bounds.minX - .015;
                case SOUTH -> z = bounds.maxZ + .015;
                case NORTH -> z = bounds.minZ - .015;
            }
            double dx = e.face().getStepX() == 0 ? .075 : .025;
            double dy = e.face().getStepY() == 0 ? .075 : .025;
            double dz = e.face().getStepZ() == 0 ? .075 : .025;
            result.add(new AABB(x-dx, y-dy, z-dz, x+dx, y+dy, z+dz).move(e.pos()));
        }
        return List.copyOf(result);
    }
}
