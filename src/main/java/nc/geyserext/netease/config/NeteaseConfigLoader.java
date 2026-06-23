/*
 * Copyright (c) 2026 EiluDick/ZDarkZ
 * Released under the MIT License.
 */

package nc.geyserext.netease.config;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.geysermc.geyser.api.extension.Extension;
import java.io.*;
import java.nio.file.*;
import java.util.Collections;

public final class NeteaseConfigLoader {
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static NeteaseConfig load(Extension ext, Class<?> mainClass) {
        File f = ext.dataFolder().resolve("config.yml").toFile();
        ext.dataFolder().toFile().mkdirs();
        if (!f.exists()) {
            try (FileWriter w = new FileWriter(f);
                 FileSystem fs = FileSystems.newFileSystem(
                     new File(mainClass.getProtectionDomain().getCodeSource().getLocation().toURI()).toPath(),
                     Collections.emptyMap());
                 InputStream in = Files.newInputStream(fs.getPath("config.yml"))) {
                w.write(new String(in.readAllBytes()));
            } catch (Exception e) { ext.logger().error("Failed to create default config", e); return null; }
        }
        try {
            return new ObjectMapper(new YAMLFactory())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(f, NeteaseConfig.class);
        } catch (IOException e) { ext.logger().error("Failed to load config", e); return null; }
    }
}
