package io.github.ruok0214.remoteinteraction.client;

import com.terraformersmc.modmenu.api.*;
import net.minecraft.client.gui.screens.Screen;

public final class RemoteModMenu implements ModMenuApi {
    @Override public ConfigScreenFactory<Screen> getModConfigScreenFactory(){return RemoteScreen::new;}
}
