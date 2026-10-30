package io.github.ruok0214.remoteinteraction;

import io.github.ruok0214.remoteinteraction.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class RemoteGameTest implements FabricClientGameTest {
    private static final BlockPos LEVER=new BlockPos(0,81,0), BUTTON=new BlockPos(2,81,0), HIDDEN=new BlockPos(4,81,0);
    private static boolean veto;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void menu(ClientGameTestContext c){
        c.getInput().pressKey(GLFW.GLFW_KEY_T);c.waitForScreen(ChatScreen.class);
        c.getInput().typeChars("/ri");c.getInput().pressKey(GLFW.GLFW_KEY_ENTER);c.waitForScreen(RemoteScreen.class);
    }
    private static void modifiers(ClientGameTestContext c,int flags,boolean down){
        if((flags&GLFW.GLFW_MOD_SHIFT)!=0){if(down)c.getInput().holdShift();else c.getInput().releaseShift();}
        if((flags&GLFW.GLFW_MOD_CONTROL)!=0){if(down)c.getInput().holdControl();else c.getInput().releaseControl();}
        if((flags&GLFW.GLFW_MOD_ALT)!=0){if(down)c.getInput().holdAlt();else c.getInput().releaseAlt();}
    }
    private static void bind(ClientGameTestContext c,ShortcutSettings.Action action,int key,int mods){
        String label=c.computeOnClient(mc->RemoteScreen.tr("keys."+action.name().toLowerCase(),RemoteClient.shortcuts().get(action).label()).getString());
        c.clickScreenButton(label);modifiers(c,mods,true);c.getInput().pressKey(key);modifiers(c,mods,false);c.waitTick();
    }
    private static void chord(ClientGameTestContext c,int key,int mods){modifiers(c,mods,true);c.getInput().pressKey(key);modifiers(c,mods,false);}
    private static void aim(ClientGameTestContext c,BlockPos pos,double localY){
        c.runOnClient(mc->{var eye=mc.player.getEyePosition();double dx=pos.getX()+.5-eye.x,dy=pos.getY()+localY-eye.y,dz=pos.getZ()+.5-eye.z;
            mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));
            mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));});c.waitTicks(5);
    }
    private static UUID id(TestSingleplayerContext world,BlockPos pos){return world.getServer().computeOnServer(s->ReceiverData.get(s).at(s.overworld().dimension().identifier(),pos).id());}
    private static void cursor(ClientGameTestContext c,double x,double y){
        var p=c.computeOnClient(mc->new double[]{x*mc.getWindow().getScreenWidth()/mc.gui.screen().width,y*mc.getWindow().getScreenHeight()/mc.gui.screen().height});
        c.getInput().setCursorPos(p[0],p[1]);c.waitTick();
    }
    private static void clickSlot(ClientGameTestContext c,UUID id,int slot){
        boolean before=c.computeOnClient(mc->RemoteClient.entries().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow().slots().contains(slot));
        var p=c.computeOnClient(mc->{
            int row=0;while(!RemoteClient.entries().get(row).id().equals(id))row++;
            var box=mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).map(Checkbox.class::cast).toList().get(row*Receiver.SLOT_COUNT+slot);
            return new int[]{box.getX()+5,box.getY()+5};
        });
        cursor(c,p[0],p[1]);c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        c.waitFor(mc->RemoteClient.entries().stream().filter(e->e.id().equals(id)).findFirst().orElseThrow().slots().contains(slot)!=before);
    }
    private static void drag(ClientGameTestContext c,double x,double y,double dx,double dy){
        cursor(c,x,y);c.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);c.waitTick();
        cursor(c,x+dx,y+dy);c.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);c.waitTick();
    }
    private static void editLayout(ClientGameTestContext c){
        c.clickScreenButton("remote_interaction.layout.title");c.waitForScreen(QuickLayoutScreen.class);
        var before=c.computeOnClient(mc->RemoteClient.layout().get().bounds(mc.gui.screen().width,mc.gui.screen().height));
        drag(c,before.x()+30,before.y()+25,-20,-18);
        drag(c,before.right()-20-5,before.bottom()-18-5,-90,-60);
        c.takeScreenshot("quick-layout-editor-ko");c.clickScreenButton("remote_interaction.save");
        var saved=c.computeOnClient(mc->RemoteClient.layout().get());
        c.runOnClient(mc->{
            var after=saved.bounds(mc.gui.screen().width,mc.gui.screen().height);
            check(after.x()==before.x()-20&&after.y()==before.y()-18,"Drag moves the preview");
            check(after.width()==before.width()-90&&after.height()==before.height()-60,"Corner drag resizes the preview");
            check(QuickLayoutSettings.load().get().equals(saved),"Window layout persists to disk");
        });
        c.clickScreenButton("remote_interaction.layout.title");c.clickScreenButton("remote_interaction.layout.reset");c.clickScreenButton("gui.cancel");
        c.runOnClient(mc->check(RemoteClient.layout().get().equals(saved),"Cancel discards the reset preview"));
        c.clickScreenButton("remote_interaction.layout.title");drag(c,before.x()+10,before.y()+10,-5000,-5000);
        c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        c.runOnClient(mc->{
            check(RemoteClient.layout().get().equals(saved),"Escape discards a move");
            var bounded=new QuickLayout(1,1,1,1).bounds(320,240);
            check(bounded.x()>=8&&bounded.y()>=8&&bounded.right()<=312&&bounded.bottom()<=232,"Oversized saved layout stays inside small screens");
        });
    }
    private static void supportedTargets(net.minecraft.server.MinecraftServer server){
        var p=server.getPlayerList().getPlayers().getFirst();var level=p.level();var data=ReceiverData.get(server);var pos=new BlockPos(5,81,0);
        for(var block:List.of(Blocks.OAK_DOOR,Blocks.OAK_TRAPDOOR,Blocks.OAK_FENCE_GATE,Blocks.REPEATER,Blocks.COMPARATOR,Blocks.NOTE_BLOCK,Blocks.DAYLIGHT_DETECTOR,Blocks.BELL)){
            level.setBlockAndUpdate(pos,block.defaultBlockState());
            if(block instanceof DoorBlock)level.setBlockAndUpdate(pos.above(),block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            var before=level.getBlockState(pos);var r=new Receiver(UUID.randomUUID(),p.getUUID(),level.dimension().identifier(),pos,Direction.UP,BuiltInRegistries.BLOCK.getKey(block),"",false,false);
            data.add(r);check(RemoteService.activate(p,r).equals("activated"),"Supported block activates: "+block);
            if(!(block instanceof BellBlock))check(!level.getBlockState(pos).equals(before),"Supported block changes: "+block);
            data.remove(r.id());level.removeBlock(pos.above(),false);level.removeBlock(pos,false);
        }
    }
    @Override public void runTest(ClientGameTestContext c){
        UseBlockCallback.EVENT.register((p,l,h,hit)->veto?InteractionResult.FAIL:InteractionResult.PASS);
        c.getInput().resizeWindow(1000,700);
        c.runOnClient(mc->{
            for(var action:ShortcutSettings.Action.values())check(!RemoteClient.shortcuts().get(action).bound(),"Every default shortcut is unassigned");
            mc.options.languageCode="ko_kr";mc.getLanguageManager().setSelected("ko_kr");mc.reloadResourcePacks();
        });
        TestWorldSave save;UUID leverId,buttonId,hiddenId;
        int quickMods=GLFW.GLFW_MOD_CONTROL|GLFW.GLFW_MOD_ALT;
        try(var world=c.worldBuilder().create()){
            save=world.getWorldSave();world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("fill -5 80 -5 8 80 5 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor]");
            world.getServer().runCommand("setblock 2 81 0 minecraft:stone_button[face=floor]");
            world.getServer().runCommand("setblock 4 81 0 minecraft:lever[face=floor]");
            world.getServer().runCommand("gamemode survival @a");world.getServer().runCommand("tp @a 0.5 81 2.5 180 35");
            world.getConnection().waitForClientboundPackets();
            c.getInput().pressKey(GLFW.GLFW_KEY_R);c.waitTick();c.runOnClient(mc->check(mc.gui.screen()==null,"R starts unbound"));
            menu(c);c.clickScreenButton("remote_interaction.keys.title");c.waitForScreen(KeySettingsScreen.class);
            bind(c,ShortcutSettings.Action.MENU,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_SHIFT);
            bind(c,ShortcutSettings.Action.ATTACH,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_CONTROL);
            bind(c,ShortcutSettings.Action.QUICK,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_SHIFT);
            c.runOnClient(mc->check(!RemoteClient.shortcuts().get(ShortcutSettings.Action.QUICK).bound(),"Duplicate chord is rejected"));
            bind(c,ShortcutSettings.Action.QUICK,GLFW.GLFW_KEY_R,quickMods);
            c.runOnClient(mc->{var restored=ShortcutSettings.load();for(var a:ShortcutSettings.Action.values())check(restored.get(a).equals(RemoteClient.shortcuts().get(a)),"Shortcut persisted: "+a);});
            c.takeScreenshot("shortcuts-ko");c.clickScreenButton("gui.done");c.clickScreenButton("gui.done");
            c.getInput().pressKey(GLFW.GLFW_KEY_R);c.waitTick();c.runOnClient(mc->check(mc.gui.screen()==null,"Missing modifiers do not open panel"));
            chord(c,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_SHIFT);c.waitForScreen(RemoteScreen.class);
            c.clickScreenButton("remote_interaction.attach");c.waitTick();aim(c,LEVER,.2);c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            c.waitFor(mc->RemoteClient.entries().size()==1);leverId=id(world,LEVER);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getMainHandItem().isEmpty(),"Empty-hand attachment needs no item");
                check(!p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Attachment suppresses vanilla lever use");});
            chord(c,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_CONTROL);c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);c.runOnClient(mc->check(!RemoteClient.placing()&&mc.gui.screen()==null,"Escape cancels placement without pause menu"));
            world.getServer().runOnServer(s->s.getPlayerList().getPlayers().getFirst().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.DIAMOND,12)));
            world.getConnection().waitForClientboundPackets();aim(c,BUTTON,.04);chord(c,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_CONTROL);c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            c.waitFor(mc->RemoteClient.entries().size()==2);buttonId=id(world,BUTTON);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(p.getMainHandItem().getCount()==12,"Held items are not consumed by attachment");
                var r=new Receiver(UUID.randomUUID(),p.getUUID(),p.level().dimension().identifier(),HIDDEN,Direction.UP,BuiltInRegistries.BLOCK.getKey(Blocks.LEVER),"숨긴 수신기",false,false);ReceiverData.get(s).add(r);RemoteService.sync(p,"");});
            hiddenId=id(world,HIDDEN);c.waitFor(mc->RemoteClient.entries().size()==3);
            menu(c);c.clickScreenButton("remote_interaction.slots.title");c.waitForScreen(QuickSettingsScreen.class);
            c.runOnClient(mc->check(mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).count()==27,"Each receiver exposes nine slot checkboxes"));
            clickSlot(c,leverId,0);clickSlot(c,buttonId,0);clickSlot(c,leverId,1);clickSlot(c,leverId,8);clickSlot(c,leverId,8);
            c.runOnClient(mc->check(RemoteClient.entries().stream().filter(e->e.id().equals(leverId)).findFirst().orElseThrow().slots().equals(List.of(0,1)),"Unchecking removes only that slot"));
            c.takeScreenshot("receiver-slot-checkboxes-ko");editLayout(c);
            world.getServer().runOnServer(s->check(!s.overworld().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Layout preview never activates receivers"));
            c.getInput().resizeWindow(854,480);c.waitTicks(3);c.takeScreenshot("receiver-slots-small-ko");
            c.runOnClient(mc->check(mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).count()==18,"Small screen paginates receiver rows"));
            c.clickScreenButton("remote_interaction.next");c.runOnClient(mc->check(mc.gui.screen().children().stream().filter(Checkbox.class::isInstance).count()==9,"Last receiver remains accessible on the next page"));
            c.clickScreenButton("remote_interaction.previous");c.getInput().resizeWindow(1000,700);c.waitTicks(3);
            c.clickScreenButton("gui.done");c.clickScreenButton("gui.done");
            world.getServer().runCommand("tp @a 6.5 81 4.5");world.getConnection().waitForClientboundPackets();
            modifiers(c,quickMods,true);c.getInput().holdKey(GLFW.GLFW_KEY_R);c.waitForScreen(QuickScreen.class);
            c.runOnClient(mc->{check(!mc.mouseHandler.isMouseGrabbed(),"Quick screen releases cursor");check(!mc.isPaused(),"Quick screen does not pause game");
                check(mc.gui.screen().children().size()==2,"Only the two configured group buttons are visible");
                check(mc.gui.screen().children().stream().map(Button.class::cast).noneMatch(b->b.getMessage().getString().contains("숨긴 수신기")),"Unselected receivers are hidden");});
            c.runOnClient(mc->{var rect=RemoteClient.layout().get().bounds(mc.gui.screen().width,mc.gui.screen().height);var first=(Button)mc.gui.screen().children().getFirst();
                check(first.getX()==rect.x()&&first.getY()==rect.y()&&first.getWidth()==rect.width(),"Live quick window uses saved position and width");});
            float yaw=c.computeOnClient(mc->mc.player.getYRot()),pitch=c.computeOnClient(mc->mc.player.getXRot());
            c.getInput().moveCursor(70,35);c.waitTicks(3);
            c.runOnClient(mc->check(mc.player.getYRot()==yaw&&mc.player.getXRot()==pitch,"Moving free mouse keeps camera locked"));
            String group=c.computeOnClient(mc->((Button)mc.gui.screen().children().getFirst()).getMessage().getString());
            c.clickScreenButton(group);c.waitTicks(3);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(!p.isWithinBlockInteractionRange(LEVER,0),"Group is outside ordinary reach");
                check(p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Group toggles lever");
                check(p.level().getBlockState(BUTTON).getValue(BlockStateProperties.POWERED),"Same packet also presses button");
                check(!p.level().getBlockState(HIDDEN).getValue(BlockStateProperties.POWERED),"Hidden receiver is not activated");});
            c.takeScreenshot("quick-interaction-ko");c.getInput().resizeWindow(854,480);c.waitTicks(3);c.takeScreenshot("quick-interaction-small-ko");
            var rect=c.computeOnClient(mc->RemoteClient.layout().get().bounds(mc.gui.screen().width,mc.gui.screen().height));
            cursor(c,rect.x()+10,rect.bottom()+4);c.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);c.waitTicks(5);
            world.getServer().runOnServer(s->check(s.overworld().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Clipped button outside resized viewport cannot activate"));
            cursor(c,rect.x()+10,rect.y()+10);c.getInput().scroll(-5);c.waitTicks(2);
            c.runOnClient(mc->check(((Button)mc.gui.screen().children().getFirst()).getY()<rect.y(),"Resized quick viewport scrolls overflowing groups"));
            c.takeScreenshot("quick-window-scrolled-ko");
            c.getInput().releaseKey(GLFW.GLFW_KEY_R);modifiers(c,quickMods,false);c.waitTick();
            c.runOnClient(mc->check(mc.gui.screen()==null&&mc.mouseHandler.isMouseGrabbed(),"Releasing quick key closes and restores mouse look"));
            c.waitTicks(25);world.getServer().runOnServer(s->check(!s.overworld().getBlockState(BUTTON).getValue(BlockStateProperties.POWERED),"Button returns on vanilla timer"));
            modifiers(c,quickMods,true);c.getInput().holdKey(GLFW.GLFW_KEY_R);c.waitForScreen(QuickScreen.class);c.getInput().releaseControl();c.waitTick();
            c.runOnClient(mc->check(mc.gui.screen()==null,"Releasing a required modifier also closes quick screen"));c.getInput().releaseKey(GLFW.GLFW_KEY_R);c.getInput().releaseAlt();
            c.getInput().pressKey(GLFW.GLFW_KEY_T);c.waitForScreen(ChatScreen.class);chord(c,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_SHIFT);
            c.runOnClient(mc->check(mc.gui.screen() instanceof ChatScreen,"Shortcuts do not open while typing in chat"));c.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            world.getServer().runOnServer(s->{
                var p=s.getPlayerList().getPlayers().getFirst();var data=ReceiverData.get(s);var r=data.get(leverId);
                veto=true;check(RemoteService.activate(p,r).equals("denied"),"Protection callback vetoes remote use");veto=false;
                p.setGameMode(GameType.SPECTATOR);check(RemoteService.activate(p,r).equals("denied"),"Spectator denied");p.setGameMode(GameType.SURVIVAL);
                var foreign=new Receiver(UUID.randomUUID(),UUID.randomUUID(),r.dimension(),new BlockPos(3,81,0),Direction.UP,r.block(),"",false,false);
                data.add(foreign);RemoteService.forget(p.getUUID());RemoteService.setSlot(p,new RemotePayloads.SetSlot(foreign.id(),0,true));
                check(data.get(foreign.id()).slots().isEmpty(),"Cannot configure another owner's receiver");check(RemoteService.activate(p,foreign).equals("denied"),"Foreign use rejected");data.remove(foreign.id());
                RemoteService.forget(p.getUUID());RemoteService.setSlot(p,new RemotePayloads.SetSlot(r.id(),99,true));check(data.get(r.id()).slots().equals(List.of(0,1)),"Out-of-range slot rejected");
                var far=new Receiver(UUID.randomUUID(),p.getUUID(),r.dimension(),new BlockPos(100000,81,100000),Direction.UP,r.block(),"",false,false,List.of(0));
                data.add(far);check(RemoteService.activate(p,far).equals("unloaded")&&!p.level().hasChunkAt(far.pos()),"Unloaded targets never force-load");
                RemoteService.forget(p.getUUID());RemoteService.activateSlot(p,0);boolean after=p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED);
                check(!after,"Available group members operate even with an unloaded member");RemoteService.activateSlot(p,0);
                check(p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED)==after,"Repeated group request is rate-limited as a whole");data.remove(far.id());
                int before=data.all().size();RemoteService.forget(p.getUUID());RemoteService.attach(p,LEVER);check(data.all().size()==before,"Forged out-of-reach attachment rejected");
                var encoded=Receiver.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,r).getOrThrow().getAsJsonObject();encoded.remove("slots");
                check(Receiver.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow().slots().isEmpty(),"0.1 saved receivers migrate with no quick slots");
                supportedTargets(s);data.update(data.get(leverId).renamed("입구 조명"));s.getDataStorage().saveAndJoin();RemoteService.sync(p,"");
            });
            c.waitTicks(5);menu(c);c.takeScreenshot("mod-panel-small-ko");c.clickScreenButton("remote_interaction.help");c.takeScreenshot("help-small-ko");c.clickScreenButton("gui.done");
            world.getServer().runCommand("setblock 2 81 0 minecraft:air");world.getServer().runCommand("setblock 2 81 0 minecraft:stone_button[face=floor]");c.waitTicks(15);
            world.getServer().runOnServer(s->{var p=s.getPlayerList().getPlayers().getFirst();check(ReceiverData.get(s).get(buttonId).lost(),"Replaced block does not relink");
                RemoteService.forget(p.getUUID());RemoteService.request(p,new RemotePayloads.Request(RemotePayloads.Action.REMOVE,buttonId,""));
                check(p.getMainHandItem().getCount()==12,"Itemless removal never changes inventory");});
            c.waitFor(mc->RemoteClient.entries().size()==2);c.clickScreenButton("gui.done");
        }
        try(var world=save.open()){
            world.getConnection().waitForChunksDownload();world.getServer().runOnServer(s->{var r=ReceiverData.get(s).get(leverId);
                check(r!=null&&r.name().equals("입구 조명")&&r.slots().equals(List.of(0,1)),"Real world restart restores names and multiple slot memberships");
                check(ReceiverData.get(s).get(buttonId)==null&&ReceiverData.get(s).get(hiddenId).slots().isEmpty(),"Removed and hidden choices persist");});
            chord(c,GLFW.GLFW_KEY_R,GLFW.GLFW_MOD_SHIFT);c.waitForScreen(RemoteScreen.class);c.takeScreenshot("restored-panel-ko");c.clickScreenButton("gui.done");
        }
        try(var world=c.worldBuilder().create()){
            world.getConnection().waitForChunksDownload();c.runOnClient(mc->check(RemoteClient.entries().isEmpty(),"Another world does not inherit receivers or slots"));
        }
    }
}
