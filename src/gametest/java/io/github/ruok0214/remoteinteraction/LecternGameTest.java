package io.github.ruok0214.remoteinteraction;

import io.github.ruok0214.remoteinteraction.client.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@SuppressWarnings("UnstableApiUsage")
final class LecternGameTest {
    private static final BlockPos BOOK = new BlockPos(0, 81, 0), SECOND = new BlockPos(2, 81, 0), LEVER = new BlockPos(4, 81, 0);
    private static boolean veto;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static LecternBlockEntity lectern(ServerPlayer p, BlockPos pos) { return (LecternBlockEntity) p.level().getBlockEntity(pos); }
    private static void setBook(ServerPlayer p, BlockPos pos, boolean written, int pages) {
        var book = new ItemStack(written ? Items.WRITTEN_BOOK : Items.WRITABLE_BOOK);
        if (written) book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Remote pages"), "Test", 0,
                java.util.stream.IntStream.range(0, pages).mapToObj(i -> Filterable.<Component>passThrough(Component.literal("Page " + (i + 1)))).toList(), true));
        else book.set(DataComponents.WRITABLE_BOOK_CONTENT, new WritableBookContent(
                java.util.stream.IntStream.range(0, pages).mapToObj(i -> Filterable.passThrough("Page " + (i + 1))).toList()));
        lectern(p, pos).setBook(book, p);
        LecternBlock.resetBookState(p, p.level(), pos, p.level().getBlockState(pos), true);
    }
    private static void menu(ClientGameTestContext c) {
        c.getInput().pressKey(GLFW.GLFW_KEY_T); c.waitForScreen(ChatScreen.class);
        c.getInput().typeChars("/ri"); c.getInput().pressKey(GLFW.GLFW_KEY_ENTER); c.waitForScreen(RemoteScreen.class);
    }
    private static Button pageButton(ClientGameTestContext c, String key) {
        return c.computeOnClient(mc -> mc.gui.screen().children().stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(b -> b.getMessage().getString().equals(RemoteScreen.tr("book." + key).getString())).findFirst().orElseThrow());
    }
    private static void awaitState(ClientGameTestContext c, UUID id, String state) {
        c.waitFor(mc -> RemoteClient.entries().stream().anyMatch(e -> e.id().equals(id) && e.state().equals(state)));
    }
    private static void turn(ServerPlayer p, UUID id, int direction) {
        RemoteService.forget(p.getUUID()); RemoteService.turnPage(p, new RemotePayloads.TurnPage(id, direction));
    }
    private static void clickGroup(ClientGameTestContext c, int button) {
        var point = c.computeOnClient(mc -> {
            var b = (Button) mc.gui.screen().children().getFirst();
            return new double[]{(b.getX() + 10.0) * mc.getWindow().getScreenWidth() / mc.gui.screen().width,
                    (b.getY() + 12.0) * mc.getWindow().getScreenHeight() / mc.gui.screen().height};
        });
        c.getInput().setCursorPos(point[0], point[1]); c.waitTicks(5); c.getInput().pressMouse(button); c.waitTicks(5);
    }
    static void run(ClientGameTestContext c) {
        UseBlockCallback.EVENT.register((p, l, h, hit) -> veto && hit.getBlockPos().equals(BOOK) ? InteractionResult.FAIL : InteractionResult.PASS);
        TestWorldSave save; UUID id;
        try (var world = c.worldBuilder().create()) {
            save = world.getWorldSave(); world.getConnection().waitForChunksDownload();
            world.getServer().runCommand("fill -5 80 -5 8 80 5 minecraft:stone");
            world.getServer().runCommand("setblock 0 81 0 minecraft:lectern[facing=south]");
            world.getServer().runCommand("gamemode survival @a");
            world.getServer().runCommand("tp @a 0.5 81 2.5 180 30");
            world.getServer().runOnServer(s -> setBook(s.getPlayerList().getPlayers().getFirst(), BOOK, false, 3));
            world.getConnection().waitForClientboundPackets();
            menu(c); c.clickScreenButton("remote_interaction.attach");
            c.runOnClient(mc -> { var eye = mc.player.getEyePosition(); double dz = .5 - eye.z, dy = 81.75 - eye.y;
                mc.player.setYRot(180); mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, Math.abs(dz)))); });
            c.waitTicks(5); c.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            c.waitFor(mc -> RemoteClient.entries().size() == 1); c.waitTicks(15);
            c.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
            c.runOnClient(mc -> check(mc.gui.screen() == null, "Attaching a lectern does not open its book"));
            id = world.getServer().computeOnServer(s -> ReceiverData.get(s).at(s.overworld().dimension().identifier(), BOOK).id());
            awaitState(c, id, "page_1_3");
            world.getServer().runCommand("tp @a 7.5 81 4.5"); world.getConnection().waitForClientboundPackets();
            menu(c);
            check(!pageButton(c, "previous").active && pageButton(c, "next").active, "First-page controls reflect bounds");
            c.clickScreenButton("remote_interaction.book.next"); awaitState(c, id, "page_2_3");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                check(!p.isWithinBlockInteractionRange(BOOK, 0), "Lectern works beyond ordinary interaction reach");
                check(lectern(p, BOOK).getPage() == 1 && lectern(p, BOOK).getRedstoneSignal() == 8, "Page change updates comparator output");
                check(p.containerMenu == p.inventoryMenu, "Remote page turn never opens a vanilla container"); });
            c.takeScreenshot("lectern-panel-ko"); c.waitTicks(5);
            c.clickScreenButton("remote_interaction.book.next"); awaitState(c, id, "page_3_3");
            check(!pageButton(c, "next").active && pageButton(c, "previous").active, "Last-page controls reflect bounds");
            c.waitTicks(5); c.clickScreenButton("remote_interaction.book.previous"); awaitState(c, id, "page_2_3");
            c.getInput().resizeWindow(640, 480); c.waitTicks(3); c.takeScreenshot("lectern-panel-small-ko");
            c.getInput().resizeWindow(1000, 700); c.waitTicks(3);
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst(); var data = ReceiverData.get(s);
                data.update(data.get(id).withSlot(0, true));
                p.level().setBlockAndUpdate(SECOND, Blocks.LECTERN.defaultBlockState()); setBook(p, SECOND, true, 2);
                p.level().setBlockAndUpdate(LEVER, Blocks.LEVER.defaultBlockState().setValue(LeverBlock.FACE, AttachFace.FLOOR));
                for (var pos : List.of(SECOND, LEVER)) data.add(new Receiver(UUID.randomUUID(), p.getUUID(), p.level().dimension().identifier(), pos,
                        Direction.UP, BuiltInRegistries.BLOCK.getKey(p.level().getBlockState(pos).getBlock()), "", false, false, List.of(0)));
                RemoteService.sync(p, "");
            });
            c.waitFor(mc -> RemoteClient.entries().size() == 3); c.clickScreenButton("gui.done");
            c.getInput().holdControl(); c.getInput().holdAlt(); c.getInput().holdKey(GLFW.GLFW_KEY_R); c.waitForScreen(QuickScreen.class);
            clickGroup(c, GLFW.GLFW_MOUSE_BUTTON_LEFT); awaitState(c, id, "page_3_3");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                check(lectern(p, SECOND).getPage() == 1 && p.level().getBlockState(LEVER).getValue(LeverBlock.POWERED), "Mixed slot advances both book types and toggles lever"); });
            c.takeScreenshot("lectern-quick-slot-ko");
            clickGroup(c, GLFW.GLFW_MOUSE_BUTTON_RIGHT); awaitState(c, id, "page_2_3");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                check(lectern(p, SECOND).getPage() == 0 && p.level().getBlockState(LEVER).getValue(LeverBlock.POWERED), "Right-click changes lecterns only"); });
            c.getInput().releaseKey(GLFW.GLFW_KEY_R); c.getInput().releaseControl(); c.getInput().releaseAlt(); c.waitTick();
            world.getServer().runOnServer(s -> {
                var p = s.getPlayerList().getPlayers().getFirst(); var data = ReceiverData.get(s); var book = lectern(p, BOOK);
                turn(p, id, 1); check(book.getPage() == 2 && p.level().getBlockState(BOOK).getValue(LecternBlock.POWERED), "Vanilla page change emits a redstone pulse");
                RemoteService.turnPage(p, new RemotePayloads.TurnPage(id, -1)); check(book.getPage() == 2, "Page-turn requests share the activation cooldown");
                turn(p, id, 1); check(book.getPage() == 2, "Last page does not wrap");
                for (int invalid : new int[]{0, 2, Integer.MIN_VALUE, Integer.MAX_VALUE}) { turn(p, id, invalid); check(book.getPage() == 2, "Malformed page direction is rejected"); }
                veto = true; try { turn(p, id, -1); check(book.getPage() == 2, "Protection callback denies page turns"); } finally { veto = false; }
                for (var mode : List.of(GameType.SPECTATOR, GameType.ADVENTURE)) { p.setGameMode(mode); turn(p, id, -1); check(book.getPage() == 2, "Restricted mode cannot turn pages"); }
                p.setGameMode(GameType.SURVIVAL);
                var own = data.get(id);
                data.update(new Receiver(id, UUID.randomUUID(), own.dimension(), own.pos(), own.face(), own.block(), "", false, false));
                turn(p, id, -1); check(book.getPage() == 2, "Another owner's lectern is protected"); data.update(own);
                var unloaded = new Receiver(UUID.randomUUID(), p.getUUID(), own.dimension(), new BlockPos(100000, 81, 100000), own.face(), own.block(), "", false, false, List.of(0));
                data.add(unloaded); turn(p, unloaded.id(), -1); check(!p.level().hasChunkAt(unloaded.pos()), "Page requests never load remote chunks");
                RemoteService.forget(p.getUUID()); RemoteService.previousSlotPage(p, 0);
                check(book.getPage() == 1 && lectern(p, SECOND).getPage() == 0, "Slot skips first-page and unloaded lecterns while turning ready books"); data.remove(unloaded.id());
                var leverId = data.at(p.level().dimension().identifier(), LEVER).id(); turn(p, leverId, -1);
                check(p.level().getBlockState(LEVER).getValue(LeverBlock.POWERED), "Forged page request cannot activate a lever");
                book.setBook(ItemStack.EMPTY); LecternBlock.resetBookState(p, p.level(), BOOK, p.level().getBlockState(BOOK), false);
                RemoteService.sync(p, "");
            });
            menu(c); awaitState(c, id, "no_book");
            check(!pageButton(c, "previous").active && !pageButton(c, "next").active, "Empty lectern disables page buttons");
            c.takeScreenshot("lectern-no-book-ko");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                check(RemoteService.activate(p, ReceiverData.get(s).get(id)).equals("no_book"), "No book is reported without opening a screen");
                setBook(p, BOOK, false, 0); turn(p, id, 1); check(lectern(p, BOOK).getPage() == 0, "Zero-page books cannot turn"); RemoteService.sync(p, ""); });
            awaitState(c, id, "empty_book");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                setBook(p, BOOK, true, 1); turn(p, id, -1); turn(p, id, 1); check(lectern(p, BOOK).getPage() == 0, "Single-page books stay on their only page");
                setBook(p, BOOK, false, 3); turn(p, id, 1); RemoteService.sync(p, ""); });
            awaitState(c, id, "page_2_3"); c.clickScreenButton("gui.done");
        }
        try (var world = save.open()) {
            world.getConnection().waitForChunksDownload(); awaitState(c, id, "page_2_3");
            world.getServer().runOnServer(s -> { var p = s.getPlayerList().getPlayers().getFirst();
                check(lectern(p, BOOK).getPage() == 1 && ReceiverData.get(s).get(id).slots().equals(List.of(0)), "World reload preserves actual page and receiver slots"); });
        }
    }
}
