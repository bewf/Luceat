package me.bewf.luceat.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;

import me.bewf.luceat.client.sky.LuceatClouds;

import net.minecraft.client.renderer.CloudRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
	@ModifyVariable(method = "render", at = @At("STORE"), ordinal = 0, require = 0)
	private RenderPipeline luceat$pipeline(RenderPipeline pipeline) {
		return LuceatClouds.choose(pipeline);
	}

	@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V"), index = 0, require = 0)
	private RenderPipeline luceat$setPipeline(RenderPipeline pipeline) {
		return LuceatClouds.choose(pipeline);
	}
}
