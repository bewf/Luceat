package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.GlowCarrier;

import net.minecraft.client.renderer.item.ItemStackRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements GlowCarrier {
	@Unique
	private int luceat$glowTag;

	@Override
	public int luceat$glow() {
		return luceat$glowTag;
	}

	@Override
	public void luceat$setGlow(int tag) {
		luceat$glowTag = tag;
	}

	@ModifyVariable(method = "submit", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
	private int luceat$tagLight(int lightCoords) {
		return lightCoords | luceat$glowTag;
	}
}
