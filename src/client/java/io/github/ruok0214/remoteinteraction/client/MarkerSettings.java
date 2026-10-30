package io.github.ruok0214.remoteinteraction.client;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.*;

/** Immutable appearances are shared safely with the render thread; previews never overwrite saved settings. */
public final class MarkerSettings {
    private final Path file;
    private volatile MarkerAppearance saved=MarkerAppearance.DEFAULT,preview;
    public MarkerSettings(Path file){this.file=file;}
    public MarkerAppearance get(){return saved;}
    public MarkerAppearance current(){var draft=preview;return draft==null?saved:draft;}
    public void preview(MarkerAppearance value){preview=value;}
    public void clearPreview(){preview=null;}
    public static MarkerSettings load(){
        var settings=new MarkerSettings(FabricLoader.getInstance().getConfigDir().resolve("remote-interaction-markers.json"));
        settings.read();return settings;
    }
    public void read(){
        if(!Files.exists(file))return;
        try{
            var json=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            saved=new MarkerAppearance(json.get("color").getAsInt(),json.get("transparency").getAsInt(),json.get("lineWidth").getAsFloat());
        }catch(IOException|RuntimeException e){LoggerFactory.getLogger(getClass()).warn("Cannot load marker appearance",e);}
    }
    public boolean save(MarkerAppearance value){
        try{
            Files.createDirectories(file.getParent());
            var temporary=file.resolveSibling(file.getFileName()+".tmp");
            Files.writeString(temporary,new GsonBuilder().setPrettyPrinting().create().toJson(value));
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
            saved=value;return true;
        }catch(IOException e){LoggerFactory.getLogger(getClass()).warn("Cannot save marker appearance",e);return false;}
    }
}
