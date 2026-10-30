package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Editing never activates receivers. Save commits the draft; Escape/Cancel leaves the old layout intact. */
public final class QuickLayoutScreen extends Screen {
    private final Screen parent;
    private QuickLayout draft;
    private QuickLayout.Bounds dragStart;
    private double startX,startY;
    private boolean resizing;
    private int scroll;
    private String notice="layout.hint";
    public QuickLayoutScreen(Screen parent){super(RemoteScreen.tr("layout.title"));this.parent=parent;draft=RemoteClient.layout().get();}
    @Override protected void init(){
        dragStart=null;
        int w=Math.min(360,width-24),x=(width-w)/2,buttonW=(w-8)/3;
        addRenderableWidget(Button.builder(RemoteScreen.tr("layout.reset"),b->{draft=QuickLayout.DEFAULT;scroll=0;notice="layout.hint";}).bounds(x,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+buttonW+4,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(RemoteScreen.tr("save"),b->{
            if(RemoteClient.layout().save(draft))onClose();else notice="layout.save_failed";
        }).bounds(x+2*(buttonW+4),height-28,buttonW,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        var rect=draft.bounds(width,height);
        boolean handle=e.button()==0&&rect.contains(e.x(),e.y())&&e.x()>=rect.right()-14&&e.y()>=rect.bottom()-14;
        if(!handle&&super.mouseClicked(e,twice))return true;
        if(e.button()!=0||!rect.contains(e.x(),e.y()))return false;
        dragStart=rect;startX=e.x();startY=e.y();
        resizing=handle;
        return true;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(dragStart==null||e.button()!=0)return super.mouseDragged(e,dx,dy);
        int moveX=(int)Math.round(e.x()-startX),moveY=(int)Math.round(e.y()-startY);
        var r=dragStart;int margin=QuickLayout.MARGIN;
        if(resizing){
            int w=Math.clamp(r.width()+moveX,Math.min(QuickLayout.MIN_WIDTH,width-2*margin),width-margin-r.x());
            int h=Math.clamp(r.height()+moveY,Math.min(QuickLayout.MIN_HEIGHT,height-2*margin),height-margin-r.y());
            draft=QuickLayout.fromBounds(new QuickLayout.Bounds(r.x(),r.y(),w,h),width,height);
        }else{
            int x=Math.clamp(r.x()+moveX,margin,width-margin-r.width()),y=Math.clamp(r.y()+moveY,margin,height-margin-r.height());
            draft=QuickLayout.fromBounds(new QuickLayout.Bounds(x,y,r.width(),r.height()),width,height);
        }
        return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragStart!=null&&e.button()==0){dragStart=null;return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        var rect=draft.bounds(width,height);if(!rect.contains(x,y))return false;
        scroll=Math.clamp(scroll-(int)(vertical*30),0,Math.max(0,QuickPanel.contentHeight(QuickPanel.groups())-rect.height()));return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        var rect=draft.bounds(width,height);var groups=QuickPanel.groups();
        scroll=Math.clamp(scroll,0,Math.max(0,QuickPanel.contentHeight(groups)-rect.height()));
        g.fill(rect.x(),rect.y(),rect.right(),rect.bottom(),0x8014232C);
        g.enableScissor(rect.x(),rect.y(),rect.right(),rect.bottom());
        int top=rect.y()-scroll;
        for(var group:groups){QuickPanel.draw(g,font,group,rect.x(),top,rect.width(),true,false);top+=group.height()+6;}
        if(groups.isEmpty()){
            g.text(font,RemoteScreen.tr("layout.sample"),rect.x()+10,rect.y()+10,0xFFF0F5F7);
            g.text(font,RemoteScreen.tr("state.off"),rect.right()-40,rect.y()+10,0xFF7FE5C7);
        }
        g.disableScissor();
        g.outline(rect.x()-1,rect.y()-1,rect.width()+2,rect.height()+2,0xFFFFD277);
        g.fill(0,0,width,47,0xD918252E);g.fill(0,height-49,width,height,0xD918252E);
        g.centeredText(font,title,width/2,9,0xFFFFFFFF);
        g.textWithWordWrap(font,RemoteScreen.tr(notice),12,25,width-24,0xFFFFD277);
        g.centeredText(font,RemoteScreen.tr("layout.bounds",rect.x(),rect.y(),rect.width(),rect.height()),width/2,height-44,0xFFC4D9DE);
        super.extractRenderState(g,x,y,dt);
        // Keep the resize handle reachable even when the panel overlaps the editor toolbar.
        g.fill(rect.right()-14,rect.bottom()-14,rect.right(),rect.bottom(),0xFFFFD277);
        g.text(font,"↘",rect.right()-11,rect.bottom()-11,0xFF17252E);
    }
}
