package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.extras.Sparkle;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {
	@Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true, require = 0)
	private void luceat$names(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
		cir.setReturnValue(Sparkle.replaceNames(cir.getReturnValue(), true, false));
	}
}
