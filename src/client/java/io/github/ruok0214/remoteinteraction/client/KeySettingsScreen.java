package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class KeySettingsScreen extends Screen {
    private final Screen parent;
    private ShortcutSettings.Action waiting;
    private String notice = "keys.hint";
    public KeySettingsScreen(Screen parent) { super(RemoteScreen.tr("keys.title")); this.parent = parent; }
    @Override protected void init() {
        int w = Math.min(430, width - 24), x = (width-w)/2, y = Math.max(62, height/2-54);
        for (var action : ShortcutSettings.Action.values()) {
            int row = y + action.ordinal()*34;
            var key = RemoteClient.shortcuts().get(action);
            addRenderableWidget(Button.builder(waiting == action ? RemoteScreen.tr("keys.press") : RemoteScreen.tr("keys."+action.name().toLowerCase(), key.label()),
                    b -> { waiting = action; notice = "keys.capture_hint"; rebuildWidgets(); }).bounds(x, row, w-66, 24).build());
            var clear = addRenderableWidget(Button.builder(RemoteScreen.tr("keys.clear"), b -> assign(action, Shortcut.UNBOUND)).bounds(x+w-60,row,60,24).build());
            clear.active = key.bound();
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(width/2-65,height-28,130,20).build());
    }
    private void assign(ShortcutSettings.Action action, Shortcut key) {
        notice = RemoteClient.shortcuts().assign(action, key); waiting = null; rebuildWidgets();
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (waiting != null) {
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) assign(waiting, Shortcut.UNBOUND);
            else if (!Shortcut.modifierKey(event.key())) assign(waiting, new Shortcut(event.key(), event.modifiers() | RemoteInput.modifiers()));
            return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float dt) {
        super.extractRenderState(g,x,y,dt);
        g.centeredText(font,title,width/2,16,0xFFFFFFFF);
        g.textWithWordWrap(font,RemoteScreen.tr(notice),Math.max(12,(width-430)/2),35,Math.min(430,width-24),0xFFF1DB9B);
    }
}
