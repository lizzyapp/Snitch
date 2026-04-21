package io.github.lizzyapp.snitch;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModFileInfo;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;
import net.neoforged.neoforgespi.locating.ModFileInfoParser;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(Snitch.MODID)
public class Snitch {
    public static final String MODID = "snitch";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Snitch(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    // for immediate records, will probably let you view a map of all dependencies later
    public record SnitchRecord(String trippedMod, String contains) {
        @Override
        public @NotNull String toString() {
            return trippedMod + " contains " + contains;
        }
    }

    public static boolean tripsDependency(EmbeddedJar embeddedJar) {
        return Config.SNITCH_ON.get().contains(embeddedJar.modId);
    }

    private static final List<SnitchRecord> snitchRecordList = new ArrayList<>();
    public static List<SnitchRecord> getSnitchRecords() {
        return snitchRecordList;
    }
    public static void compileSnitchedMods() {
        ModList modList = ModList.get();
        Optional<? extends ModContainer> myMod = modList.getModContainerById(MODID);
        IModInfo myModInfo = myMod.get().getModInfo();
        Collection<String> ignoredDependencies = myModInfo.getDependencies().stream()
            .map(IModInfo.ModVersion::getModId).collect(Collectors.toSet());
        // almost forgot
        ignoredDependencies.add(MODID);

        long milliseconds = System.currentTimeMillis();
        modList.getMods().forEach((modInfo) -> {
            if (ignoredDependencies.contains(modInfo.getModId()))
                return;

            String currentModID = modInfo.getModId();
            // soft dependencies
//            modInfo.getDependencies().forEach((dependency) -> {
//                if (ignoredDependencies.contains(dependency.getModId()))
//                    return;
//            });

            // embedded dependencies
            IModFile modFile = modInfo.getOwningFile().getFile();
            Path modPath = modFile.getFilePath();

            List<EmbeddedJar> embeddedJarList = findEmbeddedJars(modPath, currentModID);
            if (!embeddedJarList.isEmpty()) {
                if (Config.DISPLAY_ALL.get()) LOGGER.info("Embedded dependencies of " + currentModID + ": " + embeddedJarList);
                embeddedJarList.stream().filter(Snitch::tripsDependency).forEach(
                    (embeddedJar) -> snitchRecordList.add(
                        new SnitchRecord(currentModID, embeddedJar.modId)
                    )
                );
            }
        });
        LOGGER.info("successfully queried all mod dependencies in {} ms", (System.currentTimeMillis() - milliseconds));
        LOGGER.info("IT WAS THEM. " + snitchRecordList);
    }

    static class EmbeddedJar {
        public String modId = "unknown";
        private boolean isMod = false;
        private void setModId(String modId, boolean isMod) {
            this.modId = modId;
            this.isMod = isMod;
        }
        public final String parentModId;
        public final String fileName;

        public EmbeddedJar(String fileName, byte[] data, String parentModId, JsonObject jsonContext) {
            this.fileName = fileName;
            this.parentModId = parentModId;
            Path temporaryJar = null;
            try {
                temporaryJar = Files.createTempFile("extracted-embedded-jar-", ".jar");
                Files.write(temporaryJar, data);

                try (FileSystem fileSystem = FileSystems.newFileSystem(temporaryJar)) {
                    Path tomlPath = fileSystem.getPath("/META-INF/neoforge.mods.toml");
                    if (Files.exists(tomlPath)) {
                        CommentedFileConfig config = CommentedFileConfig.builder(tomlPath).build();
                        config.load();
                        var mods = config.get("mods");
                        if (mods instanceof java.util.List<?> list && !list.isEmpty()) {
                            Object first = list.getFirst();
                            if (first instanceof com.electronwill.nightconfig.core.Config mod) {
                                String modId = mod.get("modId");
                                setModId(modId, true);
                            }
                        }
                        config.close();
                        return;
                    }

                    // isnt a mod, extract embedded library name from jsoncontext instead
                    for (JsonElement element : jsonContext.getAsJsonArray("jars").asList()) {
                        JsonObject jsonObject = element.getAsJsonObject();
                        for (String jarPath : JAR_PATHS) {
                            boolean foundJar = jsonObject.get("path").getAsString().toLowerCase()
                                .contentEquals(String.format("%s/%s", jarPath, fileName).toLowerCase());
                            if (foundJar) {
                                setModId(
                                    jsonObject.getAsJsonObject("identifier")
                                        .get("artifact").getAsString().toLowerCase(), false
                                );
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read embedded jar from {}", parentModId, e);
            } finally {
                try {
                    if (temporaryJar != null) Files.deleteIfExists(temporaryJar);
                } catch (IOException e) {
                    LOGGER.warn("Failed deleting temp embedded jar {}", temporaryJar, e);
                }
            }
        }

        @Override
        public String toString() {
            return this.modId + " as embedded Jar of " + parentModId;
        }
    }

    private static List<EmbeddedJar> findEmbeddedJars(Path jarPath, String modId) {
        try (InputStream stream = Files.newInputStream(jarPath)) {
            return scanStream(stream, modId, new ArrayList<>());
        } catch (Exception e) {
            LOGGER.warn("Failed scanning {}", modId, e);
        }
        return List.of();
    }

    private static final List<String> JAR_PATHS =
        List.of("META-INF/jarjar", "META-INF/jars");
    static boolean nameStartsWithAny(String name) {
        for (String string : JAR_PATHS) {
            if (name.startsWith(string.toLowerCase())) return true;
        }
        return false;
    }

    private static List<EmbeddedJar> scanStream(InputStream stream, String modId, List<EmbeddedJar> embeddedJarList) {
        try (ZipInputStream zipInputStream = new ZipInputStream(stream)) {
            ZipEntry entry;
            byte[] jsonData = null;
            Map<String, byte[]> jarInputStreams = new HashMap<>();
            while ((entry = zipInputStream.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                if (!nameStartsWithAny(name))
                    continue;
                byte[] data = zipInputStream.readAllBytes();
                if (name.endsWith(".json")) jsonData = data;
                if (name.endsWith(".jar")) jarInputStreams.put(name, data);
            }
            if (jsonData == null) return embeddedJarList;
            JsonObject jsonContext = JsonParser.parseReader(
                new InputStreamReader(
                    new ByteArrayInputStream(jsonData),
                    StandardCharsets.UTF_8
                )).getAsJsonObject();
            jarInputStreams.forEach((name, data) -> {
                EmbeddedJar embeddedJar = new EmbeddedJar(
                    name.substring(name.lastIndexOf('/') + 1),
                    data, modId, jsonContext
                );
                embeddedJarList.add(embeddedJar);
                scanStream(new ByteArrayInputStream(data), embeddedJar.modId, embeddedJarList);
            });

        } catch (Exception e) {
            LOGGER.warn("Nested scan failed", e);
        }
        return embeddedJarList;
    }
}
