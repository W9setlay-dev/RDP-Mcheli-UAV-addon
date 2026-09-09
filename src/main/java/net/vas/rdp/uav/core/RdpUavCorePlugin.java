package net.vas.rdp.uav.core;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;

import java.util.Map;

public final class RdpUavCorePlugin implements IFMLLoadingPlugin {
    @Override
    public String[] getASMTransformerClass() {
        return new String[] {"net.vas.rdp.uav.core.RdpUavTransformer"};
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
