/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

import java.util.List;

public final class NeteaseConfig {
    @JsonProperty("only-netease-clients")
    private boolean onlyNeteaseClients;
    @JsonProperty("debug-mode")
    private boolean debugMode;
    @JsonProperty("disconnect-message")
    private String disconnectMessage = "This server only accepts NetEase clients.";

    /**
     * Whether to send a skin confirmation for the local player as soon as the game starts.
     *
     * <p>At that point no skin data exists yet, so the entry is empty, and NetEase clients have been
     * observed to drop the connection on it. Real skins are confirmed later from the actual
     * PlayerSkin/PlayerList packets, so this is off by default.</p>
     */
    @JsonProperty("confirm-skin-at-start-game")
    private boolean confirmSkinAtStartGame;

    /**
     * Packet simple names never sent to NetEase clients.
     *
     * <p>This used to default to dropping the item and creative content packets, which turned out
     * to be the wrong trade: the NetEase client dies as soon as the player opens the creative
     * inventory without them. They are now sent, with the block IDs inside translated to hashed
     * network IDs, and only appear here if a specific server still proves to need them gone.</p>
     *
     * <p>Leaving the key out keeps this default. Giving it an empty value in any form
     * ({@code dropped-packets:}, {@code dropped-packets: []}) also means "send everything", because
     * in YAML a bare {@code key:} is null and that is far too easy to write by accident.</p>
     */
    private List<String> droppedPackets = List.of();

    @JsonSetter("dropped-packets")
    public void setDroppedPackets(List<String> droppedPackets) {
        this.droppedPackets = droppedPackets != null ? droppedPackets : List.of();
    }

    /**
     * Whether block network IDs are rewritten to the NetEase hash of the block state.
     *
     * <p>NetEase clients resolve blocks by hash rather than by palette index, which is what the
     * rewrite provides. The hash is computed from Geyser's block states, so it only matches the
     * client for blocks whose state NBT is unchanged between the client's Bedrock version and
     * Geyser's. Turn this off to send Geyser's plain index IDs instead: blocks will be wrong, but
     * it tells us whether the hash mismatch is what kills the client.</p>
     */
    @JsonProperty("block-network-ids-hashed")
    private boolean blockNetworkIdsHashed = true;

    /**
     * Whether to strip the block property table out of the StartGame packet.
     *
     * <p>Geyser fills it with block definitions built for its own (newer) Bedrock version. An older
     * NetEase client has its own registry and may fail parsing them. On by default because that is
     * the combination verified against a real 1.21.124 client; set it to false to check whether it
     * is still needed, at the cost of data-driven blocks becoming unknown to the client.</p>
     */
    @JsonProperty("clear-start-game-block-properties")
    private boolean clearStartGameBlockProperties = true;

    /**
     * Send the creative content packet with no items at all.
     *
     * <p>Testing switch. The block IDs inside it are usually translated to hashed network IDs; if
     * the client still dies on the creative inventory, an empty list shows whether the remaining
     * entries themselves are the problem.</p>
     */
    @JsonProperty("empty-creative-content")
    private boolean emptyCreativeContent;

    public boolean onlyNeteaseClients() { return onlyNeteaseClients; }
    public boolean debugMode() { return debugMode; }
    public String disconnectMessage() { return disconnectMessage; }
    public boolean confirmSkinAtStartGame() { return confirmSkinAtStartGame; }
    public List<String> droppedPackets() { return droppedPackets; }
    public boolean blockNetworkIdsHashed() { return blockNetworkIdsHashed; }
    public boolean clearStartGameBlockProperties() { return clearStartGameBlockProperties; }
    public boolean emptyCreativeContent() { return emptyCreativeContent; }
}
