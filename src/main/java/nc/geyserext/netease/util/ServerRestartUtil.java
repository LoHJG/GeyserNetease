/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.util;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.util.NettyRuntime;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.internal.SystemPropertyUtil;
import nc.geyserext.netease.initializer.NeteaseServerInitializer;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.*;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerOfflineHandler;
import org.geysermc.geyser.*;
import org.geysermc.geyser.configuration.GeyserConfig;
import org.geysermc.geyser.network.RaknetServer;
import org.geysermc.geyser.network.BedrockPingHandler;
import org.geysermc.geyser.network.bedrock.raknet.Bootstraps;
import org.geysermc.geyser.network.bedrock.raknet.RakConnectionRequestHandler;
import org.geysermc.geyser.network.bedrock.raknet.RakPingHandler;
import org.geysermc.mcprotocollib.network.helper.TransportHelper;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import static org.cloudburstmc.netty.channel.raknet.RakConstants.*;

/**
 * Restarts Geyser's RakNet listener so NetEase (RakNet protocol v8) clients are accepted.
 *
 * <p>A single RakNet listener cannot serve two protocol versions at once, so the stock
 * listener is torn down and a replacement is bound to the same UDP port, with the same
 * options Geyser used, but installing {@link NeteaseServerInitializer} to select the
 * NetEase pipeline per connection.</p>
 *
 * <p>Geyser 2.12 moved the listener from {@code org.geysermc.geyser.network.netty.GeyserServer}
 * to {@link RaknetServer} in {@code org.geysermc.geyser.network}. The private fields mutated
 * below mirror that class.</p>
 */
public final class ServerRestartUtil {
    private static final TransportHelper.TransportType TRANSPORT = TransportHelper.TRANSPORT_TYPE;
    private ServerRestartUtil() {}

    public static void restart(boolean onlyNeteaseClients) {
        try { doRestart(onlyNeteaseClients); }
        catch (NoClassDefFoundError e) {
            GeyserImpl.getInstance().getLogger().info("[GeyserNetease] Mod platform detected; skipping server restart. NetEase support active through channel hook.");
        }
        catch (Exception e) {
            GeyserImpl.getInstance().getLogger().error("[GeyserNetease] Failed to restart bedrock listener", e);
        }
    }

    private static void doRestart(boolean onlyNeteaseClients) throws Exception {
        GeyserImpl geyser = GeyserImpl.getInstance();
        RaknetServer server = geyser.getGeyserServer();
        if (server == null) {
            // Geyser runs with a transport that has no RakNet listener (e.g. NetherNet only).
            geyser.getLogger().warning("[GeyserNetease] No RakNet listener to restart, so NetEase clients cannot connect. "
                + "Set bedrock.transport to \"raknet\" or \"both\" in Geyser's config.yml and restart.");
            return;
        }

        Field futuresField = RaknetServer.class.getDeclaredField("bootstrapFutures");
        futuresField.setAccessible(true);
        ChannelFuture[] oldFutures = (ChannelFuture[]) futuresField.get(server);

        server.shutdown();

        Integer bt = Integer.getInteger("Geyser.BedrockNetworkThreads");
        if (bt == null || bt < 1) {
            // Mirror Geyser's own fallback for the RakNet child thread count.
            bt = Math.max(1, SystemPropertyUtil.getInt("io.netty.eventLoopThreads", NettyRuntime.availableProcessors() * 2));
        }

        EventLoopGroup g = TRANSPORT.eventLoopGroupFactory().apply(Bootstraps.isReusePortAvailable() ? Integer.getInteger("Geyser.ListenCount", 1) : 1, new DefaultThreadFactory("GeyserServer", true));
        EventLoopGroup cg = TRANSPORT.eventLoopGroupFactory().apply(bt, new DefaultThreadFactory("GeyserServerChild", true));

        GeyserConfig cfg = geyser.config();
        boolean rakCookie = Boolean.parseBoolean(System.getProperty("Geyser.RakSendCookie", "true"));
        boolean rateLimitingDisabled = Boolean.parseBoolean(System.getProperty(
            "Geyser.RakRateLimitingDisabled",
            Boolean.toString(cfg.advanced().bedrock().useWaterdogpeForwarding())));

        NeteaseServerInitializer init = new NeteaseServerInitializer(geyser, rakCookie, onlyNeteaseClients);
        // The replacement listener must answer pings for the same server identity.
        BedrockPingHandler pingResponder = new BedrockPingHandler(geyser);

        ServerBootstrap bs = new ServerBootstrap()
            .channelFactory(RakChannelFactory.server(TRANSPORT.datagramChannelClass()))
            .group(g, cg)
            .option(RakChannelOption.RAK_HANDLE_PING, true)
            .option(RakChannelOption.RAK_MAX_MTU, cfg.advanced().bedrock().mtu())
            .option(RakChannelOption.RAK_PACKET_LIMIT, rateLimitingDisabled ? 0 : positiveProp("Geyser.RakPacketLimit", DEFAULT_PACKET_LIMIT))
            .option(RakChannelOption.RAK_GLOBAL_PACKET_LIMIT, positiveProp("Geyser.RakGlobalPacketLimit", DEFAULT_GLOBAL_PACKET_LIMIT))
            .option(RakChannelOption.RAK_SERVER_COOKIE_MODE, rakCookie ? RakServerCookieMode.ACTIVE : RakServerCookieMode.INVALID)
            .option(RakChannelOption.RAK_PROXY_PROTOCOL, cfg.advanced().bedrock().useHaproxyProtocol())
            .option(RakChannelOption.RAK_THROTTLE, rateLimitingDisabled ? null
                : new DefaultRakServerThrottle(positiveProp("Geyser.MaxConnectionsPerAddress", 10), 4_000, 3))
            .childHandler(init);
        Bootstraps.setupBootstrap(bs, TRANSPORT);

        if (oldFutures == null || oldFutures.length == 0) {
            oldFutures = new ChannelFuture[1];
        }

        InetSocketAddress bindAddress = new InetSocketAddress(cfg.bedrock().address(), cfg.bedrock().raknetPort());
        ChannelFuture[] futures = new ChannelFuture[oldFutures.length];
        for (int i = 0; i < futures.length; i++) {
            ChannelFuture fut = bs.bind(bindAddress);
            fut.addListener((ChannelFutureListener) f -> {
                if (!f.isSuccess()) {
                    geyser.getLogger().warning("[GeyserNetease] Failed to rebind Bedrock listener: " + f.cause());
                    return;
                }
                Channel c = f.channel();
                c.pipeline().addBefore(RakServerOfflineHandler.NAME, RakConnectionRequestHandler.NAME, new RakConnectionRequestHandler(server))
                    .addAfter(RakServerOfflineHandler.NAME, RakPingHandler.NAME, new RakPingHandler(pingResponder));
            });
            futures[i] = fut;
        }
        Bootstraps.allOf(futures).join();

        // Keep the server object consistent so a later shutdown/reload closes the new listener.
        futuresField.set(server, futures);
        setField(RaknetServer.class, "group", server, g);
        setField(RaknetServer.class, "childGroup", server, cg);
        setField(RaknetServer.class, "playerGroup", server, init.eventLoopGroup());
    }

    private static void setField(Class<?> c, String n, Object t, Object v) throws Exception { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(t, v); }
    private static int positiveProp(String p, int d) { String v = System.getProperty(p); try { int x = v != null ? Integer.parseInt(v) : d; return Math.max(x, 1); } catch (NumberFormatException e) { return d; } }
}
