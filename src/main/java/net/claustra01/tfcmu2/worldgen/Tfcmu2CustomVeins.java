package net.claustra01.tfcmu2.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.logging.LogUtils;

import net.claustra01.tfcmu2.Tfcmu2Mod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import org.slf4j.Logger;

/**
 * Loads veins from config/tfcmu2/veins.yaml and builds direct placed features from them.
 * These features are inserted into biomes by {@link Tfcmu2OreVeinBiomeModifier} when enabled via config.
 */
public final class Tfcmu2CustomVeins {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DEFAULT_CONFIG_SUBDIR = Tfcmu2Mod.MOD_ID;
    private static final String DEFAULT_CONFIG_FILE = "veins.yaml";
    private static final String CLASSPATH_SAMPLE = "/tfcmu2/veins.yaml";

    private static List<Tfcmu2VeinsYamlParser.VeinDefinition> CACHED_DEFS = List.of();
    private static List<net.minecraft.core.Holder<PlacedFeature>> CACHED_PLACED_FEATURES = List.of();
    private static boolean BUILT_PLACED_FEATURES = false;

    private Tfcmu2CustomVeins() {
    }

    public static void bootstrap() {
        final Path yamlPath = getConfigYamlPath();
        ensureSampleFileExists(yamlPath);

        final List<Tfcmu2VeinsYamlParser.VeinDefinition> defs;
        try {
            defs = Tfcmu2VeinsYamlParser.parseVeins(yamlPath);
        } catch (Exception e) {
            LOGGER.error("Failed to parse custom veins YAML at {}. Custom vein generation will be unavailable.", yamlPath, e);
            return;
        }

        if (defs.isEmpty()) {
            LOGGER.warn("No veins found in {}. Custom vein generation will be unavailable.", yamlPath);
            return;
        }

        final Set<ResourceLocation> seen = new HashSet<>();
        final List<Tfcmu2VeinsYamlParser.VeinDefinition> accepted = new ArrayList<>(defs.size());
        for (Tfcmu2VeinsYamlParser.VeinDefinition def : defs) {
            final ResourceLocation outId = toTfcmu2Id(def.id());
            if (!seen.add(outId)) {
                LOGGER.warn("Duplicate custom vein id {} (from {}), skipping.", outId, def.id());
                continue;
            }
            if (!canLoadDefinition(def)) {
                continue;
            }
            if (!isSupportedType(def)) {
                continue;
            }

            accepted.add(def);
        }

        if (accepted.isEmpty()) {
            LOGGER.warn("No custom veins were registered from {}.", yamlPath);
        } else {
            // Note: placed/configured features are datapack registries in 1.21.1 and are not part of the mod-registerable builtin registries.
            // We therefore build & inject direct placed features at runtime (see resolvePlacedFeatures).
            LOGGER.info("Loaded {} custom veins from {} (namespace {}).", accepted.size(), yamlPath, Tfcmu2Mod.MOD_ID);
        }
        CACHED_DEFS = List.copyOf(accepted);
        CACHED_PLACED_FEATURES = List.of();
        BUILT_PLACED_FEATURES = false;
    }

    public static List<net.minecraft.core.Holder<PlacedFeature>> resolvePlacedFeatures(net.minecraft.core.Registry<PlacedFeature> placedFeatures) {
        if (CACHED_DEFS.isEmpty()) {
            return List.of();
        }
        if (BUILT_PLACED_FEATURES) {
            return CACHED_PLACED_FEATURES;
        }

        // Build direct placed features lazily, after all builtin registries (including other mods' Feature/Block entries) are available.
        final List<net.minecraft.core.Holder<PlacedFeature>> built = new ArrayList<>(CACHED_DEFS.size());
        for (Tfcmu2VeinsYamlParser.VeinDefinition def : CACHED_DEFS) {
            try {
                final var configured = def.buildConfiguredFeature();
                final var placed = new PlacedFeature(net.minecraft.core.Holder.direct(configured), List.of());
                built.add(net.minecraft.core.Holder.direct(placed));
            } catch (Exception e) {
                LOGGER.error("Failed to build custom vein {}. Skipping.", def.id(), e);
            }
        }

        CACHED_PLACED_FEATURES = List.copyOf(built);
        BUILT_PLACED_FEATURES = true;
        return CACHED_PLACED_FEATURES;
    }

    private static boolean canLoadDefinition(Tfcmu2VeinsYamlParser.VeinDefinition def) {
        final String template = def.blockTemplate();
        final int colonIdx = template.indexOf(':');
        if (colonIdx > 0) {
            final String ns = template.substring(0, colonIdx);
            if (!"minecraft".equals(ns) && !ModList.get().isLoaded(ns)) {
                LOGGER.warn("Skipping custom vein {} because referenced mod '{}' is not loaded (block template: {}).", def.id(), ns, template);
                return false;
            }
        }
        if (def.indicator() != null) {
            final String indicator = def.indicator().block();
            final int idx = indicator.indexOf(':');
            if (idx > 0) {
                final String ns = indicator.substring(0, idx);
                if (!"minecraft".equals(ns) && !ModList.get().isLoaded(ns)) {
                    LOGGER.warn("Skipping custom vein {} because referenced mod '{}' is not loaded (indicator: {}).", def.id(), ns, indicator);
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSupportedType(Tfcmu2VeinsYamlParser.VeinDefinition def) {
        final String type = def.type().toString();
        if (!type.equals("tfc:cluster_vein") && !type.equals("tfc:disc_vein") && !type.equals("tfc:kaolin_disc_vein") && !type.equals("tfc:pipe_vein")) {
            LOGGER.warn("Skipping custom vein {} due to unsupported type '{}'.", def.id(), def.type());
            return false;
        }
        if (type.equals("tfc:pipe_vein") && def.pipe() == null) {
            LOGGER.warn("Skipping custom vein {} because type '{}' requires pipe parameters.", def.id(), def.type());
            return false;
        }
        return true;
    }

    private static ResourceLocation toTfcmu2Id(ResourceLocation sourceId) {
        return ResourceLocation.fromNamespaceAndPath(Tfcmu2Mod.MOD_ID, sourceId.getPath());
    }

    private static Path getConfigYamlPath() {
        return FMLPaths.CONFIGDIR.get().resolve(DEFAULT_CONFIG_SUBDIR).resolve(DEFAULT_CONFIG_FILE);
    }

    private static void ensureSampleFileExists(Path path) {
        if (Files.exists(path)) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            try (InputStream in = Tfcmu2CustomVeins.class.getResourceAsStream(CLASSPATH_SAMPLE)) {
                if (in == null) {
                    // Last resort: create a minimal stub if the sample wasn't packaged.
                    Files.writeString(path, "veins:\n", StandardCharsets.UTF_8);
                    LOGGER.warn("Missing packaged sample {}. Wrote a minimal stub at {}.", CLASSPATH_SAMPLE, path);
                    return;
                }
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.info("Created sample custom veins YAML at {}.", path);
        } catch (IOException e) {
            LOGGER.error("Failed to create sample custom veins YAML at {}.", path, e);
        }
    }
}
