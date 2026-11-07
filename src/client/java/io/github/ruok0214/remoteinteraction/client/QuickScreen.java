package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** A non-pausing mouse screen with no controls other than the configured receiver groups. */
public final class QuickScreen extends Screen {
    private int scroll,totalHeight,cooldown;
    private QuickLayout.Bounds panel;
    public QuickScreen(){super(RemoteScreen.tr("quick.title"));}
    @Override protected void init(){updated();RemoteClient.send(RemotePayloads.Request.list());}
    public void updated(){
        clearWidgets();panel=RemoteClient.layout().get().bounds(width,height);
        var groups=QuickPanel.groups();totalHeight=QuickPanel.contentHeight(groups);
        scroll=Math.clamp(scroll,0,Math.max(0,totalHeight-panel.height()));
        int y=panel.y()-scroll;
        for(var group:groups){
            var button=addRenderableWidget(new SlotButton(panel.x(),y,panel.width(),group,b->{
                if(cooldown>0||group.members().stream().noneMatch(RemoteScreen::canActivate))return;
                RemoteClient.send(new RemotePayloads.ActivateSlot(group.slot()));cooldown=4;updated();
            }));
            button.active=cooldown==0&&group.members().stream().anyMatch(e->RemoteScreen.canActivate(e)||RemoteScreen.canTurnPage(e,-1));
            if(group.members().stream().anyMatch(RemoteScreen::isLectern))button.setTooltip(Tooltip.create(RemoteScreen.tr("book.quick_hint")));
            y+=group.height()+6;
        }
    }
    @Override public void tick(){
        if(!RemoteInput.quickHeld()||!minecraft.isWindowActive()||minecraft.player==null||!minecraft.player.isAlive()){onClose();return;}
        if(cooldown>0&&--cooldown==0)updated();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(null);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        if(!panel.contains(e.x(),e.y()))return false;
        if(e.button()==1){
            for(var child:children())if(child instanceof SlotButton button&&button.isMouseOver(e.x(),e.y())){
                if(cooldown==0&&button.group.members().stream().anyMatch(member->RemoteScreen.canTurnPage(member,-1))){
                    RemoteClient.send(new RemotePayloads.PreviousSlotPage(button.group.slot()));cooldown=4;updated();
                }
                return true;
            }
        }
        return super.mouseClicked(e,twice);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(!panel.contains(x,y))return false;
        scroll=Math.clamp(scroll-(int)(vertical*30),0,Math.max(0,totalHeight-panel.height()));updated();return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        g.enableScissor(panel.x(),panel.y(),panel.right(),panel.bottom());super.extractRenderState(g,x,y,dt);
        if(children().isEmpty())g.textWithWordWrap(font,RemoteScreen.tr("quick.empty"),panel.x()+10,panel.y()+10,panel.width()-20,0xFFFFFFFF);
        g.disableScissor();
    }
    private final class SlotButton extends Button {
        private final QuickPanel.Group group;
        SlotButton(int x,int y,int w,QuickPanel.Group group,OnPress press){
            super(x,y,w,group.height(),Component.literal(String.join(" / ",group.members().stream().map(e->RemoteScreen.label(e).getString()+": "+RemoteScreen.stateLabel(e).getString()).toList())),press,DEFAULT_NARRATION);
            this.group=group;
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int mouseX,int mouseY,float dt){
            QuickPanel.draw(g,font,group,getX(),getY(),getWidth(),active,isHoveredOrFocused()&&panel.contains(mouseX,mouseY));
        }
    }
}
