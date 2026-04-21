package io.github.lizzyapp.snitch.mixin;

import io.github.lizzyapp.snitch.Snitch;
import net.neoforged.neoforge.client.loading.ClientModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientModLoader.class)
public class ClientModLoaderMixin {
    @Inject(method = "completeModLoading", at = @At("HEAD"))
    private static void snitch$completeModLoading(Runnable initialScreensTask, CallbackInfoReturnable<Runnable> cir) {
        Snitch.compileSnitchedMods();
    }
}
