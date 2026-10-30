package io.github.ruok0214.remoteinteraction;

import io.github.ruok0214.remoteinteraction.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class RemoteGameTest implements FabricClientGameTest {
    private static final BlockPos LEVER = new BlockPos(0,81,0), BUTTON = new BlockPos(2,81,0);
    private static boolean veto;
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void action(ClientGameTestContext context,RemotePayloads.Action action,UUID id,String name){
        context.waitTicks(5); context.runOnClient(mc->RemoteClient.send(new RemotePayloads.Request(action,id,name))); context.waitTicks(3);
    }
    private static void select(ClientGameTestContext context,String name){context.clickScreenButton(name);}
    private static void supportedTargets(net.minecraft.server.MinecraftServer server) {
        var p=server.getPlayerList().getPlayers().getFirst(); var level=p.level(); var data=ReceiverData.get(server);
        var pos=new BlockPos(5,81,0);
        for(var block:List.of(Blocks.OAK_DOOR,Blocks.OAK_TRAPDOOR,Blocks.OAK_FENCE_GATE,Blocks.REPEATER,
                Blocks.COMPARATOR,Blocks.NOTE_BLOCK,Blocks.DAYLIGHT_DETECTOR,Blocks.BELL)){
            level.setBlockAndUpdate(pos,block.defaultBlockState());
            if(block instanceof DoorBlock) level.setBlockAndUpdate(pos.above(),block.defaultBlockState().setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            var before=level.getBlockState(pos);
            var r=new Receiver(UUID.randomUUID(),p.getUUID(),level.dimension().identifier(),pos,Direction.UP,
                    BuiltInRegistries.BLOCK.getKey(block),"",false,false);
            check(RemoteService.supported(before),"Supported target: "+block);
            check(data.add(r),"Register supported target");
            check(RemoteService.activate(p,r).equals("activated"),"Vanilla interaction succeeds: "+block);
            if(!(block instanceof BellBlock)) check(!level.getBlockState(pos).equals(before),"State actually changes: "+block);
            data.remove(r.id()); level.removeBlock(pos.above(),false); level.removeBlock(pos,false);
        }
        for(var block:List.of(Blocks.IRON_DOOR,Blocks.IRON_TRAPDOOR,Blocks.CHEST,Blocks.STONE))
            check(!RemoteService.supported(block.defaultBlockState()),"Unsupported target: "+block);
        var input=net.minecraft.world.item.crafting.CraftingInput.of(3,3,List.of(
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT),new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),
                new ItemStack(net.minecraft.world.item.Items.REDSTONE),new ItemStack(net.minecraft.world.item.Items.QUARTZ),new ItemStack(net.minecraft.world.item.Items.REDSTONE),
                new ItemStack(net.minecraft.world.item.Items.IRON_INGOT),new ItemStack(net.minecraft.world.item.Items.COPPER_INGOT),new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)));
        var recipe=server.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,input,level).orElseThrow();
        var result=recipe.value().assemble(input);
        check(result.is(RemoteInteraction.RECEIVER)&&result.getCount()==2,"Survival recipe crafts two receivers");
    }
    @Override public void runTest(ClientGameTestContext context) {
        UseBlockCallback.EVENT.register((p,l,h,hit)->veto ? InteractionResult.FAIL : InteractionResult.PASS);
        context.getInput().resizeWindow(1000,700);
        context.runOnClient(mc -> { mc.options.languageCode="ko_kr"; mc.getLanguageManager().setSelected("ko_kr"); mc.reloadResourcePacks(); });
        TestWorldSave save;
        UUID leverId;
        try(var world=context.worldBuilder().create()){
            save=world.getWorldSave();
            world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("fill -5 80 -5 8 80 5 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:lever[face=floor]");
            world.getServer().runCommand("setblock 2 81 0 minecraft:stone_button[face=floor]");
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("tp @a 0.5 81 2.5 180 35");
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();
                p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(RemoteInteraction.RECEIVER,10));
            });
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(mc->{
                var eye=mc.player.getEyePosition(); double dx=.5-eye.x,dy=81.2-eye.y,dz=.5-eye.z;
                mc.player.setYRot((float)(Math.toDegrees(Math.atan2(dz,dx))-90));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));
            });
            context.waitTicks(5); context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            context.waitFor(mc->RemoteClient.entries().size()==1);
            leverId=world.getServer().computeOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst(); var data=ReceiverData.get(server);
                check(p.getMainHandItem().getCount()==9,"Actual right-click consumes one receiver");
                check(!p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Attaching does not toggle lever");
                var hit=new BlockHitResult(Vec3.atCenterOf(LEVER),Direction.UP,LEVER,false);
                RemoteService.attach(p,InteractionHand.MAIN_HAND,hit);
                check(data.owned(p.getUUID()).size()==1 && p.getMainHandItem().getCount()==9,"Duplicate attachment consumes nothing");
                veto=true;
                RemoteService.attach(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(BUTTON),Direction.UP,BUTTON,false));
                veto=false;
                check(data.owned(p.getUUID()).size()==1,"Protection callback blocks attachment");
                RemoteService.attach(p,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(BUTTON),Direction.UP,BUTTON,false));
                check(data.owned(p.getUUID()).size()==2 && p.getMainHandItem().getCount()==8,"Second target attaches");
                return data.at(p.level().dimension().identifier(),LEVER).id();
            });
            world.getServer().runCommand("tp @a 6.5 81 4.5");
            world.getConnection().waitForClientboundPackets();
            context.getInput().pressKey(GLFW.GLFW_KEY_R); context.waitForScreen(RemoteScreen.class);
            context.waitFor(mc->RemoteClient.entries().size()==2);
            action(context,RemotePayloads.Action.ACTIVATE,leverId,"");
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();
                check(!p.isWithinBlockInteractionRange(LEVER,0),"Target is outside normal reach");
                check(p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Network request toggles remote lever");
            });
            UUID buttonId=world.getServer().computeOnServer(server->ReceiverData.get(server).at(server.overworld().dimension().identifier(),BUTTON).id());
            action(context,RemotePayloads.Action.ACTIVATE,buttonId,"");
            world.getServer().runOnServer(server->check(server.overworld().getBlockState(BUTTON).getValue(BlockStateProperties.POWERED),"Remote button presses"));
            context.waitTicks(30);
            world.getServer().runOnServer(server->check(!server.overworld().getBlockState(BUTTON).getValue(BlockStateProperties.POWERED),"Button releases on vanilla schedule"));
            select(context,RemoteClient.entries().stream().filter(e->e.id().equals(leverId)).map(RemoteScreen::label).findFirst().orElseThrow().getString());
            context.clickScreenButton("remote_interaction.rename");
            context.runOnClient(mc->mc.gui.screen().children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue("입구 조명"));
            context.clickScreenButton("remote_interaction.save"); context.waitForScreen(RemoteScreen.class);
            context.waitFor(mc->RemoteClient.entries().stream().anyMatch(e->e.name().equals("입구 조명")));
            context.takeScreenshot("remote-panel-ko");
            context.getInput().resizeWindow(854,480); context.waitTicks(3); context.takeScreenshot("remote-panel-small-ko");
            context.clickScreenButton("remote_interaction.help"); context.takeScreenshot("remote-help-small-ko"); context.clickScreenButton("gui.done");
            context.runOnClient(mc->check(!RemoteClient.extractMarkers().isEmpty(),"Attached receivers have visible marker geometry"));
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst(); var data=ReceiverData.get(server); var r=data.get(leverId);
                veto=true; check(RemoteService.activate(p,r).equals("denied"),"Protection veto blocks remote use"); veto=false;
                check(p.level().getBlockState(LEVER).getValue(BlockStateProperties.POWERED),"Blocked use preserves lever");
                p.setGameMode(GameType.SPECTATOR); check(RemoteService.activate(p,r).equals("denied"),"Spectators cannot operate"); p.setGameMode(GameType.SURVIVAL);
                var foreign=new Receiver(UUID.randomUUID(),UUID.randomUUID(),r.dimension(),new BlockPos(3,81,0),Direction.UP,r.block(),"",true,false);
                data.add(foreign); check(RemoteService.activate(p,foreign).equals("denied"),"Foreign owner is rejected");
                int before=data.all().size(); RemoteService.forget(p.getUUID());
                RemoteService.request(p,new RemotePayloads.Request(RemotePayloads.Action.REMOVE,foreign.id(),""));
                check(data.all().size()==before,"Forged removal cannot delete another player's receiver"); data.remove(foreign.id());
                var far=new Receiver(UUID.randomUUID(),p.getUUID(),r.dimension(),new BlockPos(100000,81,100000),Direction.UP,r.block(),"",false,false);
                data.add(far); check(RemoteService.activate(p,far).equals("unloaded"),"Unloaded targets are rejected");
                check(!p.level().hasChunkAt(far.pos()),"Remote request never force-loads chunks"); data.remove(far.id());
                var otherDimension=new Receiver(UUID.randomUUID(),p.getUUID(),net.minecraft.resources.Identifier.withDefaultNamespace("the_nether"),LEVER,Direction.UP,r.block(),"",false,false);
                data.add(otherDimension); check(RemoteService.activate(p,otherDimension).equals("dimension"),"Cross-dimension activation rejected"); data.remove(otherDimension.id());
                // Save codec must preserve ownership, refund eligibility, position and custom names.
                var encoded=ReceiverData.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,data).getOrThrow();
                var decoded=ReceiverData.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,encoded).getOrThrow();
                check(decoded.get(leverId).equals(data.get(leverId)),"Persistence codec round-trip");
                supportedTargets(server);
            });
            // Breaking and replacing an identical block must never reconnect an old receiver.
            world.getServer().runCommand("setblock 2 81 0 minecraft:air");
            world.getServer().runCommand("setblock 2 81 0 minecraft:stone_button[face=floor]");
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst(); var data=ReceiverData.get(server);
                check(data.get(buttonId).lost(),"Breaking a target invalidates the saved attachment");
                check(RemoteService.activate(p,data.get(buttonId)).equals("missing"),"Replacing with same block does not relink");
            });
            context.waitTicks(25);
            context.clickScreenButton(">");
            select(context,context.computeOnClient(mc->RemoteScreen.label(RemoteClient.entries().stream().filter(e->e.id().equals(buttonId)).findFirst().orElseThrow()).getString()));
            context.clickScreenButton("remote_interaction.remove"); context.waitForScreen(ConfirmScreen.class); context.clickScreenButton("gui.no");
            world.getServer().runOnServer(server->check(ReceiverData.get(server).get(buttonId)!=null,"Cancel recovery preserves attachment"));
            context.clickScreenButton("remote_interaction.remove"); context.waitForScreen(ConfirmScreen.class); context.clickScreenButton("gui.yes");
            context.waitFor(mc->RemoteClient.entries().size()==1);
            action(context,RemotePayloads.Action.REMOVE,buttonId,"");
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst();
                check(p.getInventory().countItem(RemoteInteraction.RECEIVER)==9,"Exactly one refund despite repeated removal request");
                server.getDataStorage().saveAndJoin();
            });
            context.clickScreenButton("gui.done"); context.waitTicks(3); context.takeScreenshot("attached-receiver-world-ko");
        }
        try(var world=save.open()){
            world.getConnection().waitForChunksDownload();
            world.getServer().runOnServer(server->{
                var p=server.getPlayerList().getPlayers().getFirst(); var r=ReceiverData.get(server).get(leverId);
                check(r!=null && r.name().equals("입구 조명") && r.owner().equals(p.getUUID()) && r.paid(),"World restart restores real saved data");
            });
            context.getInput().pressKey(GLFW.GLFW_KEY_R); context.waitForScreen(RemoteScreen.class);
            context.waitFor(mc->RemoteClient.entries().size()==1);
            context.takeScreenshot("restored-panel-ko"); context.clickScreenButton("gui.done");
        }
        try(var world=context.worldBuilder().create()){
            world.getConnection().waitForChunksDownload();
            context.runOnClient(mc->check(RemoteClient.entries().isEmpty(),"Another world does not inherit receiver entries"));
        }
    }
}
