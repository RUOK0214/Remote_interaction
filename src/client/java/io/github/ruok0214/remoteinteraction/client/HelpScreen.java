package io.github.ruok0214.remoteinteraction.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HelpScreen extends Screen {
    private final Screen parent;
    HelpScreen(Screen parent){super(RemoteScreen.tr("help"));this.parent=parent;}
    @Override protected void init(){addRenderableWidget(Button.builder(Component.translatable("gui.done"),b->onClose()).bounds(width/2-60,height-30,120,20).build());}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        super.extractRenderState(g,x,y,dt);
        g.centeredText(font,title,width/2,16,0xFFFFFFFF);
        int w=Math.min(430,width-24), lineY=40;
        for(int i=1;i<=6;i++) for(var line:font.split(RemoteScreen.tr("help."+i),w)){
            g.text(font,line,(width-w)/2,lineY,0xFFCCD8E6); lineY+=12;
        }
    }
}
