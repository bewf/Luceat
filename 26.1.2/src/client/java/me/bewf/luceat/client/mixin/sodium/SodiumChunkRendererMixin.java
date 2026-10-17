package me.bewf.luceat.client.mixin.sodium;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import me.bewf.luceat.client.light.LightEngine;

import net.caffeinemc.mods.sodium.client.gl.shader.GlProgram;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.shader.ChunkShaderInterface;

@Mixin(ShaderChunkRenderer.class)
public abstract class SodiumChunkRendererMixin {
	@Shadow
	protected GlProgram<ChunkShaderInterface> activeProgram;

	@Inject(method = "begin", at = @At("TAIL"))
	private void luceat$bindLights(CallbackInfo ci) {
		LightEngine.upload(this.activeProgram.handle());
	}
}
