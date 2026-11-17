package me.bewf.luceat.client.mixin;

import com.mojang.blaze3d.shaders.ShaderType;

import me.bewf.luceat.client.shader.EntityShaderPatch;

import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.resources.Identifier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ShaderManager.class)
public abstract class ShaderManagerMixin {
	@Inject(method = "getShader", at = @At("RETURN"), cancellable = true, require = 0)
	private void luceat$patchEntity(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
		cir.setReturnValue(EntityShaderPatch.apply(id, cir.getReturnValue()));
	}
}
