package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Select receivers for one slot. Membership changes are saved immediately by the server. */
public final class QuickSettingsScreen extends Screen {
    private static final int ROW_HEIGHT=34;
    private final Screen parent;
    private final int slot;
    private int page,rows;
    private String query="";
    private EditBox search;
    private List<RemotePayloads.Entry> visible=List.of();
    public QuickSettingsScreen(Screen parent,int slot){
        super(RemoteScreen.tr("slots.receiver_title",slot+1));
        if(slot<0||slot>=Receiver.SLOT_COUNT)throw new IllegalArgumentException("Invalid slot");
        this.parent=parent;this.slot=slot;
    }
    public int slot(){return slot;}
    @Override protected void init(){
        int w=Math.min(480,width-24),x=(width-w)/2;
        rows=Math.max(1,(height-138)/ROW_HEIGHT);
        search=addRenderableWidget(new EditBox(font,x,48,w,20,RemoteScreen.tr("search")));
        search.setMaxLength(64);search.setValue(query);search.setHint(RemoteScreen.tr("search"));
        search.setResponder(v->{query=v;page=0;updated();});updated();RemoteClient.send(RemotePayloads.Request.list());
    }
    private List<RemotePayloads.Entry> filtered(){
        String needle=query.toLowerCase(Locale.ROOT);
        return RemoteClient.entries().stream().filter(e->(RemoteScreen.label(e).getString()+" "+e.pos().toShortString()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public void updated(){
        if(search==null)return;
        for(var child:List.copyOf(children()))if(child!=search)removeWidget(child);
        int w=Math.min(480,width-24),x=(width-w)/2;
        var entries=filtered();int pages=Math.max(1,(entries.size()+rows-1)/rows);page=Math.clamp(page,0,pages-1);
        visible=entries.subList(Math.min(page*rows,entries.size()),Math.min((page+1)*rows,entries.size()));
        for(int row=0;row<visible.size();row++){
            var e=visible.get(row);int y=76+row*ROW_HEIGHT;
            String name=RemoteScreen.label(e).getString();
            var box=addRenderableWidget(Checkbox.builder(Component.literal(font.plainSubstrByWidth(name,w-36)),font)
                    .pos(x+4,y).selected(e.slots().contains(slot))
                    .tooltip(Tooltip.create(Component.literal(name+" · "+e.pos().toShortString()+" · "+e.dimension())))
                    .onValueChange((checkbox,checked)->{
                        RemoteClient.send(new RemotePayloads.SetSlot(e.id(),slot,checked));checkbox.active=false;
                    }).build());
            box.setMessage(RemoteScreen.tr("slots.assignment",RemoteScreen.label(e),slot+1));
            box.active=RemoteClient.available();
        }
        var prev=addRenderableWidget(Button.builder(RemoteScreen.tr("previous"),b->{page--;updated();}).bounds(x,height-54,58,20).build());prev.active=page>0;
        var next=addRenderableWidget(Button.builder(RemoteScreen.tr("next"),b->{page++;updated();}).bounds(x+w-58,height-54,58,20).build());next.active=page+1<pages;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-70,height-28,140,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        super.extractRenderState(g,x,y,dt);g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        g.centeredText(font,RemoteScreen.tr("slots.hint"),width/2,30,0xFFB8C7D9);
        int w=Math.min(480,width-24),left=(width-w)/2;
        for(int i=0;i<visible.size();i++){
            var e=visible.get(i);String detail=e.pos().toShortString()+" · "+RemoteScreen.stateLabel(e).getString();
            g.text(font,font.plainSubstrByWidth(detail,w-32),left+26,96+i*ROW_HEIGHT,e.status().equals("ready")?0xFF7FE5C7:0xFFE4B583);
        }
        int total=filtered().size(),pages=Math.max(1,(total+rows-1)/rows);
        g.centeredText(font,RemoteScreen.tr("page",page+1,pages,total),width/2,height-48,0xFFCAD5E3);
        if(visible.isEmpty())g.centeredText(font,RemoteScreen.tr("no_results"),width/2,90,0xFFE4B583);
    }
}
