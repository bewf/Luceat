package me.bewf.luceat.client.mixin;

import me.bewf.luceat.client.extras.Sparkle;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@ModifyVariable(
			method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
			at = @At("HEAD"),
			argsOnly = true,
			require = 0
	)
	private Component luceat$names(Component message) {
		return Sparkle.decorateChat(message);
	}
}
