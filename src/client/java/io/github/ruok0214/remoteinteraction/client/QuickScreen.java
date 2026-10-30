package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A non-pausing mouse screen with no controls other than the configured receiver groups. */
public final class QuickScreen extends Screen {
    private int scroll, totalHeight, cooldown;
    public QuickScreen() { super(RemoteScreen.tr("quick.title")); }
    @Override protected void init() { updated(); RemoteClient.send(RemotePayloads.Request.list()); }
    public void updated() {
        clearWidgets();
        var groups = new LinkedHashMap<Integer, List<RemotePayloads.Entry>>();
        for (int slot=0; slot<Receiver.SLOT_COUNT; slot++) {
            final int index=slot;
            var members=RemoteClient.entries().stream().filter(e->e.slots().contains(index)).toList();
            if (!members.isEmpty()) groups.put(slot,members);
        }
        totalHeight=groups.values().stream().mapToInt(list->list.size()*22+14).sum();
        scroll=Math.clamp(scroll,0,Math.max(0,totalHeight-(height-24)));
        int w=Math.min(390,width-32), y=Math.max(12,(height-totalHeight)/2)-scroll;
        for (var group:groups.entrySet()) {
            int slot=group.getKey(), h=group.getValue().size()*22+8;
            var button=addRenderableWidget(new SlotButton((width-w)/2,y,w,h,group.getValue(),b->{
                if (cooldown>0) return;
                RemoteClient.send(new RemotePayloads.ActivateSlot(slot)); cooldown=4; updated();
            }));
            button.active=cooldown==0 && RemoteClient.available() && group.getValue().stream().anyMatch(e->e.status().equals("ready"));
            y+=h+6;
        }
    }
    @Override public void tick() {
        if (!RemoteInput.quickHeld() || !minecraft.isWindowActive() || minecraft.player == null || !minecraft.player.isAlive()) { onClose(); return; }
        if (cooldown>0 && --cooldown==0) updated();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { minecraft.gui.setScreen(null); }
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt) { }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice) {
        return e.y()>=12 && e.y()<height-12 && super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        scroll=Math.clamp(scroll-(int)(vertical*30),0,Math.max(0,totalHeight-(height-24))); updated(); return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt) {
        g.enableScissor(0,12,width,height-12); super.extractRenderState(g,x,y,dt); g.disableScissor();
        if (children().isEmpty()) g.centeredText(font,RemoteScreen.tr("quick.empty"),width/2,height/2,0xFFFFFFFF);
    }
    private final class SlotButton extends Button {
        private final List<RemotePayloads.Entry> members;
        SlotButton(int x,int y,int w,int h,List<RemotePayloads.Entry> members,OnPress press) {
            super(x,y,w,h,Component.literal(String.join(" / ",members.stream().map(e->RemoteScreen.label(e).getString()+": "+RemoteScreen.stateLabel(e).getString()).toList())),press,DEFAULT_NARRATION);
            this.members=members;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float dt) {
            boolean hover=isHoveredOrFocused() && mouseY>=12 && mouseY<QuickScreen.this.height-12;
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),active && hover ? 0xEB264F57 : 0xD918252E);
            g.outline(getX(),getY(),getWidth(),getHeight(),active ? 0xFF68CDBE : 0xFF66727A);
            for(int i=0;i<members.size();i++) {
                var entry=members.get(i); int y=getY()+10+i*22;
                String state=RemoteScreen.stateLabel(entry).getString();
                int stateWidth=Math.min(font.width(state),getWidth()/2-12);
                g.text(font,font.plainSubstrByWidth(RemoteScreen.label(entry).getString(),getWidth()-stateWidth-30),getX()+10,y,0xFFF0F5F7);
                g.text(font,font.plainSubstrByWidth(state,stateWidth),getX()+getWidth()-stateWidth-10,y,entry.status().equals("ready") ? 0xFF7FE5C7 : 0xFFE4B583);
            }
        }
    }
}
