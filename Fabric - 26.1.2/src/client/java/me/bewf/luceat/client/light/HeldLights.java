package me.bewf.luceat.client.light;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HeldLights {
	private HeldLights() {
	}

	public static void collect(LivingEntity entity, float partialTick, boolean firstPerson, List<LightSource> out) {
		boolean submerged = entity.isUnderWater();
		boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
		add(out, entity.getMainHandItem(), submerged, entity, partialTick, mainRight, firstPerson);
		add(out, entity.getOffhandItem(), submerged, entity, partialTick, !mainRight, firstPerson);
	}

	public static int brighten(LivingEntity entity, int lightCoords) {
		boolean submerged = entity.isUnderWater();
		int level = Math.max(LightSpecs.of(entity.getMainHandItem(), submerged).level(), LightSpecs.of(entity.getOffhandItem(), submerged).level());
		return brighten(level, lightCoords);
	}

	public static int brighten(int level, int lightCoords) {
		if (level <= LightCoordsUtil.block(lightCoords)) {
			return lightCoords;
		}
		return LightCoordsUtil.withBlock(lightCoords, level);
	}

	public static boolean isFirstPerson(LivingEntity entity) {
		Minecraft minecraft = Minecraft.getInstance();
		return entity == minecraft.player && minecraft.options.getCameraType().isFirstPerson();
	}

	private static void add(List<LightSource> out, ItemStack stack, boolean submerged, LivingEntity entity, float partialTick, boolean rightSide, boolean firstPerson) {
		LightSpec spec = LightSpecs.of(stack, submerged);
		if (spec.level() > 0) {
			Vec3 hand = handPosition(entity, partialTick, rightSide, firstPerson);
			out.add(new LightSource(hand.x, hand.y, hand.z, spec.red(), spec.green(), spec.blue(), spec.level()));
		}
	}

	private static Vec3 handPosition(LivingEntity entity, float partialTick, boolean rightSide, boolean firstPerson) {
		double side = rightSide ? 1.0 : -1.0;
		if (firstPerson) {
			Vec3 forward = entity.getViewVector(partialTick);
			Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
			right = right.lengthSqr() < 1.0E-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
			Vec3 up = right.cross(forward);
			return entity.getEyePosition(partialTick).add(forward.scale(0.45)).add(right.scale(0.32 * side)).add(up.scale(-0.28));
		}
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * (float) (Math.PI / 180.0);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double height = entity.getBbHeight() * (entity.isCrouching() ? 0.45 : 0.53);
		Vec3 base = entity.getPosition(partialTick);
		return new Vec3(
			base.x + forwardX * 0.35 - forwardZ * 0.4 * side,
			base.y + height,
			base.z + forwardZ * 0.35 + forwardX * 0.4 * side
		);
	}
}
