package me.bewf.luceat.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import me.bewf.luceat.client.light.HeldLights;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
	@ModifyVariable(method = "renderHandsWithItems", at = @At("HEAD"), argsOnly = true)
	private int luceat$brightenHands(int lightCoords) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? lightCoords : HeldLights.brighten(player, lightCoords);
	}

	@Inject(method = "renderArmWithItem", at = @At("HEAD"), require = 0)
	private void luceat$liftHeldItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float attack, ItemStack stack, float equip, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
		poseStack.pushPose();
		poseStack.translate(0.0F, HeldLights.itemLift(player, stack), 0.0F);
	}

	@Inject(method = "renderArmWithItem", at = @At("RETURN"), require = 0)
	private void luceat$dropHeldItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand, float attack, ItemStack stack, float equip, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CallbackInfo info) {
		poseStack.popPose();
	}
}
