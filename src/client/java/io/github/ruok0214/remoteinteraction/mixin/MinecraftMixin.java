package io.github.ruok0214.remoteinteraction.mixin;

import io.github.ruok0214.remoteinteraction.client.RemoteClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void remoteInteraction$attach(CallbackInfo ci) { if (RemoteClient.consumeUse()) ci.cancel(); }
}
