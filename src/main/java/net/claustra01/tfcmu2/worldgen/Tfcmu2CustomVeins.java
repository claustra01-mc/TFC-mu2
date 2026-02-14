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
import java.util.Optional;
import java.util.Set;

import com.mojang.logging.LogUtils;

import net.claustra01.tfcmu2.Tfcmu2Mod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredHolder;

import org.slf4j.Logger;

/**
 * Loads veins from config/tfcmu2/veins.yaml and registers matching configured/placed features under the tfcmu2 namespace.
 * These features are inserted into biomes by {@link Tfcmu2OreVeinBiomeModifier} when enabled via config.
 */
public final class Tfcmu2CustomVeins {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String DEFAULT_CONFIG_SUBDIR = Tfcmu2Mod.MOD_ID;
    private static final String DEFAULT_CONFIG_FILE = "veins.yaml";
    private static final String CLASSPATH_SAMPLE = "/tfcmu2/veins.yaml";

    private static final List<ResourceLocation> CUSTOM_PLACED_FEATURE_IDS = new ArrayList<>();
    private static Registry<PlacedFeature> CACHED_REGISTRY;
    private static List<Holder<PlacedFeature>> CACHED_HOLDERS = List.of();

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
        int registered = 0;
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

            final DeferredHolder<ConfiguredFeature<?, ?>, ConfiguredFeature<?, ?>> configured = Tfcmu2Worldgen.CONFIGURED_FEATURES.register(outId.getPath(), def::buildConfiguredFeature);
            Tfcmu2Worldgen.PLACED_FEATURES.register(outId.getPath(), () -> new PlacedFeature(configured, List.of()));

            CUSTOM_PLACED_FEATURE_IDS.add(outId);
            registered++;
        }

        if (registered == 0) {
            LOGGER.warn("No custom veins were registered from {}.", yamlPath);
        } else {
            LOGGER.info("Registered {} custom veins from {} (namespace {}).", registered, yamlPath, Tfcmu2Mod.MOD_ID);
        }
    }

    public static List<Holder<PlacedFeature>> resolvePlacedFeatures(Registry<PlacedFeature> placedFeatures) {
        if (CUSTOM_PLACED_FEATURE_IDS.isEmpty()) {
            return List.of();
        }
        if (placedFeatures == CACHED_REGISTRY) {
            return CACHED_HOLDERS;
        }

        final List<Holder<PlacedFeature>> resolved = new ArrayList<>(CUSTOM_PLACED_FEATURE_IDS.size());
        for (ResourceLocation id : CUSTOM_PLACED_FEATURE_IDS) {
            final ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, id);
            final Optional<Holder.Reference<PlacedFeature>> holder = placedFeatures.getHolder(key);
            if (holder.isPresent()) {
                resolved.add(holder.get());
            } else {
                LOGGER.warn("Custom placed feature {} is not present in the placed_feature registry.", id);
            }
        }

        CACHED_REGISTRY = placedFeatures;
        CACHED_HOLDERS = List.copyOf(resolved);
        return CACHED_HOLDERS;
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
