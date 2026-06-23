/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class NeteaseConfig {
    @JsonProperty("only-netease-clients")
    private boolean onlyNeteaseClients;
    @JsonProperty("debug-mode")
    private boolean debugMode;

    public boolean onlyNeteaseClients() { return onlyNeteaseClients; }
    public boolean debugMode() { return debugMode; }
}
