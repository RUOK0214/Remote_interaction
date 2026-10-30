package io.github.ruok0214.remoteinteraction.client;

import io.github.ruok0214.remoteinteraction.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Shared by the live panel and the non-interactive layout preview. */
final class QuickPanel {
    record Group(int slot,List<RemotePayloads.Entry> members){int height(){return members.size()*22+8;}}
    static List<Group> groups(){
        var groups=new ArrayList<Group>();
        for(int slot=0;slot<Receiver.SLOT_COUNT;slot++){
            final int index=slot;
            var members=RemoteClient.entries().stream().filter(e->e.slots().contains(index)).toList();
            if(!members.isEmpty())groups.add(new Group(slot,members));
        }
        return groups;
    }
    static int contentHeight(List<Group> groups){return Math.max(0,groups.stream().mapToInt(g->g.height()+6).sum()-6);}
    static void draw(GuiGraphicsExtractor g,Font font,Group group,int x,int y,int width,boolean active,boolean hover){
        g.fill(x,y,x+width,y+group.height(),active&&hover?0xEB264F57:0xD918252E);
        g.outline(x,y,width,group.height(),active?0xFF68CDBE:0xFF66727A);
        for(int i=0;i<group.members().size();i++){
            var entry=group.members().get(i);int rowY=y+10+i*22;
            String state=RemoteScreen.stateLabel(entry).getString();
            int stateWidth=Math.min(font.width(state),width/2-12);
            g.text(font,font.plainSubstrByWidth(RemoteScreen.label(entry).getString(),width-stateWidth-30),x+10,rowY,0xFFF0F5F7);
            g.text(font,font.plainSubstrByWidth(state,stateWidth),x+width-stateWidth-10,rowY,entry.status().equals("ready")?0xFF7FE5C7:0xFFE4B583);
        }
    }
}
