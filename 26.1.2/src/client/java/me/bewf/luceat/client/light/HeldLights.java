package me.bewf.luceat.client.light;

import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class HeldLights {
	private HeldLights() {
	}

	public static boolean allowed(LivingEntity entity) {
		LuceatConfig config = LuceatConfig.get();
		if (entity == Minecraft.getInstance().player) {
			return config.heldLights;
		}
		return entity instanceof Player ? config.otherPlayers : config.mobs;
	}

	public static void collect(ClientLevel level, LivingEntity entity, float partialTick, List<LightSource> out) {
		if (!allowed(entity)) {
			return;
		}
		boolean submerged = entity.isUnderWater();
		boolean mainRight = entity.getMainArm() == HumanoidArm.RIGHT;
		add(out, level, entity.getMainHandItem(), submerged, entity, partialTick, mainRight);
		add(out, level, entity.getOffhandItem(), submerged, entity, partialTick, !mainRight);
	}

	public static int brighten(LivingEntity entity, int lightCoords) {
		if (!allowed(entity)) {
			return lightCoords;
		}
		boolean submerged = entity.isUnderWater();
		int level = Math.max(LightSpecs.of(entity.getMainHandItem(), submerged).level(), LightSpecs.of(entity.getOffhandItem(), submerged).level());
		return brighten(level, lightCoords);
	}

	public static int brighten(int level, int lightCoords) {
		if (!LuceatConfig.get().enabled || level <= LightCoordsUtil.block(lightCoords)) {
			return lightCoords;
		}
		return LightCoordsUtil.withBlock(lightCoords, level);
	}

	private static void add(List<LightSource> out, ClientLevel level, ItemStack stack, boolean submerged, LivingEntity entity, float partialTick, boolean rightSide) {
		LightSpec spec = LightSpecs.of(stack, submerged);
		if (spec.level() > 0) {
			Vec3 hand = LightPlacement.outOfWalls(level, entity, entity.getEyePosition(partialTick), handPosition(entity, partialTick, rightSide));
			out.add(new LightSource(hand.x, hand.y, hand.z, spec.red(), spec.green(), spec.blue(), spec.level()));
		}
	}

	private static Vec3 handPosition(LivingEntity entity, float partialTick, boolean rightSide) {
		double side = rightSide ? 1.0 : -1.0;
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * (float) (Math.PI / 180.0);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double height = entity.getBbHeight() * (entity.isCrouching() ? 0.5 : 0.58);
		Vec3 base = entity.getPosition(partialTick);
		return new Vec3(
			base.x + forwardX * 0.35 - forwardZ * 0.4 * side,
			base.y + height,
			base.z + forwardZ * 0.35 + forwardX * 0.4 * side
		);
	}
}
