package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class QuickSettingsScreen extends Screen {
    private final Screen parent;
    private int slot,page,rows;
    private String query="";
    private EditBox search;
    public QuickSettingsScreen(Screen parent) { super(RemoteScreen.tr("slots.title")); this.parent=parent; }
    @Override protected void init() {
        int w=Math.min(440,width-24),x=(width-w)/2;
        rows=Math.max(1,(height-144)/24);
        search=addRenderableWidget(new EditBox(font,x,76,w,20,RemoteScreen.tr("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(RemoteScreen.tr("search"));
        search.setResponder(v->{query=v;page=0;updated();}); updated(); RemoteClient.send(RemotePayloads.Request.list());
    }
    private List<RemotePayloads.Entry> filtered() {
        String needle=query.toLowerCase(Locale.ROOT);
        return RemoteClient.entries().stream().filter(e->(RemoteScreen.label(e).getString()+" "+e.pos().toShortString()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public void updated() {
        if(search==null)return;
        for(var child:List.copyOf(children()))if(child!=search)removeWidget(child);
        int w=Math.min(440,width-24),x=(width-w)/2;
        addRenderableWidget(Button.builder(Component.literal("<"),b->{slot=(slot+Receiver.SLOT_COUNT-1)%Receiver.SLOT_COUNT;updated();}).bounds(x,48,28,20).build());
        addRenderableWidget(Button.builder(Component.literal(">"),b->{slot=(slot+1)%Receiver.SLOT_COUNT;updated();}).bounds(x+w-28,48,28,20).build());
        var entries=filtered(); int pages=Math.max(1,(entries.size()+rows-1)/rows); page=Math.clamp(page,0,pages-1);
        for(int i=page*rows;i<Math.min(entries.size(),(page+1)*rows);i++) {
            var e=entries.get(i); boolean included=e.slots().contains(slot);
            var button=addRenderableWidget(Button.builder(Component.literal((included?"[✓] ":"[ ] ")+font.plainSubstrByWidth(RemoteScreen.label(e).getString(),w-30)),b->{
                RemoteClient.send(new RemotePayloads.SetSlot(e.id(),slot,!included)); b.active=false;
            }).bounds(x,103+(i-page*rows)*24,w,20).build());
            button.active=RemoteClient.available();
            button.setTooltip(Tooltip.create(Component.literal(e.pos().toShortString()+" · "+e.dimension())));
        }
        var prev=addRenderableWidget(Button.builder(RemoteScreen.tr("previous"),b->{page--;updated();}).bounds(x,height-28,70,20).build());prev.active=page>0;
        var next=addRenderableWidget(Button.builder(RemoteScreen.tr("next"),b->{page++;updated();}).bounds(x+w-70,height-28,70,20).build());next.active=page+1<pages;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-50,height-28,100,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        g.centeredText(font,RemoteScreen.tr("slots.hint"),width/2,30,0xFFB8C7D9);
        long count=RemoteClient.entries().stream().filter(e->e.slots().contains(slot)).count();
        g.centeredText(font,RemoteScreen.tr("slots.current",slot+1,Receiver.SLOT_COUNT,count),width/2,54,0xFF7FE5C7);
        if(filtered().isEmpty())g.centeredText(font,RemoteScreen.tr("no_results"),width/2,110,0xFFE4B583);
    }
}
