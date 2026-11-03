package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.extras.Sparkle;

import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "at.hannibal2.skyhanni.features.misc.compacttablist.AdvancedPlayerList")
public abstract class SkyHanniTabMixin {
	@Inject(method = "createCustomName", at = @At("RETURN"), cancellable = true, remap = false, require = 0)
	private void luceat$names(@Coerce Object playerData, CallbackInfoReturnable<Component> cir) {
		Component original = cir.getReturnValue();
		Component restored = Sparkle.restoreAfterSkyHanni(original);
		if (restored != original) {
			cir.setReturnValue(restored);
		}
	}
}
