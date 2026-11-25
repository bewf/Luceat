package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.shadow.EntityShadows;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityShadowCircleMixin {
	@Inject(method = "extractShadow", at = @At("RETURN"), require = 0)
	private void luceat$dropVanillaCircle(EntityRenderState state, Minecraft minecraft, Level level, CallbackInfo ci) {
		if (EntityShadows.replacesVanilla()) {
			state.shadowPieces.clear();
			state.shadowRadius = 0.0F;
		}
	}
}
