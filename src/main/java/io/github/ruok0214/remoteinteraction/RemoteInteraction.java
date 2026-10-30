package io.github.ruok0214.remoteinteraction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.resources.Identifier;

public final class RemoteInteraction implements ModInitializer {
    public static final String MOD_ID = "remote_interaction";
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    @Override public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.Request.TYPE, RemotePayloads.Request.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.Attach.TYPE, RemotePayloads.Attach.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.RemoveAll.TYPE, RemotePayloads.RemoveAll.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.SetSlot.TYPE, RemotePayloads.SetSlot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.ActivateSlot.TYPE, RemotePayloads.ActivateSlot.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RemotePayloads.Snapshot.TYPE, RemotePayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.Request.TYPE, (payload, context) ->
                RemoteService.request(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.Attach.TYPE, (payload, context) -> RemoteService.attach(context.player(), payload.pos()));
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.RemoveAll.TYPE, (payload, context) -> RemoteService.removeAll(context.player()));
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.SetSlot.TYPE, (payload, context) -> RemoteService.setSlot(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.ActivateSlot.TYPE, (payload, context) -> RemoteService.activateSlot(context.player(), payload.slot()));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RemoteService.sync(handler.player, ""));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RemoteService.forget(handler.player.getUUID()));
    }
}
