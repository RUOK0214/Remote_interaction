package io.github.ruok0214.remoteinteraction.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;

public final class QuickLayoutSettings {
    private final Path file;
    private QuickLayout layout=QuickLayout.DEFAULT;
    public QuickLayoutSettings(Path file){this.file=file;}
    public QuickLayout get(){return layout;}
    public static QuickLayoutSettings load(){
        var settings=new QuickLayoutSettings(FabricLoader.getInstance().getConfigDir().resolve("remote-interaction-window.json"));
        settings.read();return settings;
    }
    public void read(){
        if(!Files.exists(file))return;
        try{
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            layout=new QuickLayout(json.get("x").getAsDouble(),json.get("y").getAsDouble(),json.get("width").getAsDouble(),json.get("height").getAsDouble());
        }catch(IOException|RuntimeException e){LoggerFactory.getLogger(getClass()).warn("Cannot load quick window layout",e);}
    }
    public boolean save(QuickLayout value){
        try{
            Files.createDirectories(file.getParent());
            var temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(value));
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            layout=value;return true;
        }catch(IOException e){LoggerFactory.getLogger(getClass()).warn("Cannot save quick window layout",e);return false;}
    }
}
