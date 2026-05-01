package ca.dnamobile.javalauncher.renderer;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class PanfrostRenderer implements RendererInterface {
    @NonNull
    @Override
    public String getRendererId() {
        return "gallium_panfrost";
    }

    @NonNull
    @Override
    public String getUniqueIdentifier() {
        return "9b2808c4-11af-4c72-a9c6-94c940396475";
    }

    @NonNull
    @Override
    public String getRendererName() {
        return "Panfrost (Mali)";
    }

    @NonNull
    @Override
    public String getRendererDescription() {
        return "Gallium renderer intended for compatible Mali/Panfrost devices.";
    }

    @NonNull
    @Override
    public Map<String, String> getRendererEnv() {
        java.util.LinkedHashMap<String, String> env = new java.util.LinkedHashMap<>();
        env.put("GALLIUM_DRIVER", "panfrost");
        env.put("MESA_LOADER_DRIVER_OVERRIDE", "panfrost");
        return env;
    }

    @NonNull
    @Override
    public List<String> getDlopenLibrary() {
        return Collections.emptyList();
    }

    @NonNull
    @Override
    public String getRendererLibrary() {
        return "libOSMesa_2300d.so";
    }

    @Override
    public String getRendererEGL() {
        return getRendererLibrary();
    }
}
