package net.claustra01.tfcmu2.worldgen;

import com.mojang.serialization.MapCodec;

import net.claustra01.tfcmu2.Tfcmu2Mod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Tfcmu2Worldgen {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Tfcmu2Mod.MOD_ID);

    public static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES =
        DeferredRegister.create(Registries.CONFIGURED_FEATURE, Tfcmu2Mod.MOD_ID);

    public static final DeferredRegister<PlacedFeature> PLACED_FEATURES =
        DeferredRegister.create(Registries.PLACED_FEATURE, Tfcmu2Mod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<Tfcmu2OreVeinBiomeModifier>> ORE_VEINS =
        BIOME_MODIFIER_SERIALIZERS.register("ore_veins", () -> MapCodec.unit(Tfcmu2OreVeinBiomeModifier.INSTANCE));

    private static final AtomicBoolean BOOTSTRAPPED = new AtomicBoolean(false);

    public static void bootstrap() {
        if (!BOOTSTRAPPED.compareAndSet(false, true)) {
            return;
        }
        Tfcmu2CustomVeins.bootstrap();
    }

    private Tfcmu2Worldgen() {
    }
}
