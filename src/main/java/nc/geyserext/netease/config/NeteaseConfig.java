/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.config;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class NeteaseConfig {
    @JsonProperty("auth-mode")
    private String authMode = "auto";
    @JsonProperty("only-netease-clients")
    private boolean onlyNeteaseClients;
    @JsonProperty("debug-mode")
    private boolean debugMode;

    public String authMode() { return authMode; }
    public boolean onlyNeteaseClients() { return onlyNeteaseClients; }
    public boolean debugMode() { return debugMode; }
}
