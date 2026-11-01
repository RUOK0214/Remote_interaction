package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Receiver-first assignment with one compact row of numbered slot checkboxes. */
public final class QuickSettingsScreen extends Screen {
    private static final int ROW_HEIGHT=50;
    private final Screen parent;
    private int page, rows;
    private String query="";
    private EditBox search;
    private List<RemotePayloads.Entry> visible=List.of();
    public QuickSettingsScreen(Screen parent) { super(RemoteScreen.tr("slots.title")); this.parent=parent; }
    @Override protected void init() {
        int w=Math.min(480,width-24), x=(width-w)/2;
        rows=Math.max(1,(height-138)/ROW_HEIGHT);
        search=addRenderableWidget(new EditBox(font,x,48,w,20,RemoteScreen.tr("search")));
        search.setMaxLength(64); search.setValue(query); search.setHint(RemoteScreen.tr("search"));
        search.setResponder(v->{query=v;page=0;updated();}); updated(); RemoteClient.send(RemotePayloads.Request.list());
    }
    private List<RemotePayloads.Entry> filtered() {
        String needle=query.toLowerCase(Locale.ROOT);
        return RemoteClient.entries().stream().filter(e->(RemoteScreen.label(e).getString()+" "+e.pos().toShortString()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    private int slotSpacing(int panelWidth){return Math.min(24,(panelWidth-8)/Receiver.SLOT_COUNT);}
    public void updated() {
        if(search==null)return;
        for(var child:List.copyOf(children()))if(child!=search)removeWidget(child);
        int w=Math.min(480,width-24),x=(width-w)/2;
        var entries=filtered(); int pages=Math.max(1,(entries.size()+rows-1)/rows); page=Math.clamp(page,0,pages-1);
        visible=entries.subList(Math.min(page*rows,entries.size()),Math.min((page+1)*rows,entries.size()));
        for(int row=0;row<visible.size();row++) {
            var e=visible.get(row); int y=76+row*ROW_HEIGHT;
            for(int index=0;index<Receiver.SLOT_COUNT;index++) {
                final int slot=index;
                var description=RemoteScreen.tr("slots.assignment",RemoteScreen.label(e),slot+1);
                var box=addRenderableWidget(Checkbox.builder(Component.empty(),font)
                        .pos(x+4+slot*slotSpacing(w),y+25).selected(e.slots().contains(slot))
                        .tooltip(Tooltip.create(description))
                        .onValueChange((checkbox,checked)->{
                            RemoteClient.send(new RemotePayloads.SetSlot(e.id(),slot,checked)); checkbox.active=false;
                        }).build());
                // Numbers are drawn above the boxes. Keep a full spoken label without a side label.
                box.setMessage(description);
                box.setSize(Checkbox.getBoxSize(font),Checkbox.getBoxSize(font));
                box.active=RemoteClient.available();
            }
        }
        var prev=addRenderableWidget(Button.builder(RemoteScreen.tr("previous"),b->{page--;updated();}).bounds(x,height-54,58,20).build());prev.active=page>0;
        var next=addRenderableWidget(Button.builder(RemoteScreen.tr("next"),b->{page++;updated();}).bounds(x+w-58,height-54,58,20).build());next.active=page+1<pages;
        addRenderableWidget(Button.builder(RemoteScreen.tr("layout.title"),b->minecraft.gui.setScreen(new QuickLayoutScreen(this))).bounds(x,height-28,w/2-3,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(x+w/2+3,height-28,w/2-3,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        g.centeredText(font,RemoteScreen.tr("slots.hint"),width/2,30,0xFFB8C7D9);
        int w=Math.min(480,width-24),left=(width-w)/2;
        for(int i=0;i<visible.size();i++) {
            var e=visible.get(i); int top=76+i*ROW_HEIGHT;
            String detail=RemoteScreen.label(e).getString()+" · "+e.pos().toShortString();
            g.text(font,font.plainSubstrByWidth(detail,w-8),left+4,top,0xFFDAEAE8);
            for(int slot=0;slot<Receiver.SLOT_COUNT;slot++)
                g.centeredText(font,Integer.toString(slot+1),left+4+slot*slotSpacing(w)+Checkbox.getBoxSize(font)/2,top+14,0xFFDAEAE8);
            g.horizontalLine(left,left+w,top+ROW_HEIGHT-5,0xFF46575F);
            if(x>=left&&x<left+w&&y>=top&&y<top+14)g.setTooltipForNextFrame(font,Component.literal(detail+" · "+e.dimension()),x,y);
        }
        int total=filtered().size(),pages=Math.max(1,(total+rows-1)/rows);
        g.centeredText(font,RemoteScreen.tr("page",page+1,pages,total),width/2,height-48,0xFFCAD5E3);
        if(visible.isEmpty())g.centeredText(font,RemoteScreen.tr("no_results"),width/2,90,0xFFE4B583);
    }
}
