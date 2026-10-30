package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.fabricmc.fabric.api.client.rendering.v1.level.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.*;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class RemoteClient implements ClientModInitializer {
    private static List<RemotePayloads.Entry> entries = List.of();
    private static volatile List<AABB> markers = List.of();
    private static boolean requested;
    private static int ticks;
    public static List<RemotePayloads.Entry> entries() { return entries; }
    public static boolean available() { return Minecraft.getInstance().player != null && ClientPlayNetworking.canSend(RemotePayloads.Request.TYPE); }
    public static void send(RemotePayloads.Request request) { if (available()) ClientPlayNetworking.send(request); }
    @Override public void onInitializeClient() {
        var key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.remote_interaction.open", GLFW.GLFW_KEY_R,
                KeyMapping.Category.register(RemoteInteraction.id("controls"))));
        ClientPlayNetworking.registerGlobalReceiver(RemotePayloads.Snapshot.TYPE, (payload, context) -> {
            entries = payload.entries();
            if (context.client().gui.screen() instanceof RemoteScreen screen) screen.updated(payload.message());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> { entries = List.of(); markers = List.of(); requested = false; });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            dispatcher.register(ClientCommands.literal("remoteinteraction").executes(c -> { requested = true; return 1; }));
            dispatcher.register(ClientCommands.literal("ri").executes(c -> { requested = true; return 1; }));
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (key.consumeClick()) if (client.gui.screen() == null) requested = true;
            if (requested) {
                requested = false;
                if (client.player != null) client.gui.setScreen(new RemoteScreen(null));
            }
            // Only poll while the panel is open, or periodically for the small world markers.
            if (++ticks % (client.gui.screen() instanceof RemoteScreen ? 20 : 100) == 0) send(RemotePayloads.Request.list());
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
