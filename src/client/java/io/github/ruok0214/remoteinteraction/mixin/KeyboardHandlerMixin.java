package io.github.ruok0214.remoteinteraction.mixin;

import io.github.ruok0214.remoteinteraction.client.RemoteInput;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void remoteInteraction$key(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle() && RemoteInput.handle(action, event)) ci.cancel();
    }
}
