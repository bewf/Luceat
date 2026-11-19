package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.gui.SmoothText;

import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.GlyphRenderTypes;
import net.minecraft.client.renderer.texture.AbstractTexture;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Supplier;

@Mixin(FontTexture.class)
public abstract class FontTextureMixin extends AbstractTexture {
	@Inject(method = "<init>", at = @At("RETURN"), require = 0)
	private void luceat$smooth(Supplier<String> label, GlyphRenderTypes renderTypes, boolean colored, CallbackInfo info) {
		if (label.get().startsWith("luceat:")) {
			SmoothText.register(getTextureView());
		}
	}
}
