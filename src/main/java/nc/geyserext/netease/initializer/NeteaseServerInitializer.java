/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.initializer;

import nc.geyserext.netease.handler.NetEaseUpstreamHandler;
import nc.geyserext.netease.handler.UpstreamHandlerBase;
import io.netty.channel.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.protocol.bedrock.*;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.*;
import org.cloudburstmc.protocol.bedrock.netty.codec.packet.*;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockServerInitializer;
import org.geysermc.geyser.*;
import org.geysermc.geyser.network.*;
import org.geysermc.geyser.session.GeyserSession;

public class NeteaseServerInitializer extends BedrockServerInitializer {
    private static final int NETEASE_RAKNET = 8;
    private final GeyserImpl geyser;
    private final boolean rakCookie;
    private final boolean onlyNeteaseClients;
    private final DefaultEventLoopGroup elg = new DefaultEventLoopGroup(0, new DefaultThreadFactory("Geyser player thread"));

    public NeteaseServerInitializer(GeyserImpl geyser, boolean rakCookie, boolean onlyNeteaseClients) {
        this.geyser = geyser; this.rakCookie = rakCookie; this.onlyNeteaseClients = onlyNeteaseClients;
    }
    public DefaultEventLoopGroup eventLoopGroup() { return elg; }

    @Override
    protected void preInitChannel(Channel c) throws Exception {
        if (!rakCookie) c.setOption(RakChannelOption.RAK_PROTOCOL_VERSION, 11);
        super.preInitChannel(c);
        int rakVer = c.config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
        if (rakVer == NETEASE_RAKNET) c.pipeline().replace(CompressionCodec.NAME, CompressionCodec.NAME, new CompressionCodec(new SimpleCompressionStrategy(new NoopCompression()), false));
    }

    @Override
    protected void initPacketCodec(Channel c) throws Exception {
        int rakVer = c.config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
        if (rakVer == NETEASE_RAKNET) { c.pipeline().addLast(BedrockPacketCodec.NAME, new BedrockPacketCodec_v3()); return; }
        super.initPacketCodec(c);
    }

    @Override
    public void initSession(@NonNull BedrockServerSession srv) {
        try {
            srv.setLogging(geyser.config().debugMode());
            GeyserSession session = new GeyserSession(geyser, srv, elg.next());
            if (!srv.isSubClient()) {
                Channel c = srv.getPeer().getChannel();
                try {
                    c.pipeline().addAfter(BedrockPacketCodec.NAME, InvalidPacketHandler.NAME, new InvalidPacketHandler(session));
                } catch (Exception ignored) {}
            }
            int rakVer = srv.getPeer().getChannel().config().getOption(RakChannelOption.RAK_PROTOCOL_VERSION);
            if (rakVer == NETEASE_RAKNET) {
                srv.setPacketHandler(new NetEaseUpstreamHandler(geyser, session));
            } else {
                if (onlyNeteaseClients) {
                    session.disconnect("This server only accepts NetEase clients.");
                    return;
                }
                srv.setPacketHandler(new UpstreamHandlerBase(geyser, session));
            }
        } catch (Throwable e) { geyser.getLogger().error("Error initializing player!", e); srv.disconnect(e.getMessage()); }
    }

    @Override
    protected BedrockPeer createPeer(Channel c) { return new GeyserBedrockPeer(c, this::createSession); }
}
