package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.GlowCarrier;
import me.bewf.luceat.client.light.ItemGlow;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
	@Inject(method = "updateForLiving", at = @At("RETURN"), require = 0)
	private void luceat$glowLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, LivingEntity entity, CallbackInfo info) {
		((GlowCarrier) state).luceat$setGlow(ItemGlow.tag(stack, context));
	}

	@Inject(method = "updateForNonLiving", at = @At("RETURN"), require = 0)
	private void luceat$glowNonLiving(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Entity entity, CallbackInfo info) {
		((GlowCarrier) state).luceat$setGlow(ItemGlow.tag(stack, context, entity));
	}

	@Inject(method = "updateForTopItem", at = @At("RETURN"), require = 0)
	private void luceat$glowTop(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level, ItemOwner owner, int seed, CallbackInfo info) {
		((GlowCarrier) state).luceat$setGlow(ItemGlow.tag(stack, context));
	}
}
