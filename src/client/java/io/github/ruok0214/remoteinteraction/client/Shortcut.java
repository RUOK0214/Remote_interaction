package io.github.ruok0214.remoteinteraction.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.Set;

public record Shortcut(int key, int modifiers) {
    public static final Shortcut UNBOUND = new Shortcut(GLFW.GLFW_KEY_UNKNOWN, 0);
    public static final int MASK = GLFW.GLFW_MOD_SHIFT | GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SUPER;
    public Shortcut { modifiers &= MASK; }
    public boolean bound() { return key >= GLFW.GLFW_KEY_SPACE && key <= GLFW.GLFW_KEY_LAST && !modifierKey(key); }
    public static boolean modifierKey(int key) { return key >= GLFW.GLFW_KEY_LEFT_SHIFT && key <= GLFW.GLFW_KEY_RIGHT_SUPER; }
    public boolean matches(int pressed, int mods) { return bound() && key == pressed && modifiers == (mods & MASK); }
    public boolean held(Set<Integer> pressed) { return bound() && pressed.contains(key) && heldModifiers(pressed) == modifiers; }
    public static int heldModifiers(Set<Integer> keys) {
        int result = 0;
        if (keys.contains(GLFW.GLFW_KEY_LEFT_SHIFT) || keys.contains(GLFW.GLFW_KEY_RIGHT_SHIFT)) result |= GLFW.GLFW_MOD_SHIFT;
        if (keys.contains(GLFW.GLFW_KEY_LEFT_CONTROL) || keys.contains(GLFW.GLFW_KEY_RIGHT_CONTROL)) result |= GLFW.GLFW_MOD_CONTROL;
        if (keys.contains(GLFW.GLFW_KEY_LEFT_ALT) || keys.contains(GLFW.GLFW_KEY_RIGHT_ALT)) result |= GLFW.GLFW_MOD_ALT;
        if (keys.contains(GLFW.GLFW_KEY_LEFT_SUPER) || keys.contains(GLFW.GLFW_KEY_RIGHT_SUPER)) result |= GLFW.GLFW_MOD_SUPER;
        return result;
    }
    public Component label() {
        if (!bound()) return RemoteScreen.tr("keys.unbound");
        String prefix = ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0 ? "Ctrl + " : "")
                + ((modifiers & GLFW.GLFW_MOD_ALT) != 0 ? "Alt + " : "")
                + ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? "Shift + " : "")
                + ((modifiers & GLFW.GLFW_MOD_SUPER) != 0 ? "Super + " : "");
        return Component.literal(prefix).append(InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName());
    }
}
