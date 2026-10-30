package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Locale;

public final class MarkerSettingsScreen extends Screen {
    public enum Control { RED,GREEN,BLUE,TRANSPARENCY,WIDTH }
    private static final int[] PALETTE={0x43E7D4,0x478CFF,0xFF7061,0xFFC857,0x7DDF64,0xBC86FF,0xFFFFFF,0x181D25};
    private final Screen parent;
    private MarkerAppearance draft;
    private String notice="markers.hint";
    public MarkerSettingsScreen(Screen parent){super(RemoteScreen.tr("markers.title"));this.parent=parent;draft=RemoteClient.markerSettings().get();}
    @Override protected void init(){
        RemoteClient.markerSettings().preview(draft);
        int w=Math.min(440,width-24),x=(width-w)/2,controlW=w-124,top=Math.max(76,height/2-54);
        for(int i=0;i<PALETTE.length;i++){
            final int rgb=PALETTE[i];
            addRenderableWidget(new ColorButton(x+i*24,top-25,rgb));
        }
        for(var control:Control.values())addRenderableWidget(new SettingSlider(x,top+control.ordinal()*23,controlW,control));
        int buttonW=(w-8)/3;
        addRenderableWidget(Button.builder(RemoteScreen.tr("markers.reset"),b->{draft=MarkerAppearance.DEFAULT;notice="markers.hint";rebuildWidgets();}).bounds(x,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+buttonW+4,height-28,buttonW,20).build());
        addRenderableWidget(Button.builder(RemoteScreen.tr("save"),b->{
            if(RemoteClient.markerSettings().save(draft))onClose();else notice="markers.save_failed";
        }).bounds(x+2*(buttonW+4),height-28,buttonW,20).build());
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void removed(){RemoteClient.markerSettings().clearPreview();super.removed();}
    private void changed(){RemoteClient.markerSettings().preview(draft);notice="markers.hint";}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mouseX,int mouseY,float dt){
        super.extractRenderState(g,mouseX,mouseY,dt);
        g.centeredText(font,title,width/2,12,0xFFFFFFFF);
        int w=Math.min(440,width-24),x=(width-w)/2,top=Math.max(76,height/2-54);
        g.textWithWordWrap(font,RemoteScreen.tr(notice),x,31,w,0xFFC6DADD);
        int px=x+w-114,py=top+12;
        g.centeredText(font,RemoteScreen.tr("markers.preview"),px+57,top-4,0xFFCCE1DF);
        // Checkerboard makes alpha visible even when no world is open.
        for(int row=0;row<9;row++)for(int col=0;col<11;col++)
            g.fill(px+col*10,py+row*10,px+col*10+10,py+row*10+10,(row+col)%2==0?0xFF29343C:0xFF3B4852);
        int[][] points={{px+15,py+32},{px+67,py+32},{px+67,py+78},{px+15,py+78},
                {px+40,py+12},{px+92,py+12},{px+92,py+58},{px+40,py+58}};
        int[][] edges={{0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}};
        if(draft.transparency()<100)for(var edge:edges)line(g,points[edge[0]],points[edge[1]]);
        g.centeredText(font,Component.literal(String.format(Locale.ROOT,"#%06X",draft.color())),px+55,py+96,0xFFE9F3F1);
    }
    private void line(GuiGraphicsExtractor g,int[] a,int[] b){
        float dx=b[0]-a[0],dy=b[1]-a[1];
        int thickness=Math.max(1,Math.round(draft.lineWidth()));
        g.pose().pushMatrix();g.pose().translate(a[0],a[1]).rotate((float)Math.atan2(dy,dx));
        g.fill(0,-thickness/2,Math.round((float)Math.hypot(dx,dy)),(thickness+1)/2,draft.argb());g.pose().popMatrix();
    }
    private final class ColorButton extends Button {
        private final int rgb;
        ColorButton(int x,int y,int rgb){
            super(x,y,20,18,Component.literal(String.format(Locale.ROOT,"#%06X",rgb)),b->{
                draft=new MarkerAppearance(rgb,draft.transparency(),draft.lineWidth());changed();rebuildWidgets();
            },DEFAULT_NARRATION);this.rgb=rgb;
            setTooltip(Tooltip.create(getMessage()));
        }
        @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float dt){
            g.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+getHeight()-2,0xFF000000|rgb);
            g.outline(getX(),getY(),getWidth(),getHeight(),draft.color()==rgb||isHoveredOrFocused()?0xFFFFFFFF:0xFF76858D);
        }
    }
    public final class SettingSlider extends AbstractSliderButton {
        private final Control control;
        SettingSlider(int x,int y,int w,Control control){
            super(x,y,w,20,Component.empty(),switch(control){
                case RED->((draft.color()>>16)&255)/255.0;
                case GREEN->((draft.color()>>8)&255)/255.0;
                case BLUE->(draft.color()&255)/255.0;
                case TRANSPARENCY->draft.transparency()/100.0;
                case WIDTH->(draft.lineWidth()-1)/5.0;
            });this.control=control;updateMessage();
        }
        public Control control(){return control;}
        @Override protected void updateMessage(){
            Object display=switch(control){
                case RED,GREEN,BLUE->Math.round(value*255);
                case TRANSPARENCY->Math.round(value*100);
                case WIDTH->String.format(Locale.ROOT,"%.1f",1+Math.round(value*50)/10f);
            };
            setMessage(RemoteScreen.tr("markers."+control.name().toLowerCase(Locale.ROOT),display));
        }
        @Override protected void applyValue(){
            int rgb=draft.color(),alpha=draft.transparency();float thickness=draft.lineWidth();
            switch(control){
                case RED->rgb=(rgb&0x00FFFF)|((int)Math.round(value*255)<<16);
                case GREEN->rgb=(rgb&0xFF00FF)|((int)Math.round(value*255)<<8);
                case BLUE->rgb=(rgb&0xFFFF00)|(int)Math.round(value*255);
                case TRANSPARENCY->alpha=(int)Math.round(value*100);
                case WIDTH->thickness=1+Math.round(value*50)/10f;
            }
            draft=new MarkerAppearance(rgb,alpha,thickness);changed();
        }
    }
}
