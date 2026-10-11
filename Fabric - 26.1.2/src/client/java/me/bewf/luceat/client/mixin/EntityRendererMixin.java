package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.HeldLights;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
	private void luceat$brightenPlayers(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
		if (entity instanceof Player player) {
			cir.setReturnValue(HeldLights.brighten(player, cir.getReturnValue()));
		}
	}
}
