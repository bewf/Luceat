package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.light.PlacedLights;
import me.bewf.luceat.client.shadow.ShadowVolume;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
	@Inject(method = "setBlocksDirty", at = @At("HEAD"))
	private void luceat$markShadowsDirty(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
		ShadowVolume.markDirty(pos);
		PlacedLights.markDirty(pos);
	}
}
