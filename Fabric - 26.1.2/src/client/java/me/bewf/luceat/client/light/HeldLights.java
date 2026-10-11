package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HeldLights {
	private HeldLights() {
	}

	public static List<LightSource> collect(LocalPlayer player, float partialTick) {
		List<LightSource> lights = new ArrayList<>(2);
		boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
		boolean mainRight = player.getMainArm() == HumanoidArm.RIGHT;
		add(lights, player.getMainHandItem(), handPosition(player, partialTick, mainRight, firstPerson));
		add(lights, player.getOffhandItem(), handPosition(player, partialTick, !mainRight, firstPerson));
		return lights;
	}

	public static int brighten(Player player, int lightCoords) {
		int level = Math.max(LightSpecs.of(player.getMainHandItem()).level(), LightSpecs.of(player.getOffhandItem()).level());
		if (level <= LightCoordsUtil.block(lightCoords)) {
			return lightCoords;
		}
		return LightCoordsUtil.withBlock(lightCoords, level);
	}

	private static void add(List<LightSource> lights, ItemStack stack, Vec3 hand) {
		LightSpec spec = LightSpecs.of(stack);
		if (spec.level() > 0) {
			lights.add(new LightSource(hand.x, hand.y, hand.z, spec.red(), spec.green(), spec.blue(), spec.level()));
		}
	}

	private static Vec3 handPosition(Player player, float partialTick, boolean rightSide, boolean firstPerson) {
		double side = rightSide ? 1.0 : -1.0;
		if (firstPerson) {
			Vec3 forward = player.getViewVector(partialTick);
			Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
			right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			Vec3 up = right.cross(forward);
			return player.getEyePosition(partialTick).add(forward.scale(0.45)).add(right.scale(0.32 * side)).add(up.scale(-0.28));
		}
		float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot) * (float) (Math.PI / 180.0);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double height = player.isCrouching() ? 0.8 : 0.95;
		Vec3 base = player.getPosition(partialTick);
		return new Vec3(
			base.x + forwardX * 0.35 - forwardZ * 0.4 * side,
			base.y + height,
			base.z + forwardZ * 0.35 + forwardX * 0.4 * side
		);
	}
}
