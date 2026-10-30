package io.github.ruok0214.remoteinteraction;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class RemoteInteraction implements ModInitializer {
    public static final String MOD_ID = "remote_interaction";
    public static Item RECEIVER;
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }
    @Override public void onInitialize() {
        var key = ResourceKey.create(Registries.ITEM, id("receiver"));
        RECEIVER = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> entries.accept(RECEIVER));
        PayloadTypeRegistry.serverboundPlay().register(RemotePayloads.Request.TYPE, RemotePayloads.Request.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RemotePayloads.Snapshot.TYPE, RemotePayloads.Snapshot.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RemotePayloads.Request.TYPE, (payload, context) ->
                RemoteService.request(context.player(), payload));
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> RemoteService.sync(handler.player, ""));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RemoteService.forget(handler.player.getUUID()));
        // Intercept before vanilla toggles the block; sneaking is not required for attachment.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (RemoteService.remoteUse() || !player.getItemInHand(hand).is(RECEIVER)) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer) RemoteService.attach(serverPlayer, hand, hit);
            return InteractionResult.SUCCESS;
        });
    }
}
