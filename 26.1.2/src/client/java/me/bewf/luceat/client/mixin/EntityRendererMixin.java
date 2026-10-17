package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.EntityLights;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
	@Inject(method = "getPackedLightCoords", at = @At("RETURN"), cancellable = true)
	private void luceat$brightenPlayers(Entity entity, float partialTick, CallbackInfoReturnable<Integer> cir) {
		cir.setReturnValue(EntityLights.brighten(entity, cir.getReturnValue()));
	}
}
