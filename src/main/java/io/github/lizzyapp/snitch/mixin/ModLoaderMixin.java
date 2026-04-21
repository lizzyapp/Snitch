package io.github.lizzyapp.snitch.mixin;

import io.github.lizzyapp.snitch.Snitch;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModLoadingIssue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(ModLoader.class)
public class ModLoaderMixin {

    @Inject(
        method = "getLoadingIssues",
        at = @At("RETURN"),
        cancellable = true
    )
    private static void snitch$getLoadingIssues(
        CallbackInfoReturnable<List<ModLoadingIssue>> cir
    ) {
        List<ModLoadingIssue> original = new ArrayList<>(cir.getReturnValue());
        Snitch.getSnitchRecords().forEach(
            (snitchRecord) -> {
                original.add(
                    new ModLoadingIssue(
                        ModLoadingIssue.Severity.WARNING,
                        "snitch.modloadingissue.has_dependency",
                        List.of(snitchRecord.trippedMod(), snitchRecord.contains())
                    )
                );
            }
        );
        cir.setReturnValue(original);
    }
}
