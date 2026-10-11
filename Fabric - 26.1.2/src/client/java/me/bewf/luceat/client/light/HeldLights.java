package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class HeldLights {
	private static final float TORCH_RADIUS = 14.0F;

	private HeldLights() {
	}

	public static List<LightSource> collect(LocalPlayer player, float partialTick) {
		List<LightSource> lights = new ArrayList<>(2);
		Vec3 eye = player.getEyePosition(partialTick);
		add(lights, player.getMainHandItem(), eye);
		add(lights, player.getOffhandItem(), eye);
		return lights;
	}

	private static void add(List<LightSource> lights, ItemStack stack, Vec3 eye) {
		if (stack.isEmpty()) {
			return;
		}
		Item item = stack.getItem();
		if (item == Items.TORCH) {
			lights.add(new LightSource(eye.x, eye.y - 0.3, eye.z, 1.0F, 0.62F, 0.28F, TORCH_RADIUS));
		} else if (item == Items.SOUL_TORCH) {
			lights.add(new LightSource(eye.x, eye.y - 0.3, eye.z, 0.3F, 0.75F, 1.0F, TORCH_RADIUS));
		}
	}
}
