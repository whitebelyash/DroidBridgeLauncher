package ca.dnamobile.javalauncher.renderer;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class FreedrenoRenderer implements RendererInterface {
    @NonNull
    @Override
    public String getRendererId() {
        return "gallium_freedreno";
    }

    @NonNull
    @Override
    public String getUniqueIdentifier() {
        return "1ad7249f-5784-4f00-bc72-174b3578ee46";
    }

    @NonNull
    @Override
    public String getRendererName() {
        return "Freedreno (Adreno)";
    }

    @NonNull
    @Override
    public String getRendererDescription() {
        return "Gallium renderer intended for compatible Adreno/Freedreno setups.";
    }

    @NonNull
    @Override
    public Map<String, String> getRendererEnv() {
        java.util.LinkedHashMap<String, String> env = new java.util.LinkedHashMap<>();
        env.put("GALLIUM_DRIVER", "freedreno");
        env.put("MESA_LOADER_DRIVER_OVERRIDE", "freedreno");
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
        return "libOSMesa_8.so";
    }

    @Override
    public String getRendererEGL() {
        return getRendererLibrary();
    }
}
