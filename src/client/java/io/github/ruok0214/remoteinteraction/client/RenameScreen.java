package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class RenameScreen extends Screen {
    private final Screen parent;
    private final RemotePayloads.Entry entry;
    private String value;
    RenameScreen(Screen parent, RemotePayloads.Entry entry) {
        super(RemoteScreen.tr("rename")); this.parent=parent; this.entry=entry; value=entry.name();
    }
    @Override protected void init() {
        int w=Math.min(300,width-24), x=(width-w)/2, y=height/2-12;
        var edit=addRenderableWidget(new EditBox(font,x,y,w,20,title));
        edit.setMaxLength(Receiver.MAX_NAME); edit.setValue(value); edit.setResponder(v->value=v);
        addRenderableWidget(Button.builder(RemoteScreen.tr("save"),b->{
            RemoteClient.send(new RemotePayloads.Request(RemotePayloads.Action.RENAME,entry.id(),value)); onClose();
        }).bounds(x,y+32,(w-6)/2,20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"),b->onClose()).bounds(x+(w+6)/2,y+32,(w-6)/2,20).build());
        setInitialFocus(edit);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float dt){
        super.extractRenderState(g,x,y,dt);
        g.centeredText(font,title,width/2,height/2-52,0xFFFFFFFF);
        g.centeredText(font,RemoteScreen.tr("rename_hint"),width/2,height/2-32,0xFFB8C7D9);
    }
}
