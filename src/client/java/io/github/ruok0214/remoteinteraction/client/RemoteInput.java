package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import java.util.*;

public final class RemoteInput {
    private RemoteInput() {}
    public static int modifiers() {
        var window=Minecraft.getInstance().getWindow();
        var held=new HashSet<Integer>();
        for(int key=GLFW.GLFW_KEY_LEFT_SHIFT;key<=GLFW.GLFW_KEY_RIGHT_SUPER;key++)
            if(InputConstants.isKeyDown(window,key))held.add(key);
        return Shortcut.heldModifiers(held);
    }
    public static boolean quickHeld() {
        var key=RemoteClient.shortcuts().get(ShortcutSettings.Action.QUICK);
        return key.bound() && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(),key.key()) && key.modifiers()==modifiers();
    }
    public static boolean handle(int action, KeyEvent event) {
        var mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof QuickScreen) {
            if (!quickHeld() || action == GLFW.GLFW_PRESS && event.key() == GLFW.GLFW_KEY_ESCAPE) mc.gui.setScreen(null);
            return true;
        }
        if (action != GLFW.GLFW_PRESS || mc.player == null || mc.level == null || mc.gui.screen() != null) return false;
        if (RemoteClient.placing() && event.key() == GLFW.GLFW_KEY_ESCAPE) { RemoteClient.cancelPlacing(); return true; }
        for (var target : ShortcutSettings.Action.values()) {
            if (!RemoteClient.shortcuts().get(target).matches(event.key(), event.modifiers() | modifiers())) continue;
            switch (target) {
                case MENU -> mc.gui.setScreen(new RemoteScreen(null));
                case ATTACH -> RemoteClient.startPlacing();
                case QUICK -> { RemoteClient.cancelPlacing(); mc.gui.setScreen(new QuickScreen()); }
            }
            return true;
        }
        return false;
    }
}
