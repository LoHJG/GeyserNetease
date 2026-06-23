package org.cloudburstmc.protocol.bedrock.netty.codec.compression;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import org.cloudburstmc.protocol.bedrock.data.CompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.common.util.Zlib;

import java.util.zip.DataFormatException;

public class NetEaseCompression implements BatchCompression {
    private static final InternalLogger log = InternalLoggerFactory.getInstance(NetEaseCompression.class);
    private static final int MAX_DECOMPRESSED_BYTES = Integer.getInteger("bedrock.maxDecompressedBytes", 1024 * 1024 * 10);

    private int level = 7;
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    @Override
    public ByteBuf encode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        ByteBuf outBuf = ctx.alloc().ioBuffer(msg.readableBytes() << 3);
        try {
            Zlib.RAW.deflate(msg, outBuf, level);
            return outBuf.retain();
        } finally {
            outBuf.release();
        }
    }

    @Override
    public ByteBuf decode(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        if (!msg.isReadable()) {
            throw new DataFormatException("Cannot decompress empty data");
        }

        msg.markReaderIndex();
        try {
            return Zlib.RAW.inflate(msg, MAX_DECOMPRESSED_BYTES);
        } catch (DataFormatException e) {
            if (log.isWarnEnabled()) {
                log.warn("Raw deflate decompression failed: " + e.getMessage());
            }
            msg.resetReaderIndex();
        }

        try {
            return Zlib.DEFAULT.inflate(msg, MAX_DECOMPRESSED_BYTES);
        } catch (DataFormatException e) {
            if (log.isWarnEnabled()) {
                log.warn("Standard zlib decompression also failed: " + e.getMessage());
            }
            msg.resetReaderIndex();
        }

        if (log.isWarnEnabled()) {
            log.warn("All decompression methods failed, returning raw data");
        }
        return msg.retainedSlice();
    }

    @Override
    public CompressionAlgorithm getAlgorithm() {
        return PacketCompressionAlgorithm.ZLIB;
    }
}
