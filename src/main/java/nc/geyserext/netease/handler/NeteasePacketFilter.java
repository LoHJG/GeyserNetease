/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.handler;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import nc.geyserext.netease.NeteaseExtension;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.BedrockPacketCodec;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.geysermc.geyser.GeyserImpl;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NetEase session guard, placed between the packet codec and the peer.
 *
 * <p><b>Outbound filtering.</b> NetEase sessions report Geyser's newest protocol version (so block
 * and item registries resolve), but the wire codec is a NetEase codec derived from an older Bedrock
 * version. Geyser therefore emits packets that did not exist when that codec was built, and
 * {@code BedrockPacketCodec.getPacketId} dereferences a null {@code BedrockPacketDefinition}, which
 * kills the whole session. Such a packet cannot be put on the wire in any form and the NetEase
 * client predates it anyway, so it is skipped.</p>
 *
 * <p><b>Packet tracing.</b> When the extension's {@code debug-mode} is on, every inbound and
 * outbound packet is logged. The Bedrock stack only logs packets at TRACE, which a normal server
 * configuration never enables, so this is the only way to see how far a session got.</p>
 */
public class NeteasePacketFilter extends ChannelDuplexHandler {
    public static final String NAME = "geyser-netease-packet-filter";

    /** Keeps the dropped-packet notice to one line per packet type for the whole JVM. */
    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();
    /** Fallback when the config cannot be read; mirrors NeteaseConfig's default. */
    private static final Set<String> DEFAULT_DROPS = Set.of("ItemComponentPacket", "CreativeContentPacket");

    /** Packet simple names this session never sends, resolved once from config. */
    private final Set<String> droppedPackets;

    public NeteasePacketFilter() {
        var configured = NeteaseExtension.CONFIG != null ? NeteaseExtension.CONFIG.droppedPackets() : null;
        // The config resolves an empty value to an empty list, so this is "drop nothing" then;
        // only a missing config (CONFIG == null) falls back to the built-in default.
        this.droppedPackets = configured != null ? Set.copyOf(configured) : DEFAULT_DROPS;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof BedrockPacketWrapper wrapper) {
            BedrockPacket packet = wrapper.getPacket();
            if (packet != null) {
                String name = packet.getClass().getSimpleName();
                if (droppedPackets.contains(name)) {
                    report(packet, "configured as unsupported by the NetEase client");
                    // The wrapper is pooled and reference counted; release it since it is never encoded.
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess();
                    return;
                }
                BedrockCodec codec = currentCodec(ctx);
                if (codec != null && codec.getPacketDefinition(packet.getClass()) == null) {
                    report(packet, "the active NetEase codec has no definition for it");
                    ReferenceCountUtil.release(msg);
                    promise.setSuccess();
                    return;
                }
            }
            trace("OUT", packet);
        }
        ctx.write(msg, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof BedrockPacketWrapper wrapper) {
            trace("IN ", wrapper.getPacket());
        }
        ctx.fireChannelRead(msg);
    }

    private static BedrockCodec currentCodec(ChannelHandlerContext ctx) {
        BedrockPacketCodec codecHandler = ctx.pipeline().get(BedrockPacketCodec.class);
        return codecHandler != null ? codecHandler.getCodec() : null;
    }

    private static boolean debugEnabled() {
        return NeteaseExtension.CONFIG != null && NeteaseExtension.CONFIG.debugMode();
    }

    private static void trace(String direction, BedrockPacket packet) {
        if (packet == null || !debugEnabled()) {
            return;
        }
        info("[GeyserNetease] " + direction + " " + packet.getClass().getSimpleName());
    }

    /** Logged once per packet type per JVM, at info: a dropped packet changes what the client sees. */
    private static void report(BedrockPacket packet, String why) {
        if (!REPORTED.add(packet.getClass().getName())) {
            return;
        }
        info("[GeyserNetease] Skipping " + packet.getClass().getSimpleName() + ": " + why + ".");
    }

    private static void info(String message) {
        if (NeteaseExtension.LOG != null) {
            NeteaseExtension.LOG.info(message);
            return;
        }
        GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser != null) {
            geyser.getLogger().info(message);
        }
    }
}
