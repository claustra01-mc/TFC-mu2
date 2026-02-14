package net.claustra01.tfcmu2.worldgen;

import com.mojang.serialization.MapCodec;

import net.claustra01.tfcmu2.Tfcmu2Mod;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class Tfcmu2Worldgen {
    public static final DeferredRegister<MapCodec<? extends BiomeModifier>> BIOME_MODIFIER_SERIALIZERS =
        DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, Tfcmu2Mod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<Tfcmu2OreVeinBiomeModifier>> ORE_VEINS =
        BIOME_MODIFIER_SERIALIZERS.register("ore_veins", () -> MapCodec.unit(Tfcmu2OreVeinBiomeModifier.INSTANCE));

    private Tfcmu2Worldgen() {
    }
}

