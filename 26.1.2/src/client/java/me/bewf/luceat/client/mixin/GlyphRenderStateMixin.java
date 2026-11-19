package me.bewf.luceat.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;

import me.bewf.luceat.client.gui.SmoothText;

import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlyphRenderState.class)
public abstract class GlyphRenderStateMixin {
	@Inject(method = "textureSetup", at = @At("HEAD"), cancellable = true, require = 0)
	private void luceat$linear(CallbackInfoReturnable<TextureSetup> info) {
		GpuTextureView view = ((GlyphRenderState) (Object) this).renderable().textureView();
		if (SmoothText.has(view)) {
			info.setReturnValue(TextureSetup.singleTextureWithLightmap(view, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
		}
	}
}
