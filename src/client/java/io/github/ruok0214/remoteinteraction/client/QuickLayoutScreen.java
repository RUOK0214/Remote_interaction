package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.RemotePayloads;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.*;

/** Layout is a draft; receiver assignments and automatic assignment are saved separately on the server. */
public final class QuickLayoutScreen extends Screen {
    private static final int HEADER=47,FOOTER=74;
    private final Screen parent;
    private QuickLayout draft;
    private QuickLayout.Bounds dragStart;
    private double startX,startY;
    private boolean resizing;
    private int scroll;
    private String notice="layout.hint";
    private Checkbox automatic;
    private List<QuickPanel.Group> groups=List.of();
    private final List<Button> edits=new ArrayList<>();
    public QuickLayoutScreen(Screen parent){super(RemoteScreen.tr("layout.title"));this.parent=parent;draft=RemoteClient.layout().get();}
    public QuickLayout preview(){return draft;}
    @Override protected void init(){
        dragStart=null;edits.clear();automatic=null;
        int w=Math.min(360,width-24),x=(width-w)/2,buttonW=(w-8)/3;
        addRenderableWidget(Button.builder(RemoteScreen.tr("layout.reset"),b->{draft=QuickLayout.DEFAULT;scroll=0;notice="layout.hint";positionEdits();}).bounds(x,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(RemoteScreen.tr("layout.cancel"),b->onClose()).bounds(x+buttonW+4,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(RemoteScreen.tr("layout.save"),b->{
            if(RemoteClient.layout().save(draft))onClose();else notice="layout.save_failed";
        }).bounds(x+2*(buttonW+4),height-28,buttonW,20).build());
        updated();RemoteClient.send(RemotePayloads.Request.list());
    }
    public void updated(){
        if(minecraft==null)return;
        if(automatic!=null)removeWidget(automatic);
        int w=Math.min(360,width-24),x=(width-w)/2;
        automatic=addRenderableWidget(Checkbox.builder(RemoteScreen.tr("slots.auto_add"),font).pos(x,height-69)
                .selected(RemoteClient.autoAssign()).tooltip(Tooltip.create(RemoteScreen.tr("slots.auto_add_hint")))
                .onValueChange((checkbox,checked)->{RemoteClient.send(new RemotePayloads.SetAutoAssign(checked));checkbox.active=false;}).build());
        automatic.active=RemoteClient.canAutoAssign();
        groups=QuickPanel.groups(true);
        if(edits.isEmpty())for(var group:groups){
            int slot=group.slot();
            edits.add(addWidget(Button.builder(RemoteScreen.tr("slots.edit",slot+1),b->minecraft.gui.setScreen(new QuickSettingsScreen(this,slot)))
                    .bounds(0,0,64,20).build()));
        }
        positionEdits();
    }
    private int groupHeight(QuickPanel.Group group){return 24+Math.max(30,group.height());}
    private int contentHeight(){return Math.max(0,groups.stream().mapToInt(g->groupHeight(g)+6).sum()-6);}
    private int viewTop(QuickLayout.Bounds r){return Math.max(HEADER,r.y());}
    private int viewBottom(QuickLayout.Bounds r){return Math.max(viewTop(r),Math.min(height-FOOTER,r.bottom()));}
    private void positionEdits(){
        var r=draft.bounds(width,height);int top=viewTop(r),bottom=viewBottom(r);
        scroll=Math.clamp(scroll,0,Math.max(0,contentHeight()-(bottom-top)));
        int y=top-scroll;
        for(int i=0;i<edits.size();i++){
            var b=edits.get(i);b.setPosition(r.right()-84,y+2);
            b.visible=b.getY()>=top&&b.getY()+20<=bottom;
            b.active=b.visible&&RemoteClient.available();y+=groupHeight(groups.get(i))+6;
        }
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float dt){}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){
        var rect=draft.bounds(width,height);
        boolean handle=e.button()==0&&rect.contains(e.x(),e.y())&&e.x()>=rect.right()-14&&e.y()>=rect.bottom()-14;
        if(!handle&&super.mouseClicked(e,twice))return true;
        if(e.button()!=0||!rect.contains(e.x(),e.y()))return false;
        dragStart=rect;startX=e.x();startY=e.y();resizing=handle;return true;
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
        positionEdits();return true;
    }
    @Override public boolean mouseReleased(MouseButtonEvent e){
        if(dragStart!=null&&e.button()==0){dragStart=null;return true;}
        return super.mouseReleased(e);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        var rect=draft.bounds(width,height);
        if(!rect.contains(x,y)||y<viewTop(rect)||y>=viewBottom(rect))return false;
        scroll=Math.clamp(scroll-(int)(vertical*30),0,Math.max(0,contentHeight()-(viewBottom(rect)-viewTop(rect))));
        positionEdits();return true;
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        var rect=draft.bounds(width,height);positionEdits();
        g.fill(rect.x(),rect.y(),rect.right(),rect.bottom(),0x8014232C);
        g.enableScissor(rect.x(),viewTop(rect),rect.right(),viewBottom(rect));
        int top=viewTop(rect)-scroll;
        for(var group:groups){
            g.fill(rect.x(),top,rect.right(),top+24,0xE9233944);
            g.text(font,RemoteScreen.tr("slots.number",group.slot()+1),rect.x()+8,top+7,0xFFFFD277);
            if(group.members().isEmpty()){
                g.outline(rect.x(),top+24,rect.width(),30,0xFF66727A);
                g.text(font,RemoteScreen.tr("slots.empty"),rect.x()+10,top+34,0xFFB8C7D9);
            }else QuickPanel.draw(g,font,group,rect.x(),top+24,rect.width(),true,false);
            top+=groupHeight(group)+6;
        }
        for(var b:edits)if(b.visible)b.extractRenderState(g,x,y,dt);
        g.disableScissor();
        g.outline(rect.x()-1,rect.y()-1,rect.width()+2,rect.height()+2,0xFFFFD277);
        g.fill(0,0,width,HEADER,0xD918252E);g.fill(0,height-FOOTER,width,height,0xD918252E);
        g.centeredText(font,title,width/2,9,0xFFFFFFFF);
        g.textWithWordWrap(font,RemoteScreen.tr(notice),12,25,width-24,0xFFFFD277);
        g.centeredText(font,RemoteScreen.tr("layout.bounds",rect.x(),rect.y(),rect.width(),rect.height()),width/2,height-44,0xFFC4D9DE);
        super.extractRenderState(g,x,y,dt);
        g.fill(rect.right()-14,rect.bottom()-14,rect.right(),rect.bottom(),0xFFFFD277);
        g.text(font,"↘",rect.right()-11,rect.bottom()-11,0xFF17252E);
    }
}
