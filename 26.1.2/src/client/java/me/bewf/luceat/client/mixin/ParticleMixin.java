package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.LightSampler;

import net.minecraft.client.particle.Particle;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Particle.class)
public abstract class ParticleMixin {
	@Shadow
	protected double x;
	@Shadow
	protected double y;
	@Shadow
	protected double z;

	@Inject(method = {"getLightCoords", "getLightColor"}, at = @At("RETURN"), cancellable = true, require = 0)
	private void luceat$lightParticles(float partialTick, CallbackInfoReturnable<Integer> cir) {
		try {
			cir.setReturnValue(LightSampler.particle(this.x, this.y, this.z, cir.getReturnValue()));
		} catch (RuntimeException ignored) {
		}
	}
}
