package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.HeldLights;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@ModifyVariable(method = "renderHandsWithItems", at = @At("HEAD"), argsOnly = true)
	private int luceat$brightenHands(int lightCoords) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? lightCoords : HeldLights.brighten(player, lightCoords);
	}
}
