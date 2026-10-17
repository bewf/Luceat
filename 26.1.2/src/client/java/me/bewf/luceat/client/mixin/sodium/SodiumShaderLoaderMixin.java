package me.bewf.luceat.client.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import me.bewf.luceat.client.shader.TerrainShaderPatch;

import net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader;
import net.minecraft.resources.Identifier;

@Mixin(ShaderLoader.class)
public class SodiumShaderLoaderMixin {
	@Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
	private static void luceat$patchSource(Identifier name, CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(TerrainShaderPatch.apply(name, cir.getReturnValue()));
	}
}
