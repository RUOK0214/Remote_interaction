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
    private static boolean autoAssign;
    private static boolean placing;
    private static boolean suppressUseUntilRelease;
    private static ShortcutSettings shortcuts;
    private static QuickLayoutSettings layout;
    private static MarkerSettings markerSettings;
    private static int ticks;
    public static List<RemotePayloads.Entry> entries() { return entries; }
    public static ShortcutSettings shortcuts() { return shortcuts; }
    public static QuickLayoutSettings layout() { return layout; }
    public static MarkerSettings markerSettings() { return markerSettings; }
    public static boolean autoAssign() { return autoAssign; }
    public static boolean canAutoAssign() { return available() && ClientPlayNetworking.canSend(RemotePayloads.SetAutoAssign.TYPE); }
    public static boolean available() { return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(RemotePayloads.Attach.TYPE); }
    public static boolean canRemoveAll() { return available() && ClientPlayNetworking.canSend(RemotePayloads.RemoveAll.TYPE); }
    public static boolean canRemoveMissing() { return available() && ClientPlayNetworking.canSend(RemotePayloads.RemoveMissing.TYPE); }
    public static long missingCount() { return entries.stream().filter(e -> e.status().equals("missing")).count(); }
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
        if (suppressUseUntilRelease) return true;
        if (!placing || mc.player == null || mc.gui.screen() != null) return false;
        // startUseItem repeats while use is held. The entire attachment press belongs to placement,
        // including repeat ticks and queued clicks after placement finishes or is rejected.
        suppressUseUntilRelease = true;
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            send(new RemotePayloads.Attach(hit.getBlockPos())); placing = false;
        } else mc.player.sendOverlayMessage(RemoteScreen.tr("look_at_block"));
        return true;
    }
    @Override public void onInitializeClient() {
        shortcuts = ShortcutSettings.load();
        layout = QuickLayoutSettings.load();
        markerSettings = MarkerSettings.load();
        ClientPlayNetworking.registerGlobalReceiver(RemotePayloads.Snapshot.TYPE, (payload, context) -> {
            entries = payload.entries(); autoAssign = payload.autoAssign();
            if (context.client().gui.screen() instanceof RemoteScreen screen) screen.updated(payload.message());
            if (context.client().gui.screen() instanceof QuickSettingsScreen screen) screen.updated();
            if (context.client().gui.screen() instanceof QuickLayoutScreen screen) screen.updated();
            if (context.client().gui.screen() instanceof QuickScreen screen) screen.updated();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { entries = List.of(); markers = List.of(); requested = placing = suppressUseUntilRelease = autoAssign = false; });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("remoteinteraction").executes(c -> { requested = true; return 1; }));
            dispatcher.register(ClientCommands.literal("ri").executes(c -> { requested = true; return 1; }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Respect Minecraft's configurable use binding, including keyboard bindings.
            if (!client.options.keyUse.isDown()) suppressUseUntilRelease = false;
            if (!client.isWindowActive() || client.player == null || !client.player.isAlive()) {
                placing = false;
                if (client.gui.screen() instanceof QuickScreen) client.gui.setScreen(null);
            }
            if (requested) {
                requested = false;
                if (client.player != null) client.gui.setScreen(new RemoteScreen(null));
            }
            // Poll open panels and periodically refresh world markers.
            boolean open = client.gui.screen() instanceof RemoteScreen || client.gui.screen() instanceof QuickScreen || client.gui.screen() instanceof QuickSettingsScreen || client.gui.screen() instanceof QuickLayoutScreen;
            if (++ticks % (open ? 10 : 100) == 0) send(RemotePayloads.Request.list());
        });
        LevelExtractionEvents.END_EXTRACTION.register(context -> markers = extractMarkers());
        LevelRenderEvents.BEFORE_GIZMOS.register(context -> {
            var appearance=markerSettings.current();
            if(appearance.transparency()==100)return;
            try (var ignored = context.levelRenderer().collectPerFrameRenderThreadGizmos()) {
                for (var box : markers) Gizmos.cuboid(box, appearance.style());
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
            // Use the grid cell, independent of the target's collision shape, facing, or powered state.
            // A tiny expansion prevents coincident surfaces from flickering on full blocks.
            result.add(new AABB(e.pos()).inflate(.002));
        }
        return List.copyOf(result);
    }
}
