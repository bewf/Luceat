package me.bewf.luceat.client.mixin;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;

import me.bewf.luceat.client.shader.BloomPass;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererBloomMixin {
	@Shadow
	@Final
	private CrossFrameResourcePool resourcePool;

	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"), require = 0)
	private void luceat$bloom(CallbackInfo info) {
		BloomPass.run(Minecraft.getInstance(), this.resourcePool);
	}
}
