/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease;

import nc.geyserext.netease.config.*;
import nc.geyserext.netease.util.*;
import org.geysermc.event.subscribe.Subscribe;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostInitializeEvent;
import org.geysermc.geyser.api.event.lifecycle.GeyserPostReloadEvent;
import org.geysermc.geyser.api.extension.*;

public class NeteaseExtension implements Extension {

    public static ExtensionLogger LOG;
    public static NeteaseConfig CONFIG;

    @Subscribe
    public void onPostInit(GeyserPostInitializeEvent event) {
        apply("starting");
    }

    /**
     * A Geyser reload rebuilds Geyser's own RakNet listener, which would drop the NetEase pipeline,
     * and it re-reads neither this extension's config nor its listener. Re-applying both here keeps
     * {@code /geyser reload} usable instead of requiring a full server restart.
     */
    @Subscribe
    public void onPostReload(GeyserPostReloadEvent event) {
        apply("reloading");
    }

    private void apply(String what) {
        LOG = logger();
        LOG.info("NetEase Extension " + what + "...");

        CONFIG = NeteaseConfigLoader.load(this, NeteaseExtension.class);
        if (CONFIG == null) {
            LOG.error("NetEase Extension could not read its config; leaving Geyser's listener as it is.");
            return;
        }

        try {
            ServerRestartUtil.restart(CONFIG.onlyNeteaseClients());
            LOG.info("NetEase Extension initialized — RakNet v8 clients supported.");
        } catch (Exception e) { LOG.error("Init failed", e); }
    }
}
