package io.github.lizzyapp.snitch;

import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.ConfigValue<List<String>> SNITCH_ON = BUILDER
        .comment("Mods for the system to flag and detect")
        .define("unwantedDependencies", List.of("fabric_api"));

    public static final ModConfigSpec.ConfigValue<Boolean> DISPLAY_ALL = BUILDER
        .comment("If the mod should print all embedded dependencies in the console")
        .define("printAllDependencies", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}