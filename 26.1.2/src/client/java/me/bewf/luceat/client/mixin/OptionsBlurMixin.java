package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.gui.glass.LuceatBlur;

import net.minecraft.client.Options;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Options.class)
public abstract class OptionsBlurMixin {
	@Inject(method = "getMenuBackgroundBlurriness", at = @At("RETURN"), cancellable = true, require = 0)
	private void luceat$blur(CallbackInfoReturnable<Integer> result) {
		int base = result.getReturnValueI();
		int wide = LuceatBlur.radius(base);
		if (wide != base) {
			result.setReturnValue(wide);
		}
	}
}
