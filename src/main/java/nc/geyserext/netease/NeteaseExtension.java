/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease;

import nc.geyserext.netease.config.*;
import nc.geyserext.netease.util.*;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.extension.*;

public class NeteaseExtension implements Extension {

    public static ExtensionLogger LOG;
    public static NeteaseConfig CONFIG;

    @Subscribe
    public void onPostInit(GeyserPostInitializeEvent event) {
        LOG = logger();
        LOG.info("NetEase Extension starting...");

        CONFIG = NeteaseConfigLoader.load(this, NeteaseExtension.class);

        try {
            ServerRestartUtil.restart(CONFIG.onlyNeteaseClients());
            LOG.info("NetEase Extension initialized — RakNet v8 clients supported.");
        } catch (Exception e) { LOG.error("Init failed", e); }
    }
}
