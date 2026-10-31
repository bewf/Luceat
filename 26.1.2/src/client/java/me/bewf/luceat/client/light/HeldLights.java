package me.bewf.luceat.client.light;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.light.block.BlockRule;
import me.bewf.luceat.client.light.block.BlockRules;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class HeldLights {
	private static final double MARGIN = 0.4;

	private static final class Smooth {
		float height;
		float reach = 1.0F;
		long nanos;
	}

	private static final Map<Long, Smooth> SMOOTH = new HashMap<>();

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
		if (!LuceatConfig.get().enabled || me.bewf.luceat.client.compat.IrisCompat.standDown() || level <= LightCoordsUtil.block(lightCoords)) {
			return lightCoords;
		}
		return LightCoordsUtil.withBlock(lightCoords, level);
	}

	private static void add(List<LightSource> out, ClientLevel level, ItemStack stack, boolean submerged, LivingEntity entity, float partialTick, boolean rightSide) {
		LightSpec spec = LightSpecs.of(stack, submerged);
		if (spec.level() > 0) {
			BlockRule rule = BlockRules.forItem(stack.getItem());
			Vec3 hand = handPosition(level, entity, partialTick, rightSide, rule != null ? rule.heldHeight : -1.0F);
			out.add(new LightSource(hand.x, hand.y, hand.z, spec.red(), spec.green(), spec.blue(), spec.level(), false, spec.fire(), LightSmoother.key(1, entity.getId(), rightSide ? 1 : 0), spec.noShadow()));
		}
	}

	private static Vec3 handPosition(ClientLevel level, LivingEntity entity, float partialTick, boolean rightSide, float heightOverride) {
		LuceatConfig config = LuceatConfig.get();
		double side = rightSide ? 1.0 : -1.0;
		float yaw = Mth.rotLerp(partialTick, entity.yBodyRotO, entity.yBodyRot) * (float) (Math.PI / 180.0);
		double forwardX = -Mth.sin(yaw);
		double forwardZ = Mth.cos(yaw);
		double dx = forwardX * 0.35 - forwardZ * 0.4 * side;
		double dz = forwardZ * 0.35 + forwardX * 0.4 * side;
		double reach = Math.sqrt(dx * dx + dz * dz);
		Vec3 base = entity.getPosition(partialTick);

		long now = System.nanoTime();
		long key = LightSmoother.key(1, entity.getId(), rightSide ? 1 : 0);
		float height = heightOverride >= 0.0F ? heightOverride : config.heldHeight;
		float wantHeight = entity.getBbHeight() * (entity.isCrouching() ? height * 0.86F : height);
		Smooth smooth = SMOOTH.get(key);
		if (smooth == null) {
			smooth = new Smooth();
			smooth.height = wantHeight;
			smooth.nanos = now;
			SMOOTH.put(key, smooth);
			if (SMOOTH.size() > 128) {
				prune(now);
			}
		}
		float dt = Math.min(0.1F, Math.max(0.0F, (now - smooth.nanos) / 1.0E9F));
		smooth.nanos = now;
		smooth.height += (wantHeight - smooth.height) * (1.0F - (float) Math.exp(-dt / 0.1F));

		Vec3 axis = new Vec3(base.x, base.y + smooth.height, base.z);
		float wantReach = 1.0F;
		if (level != null && config.keepLightOutOfWalls && reach > 1.0E-4) {
			Vec3 toHand = new Vec3(dx, 0.0, dz);
			Vec3 end = axis.add(toHand).add(toHand.scale(MARGIN / reach));
			BlockHitResult hit = level.clip(new ClipContext(axis, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
			if (hit.getType() != HitResult.Type.MISS) {
				double distance = hit.getLocation().distanceTo(axis);
				wantReach = (float) Math.max(0.0, Math.min(1.0, (distance - MARGIN) / reach));
			}
		}
		float tau = wantReach < smooth.reach ? 0.03F : 0.15F;
		smooth.reach += (wantReach - smooth.reach) * (1.0F - (float) Math.exp(-dt / tau));
		Vec3 hand = axis.add(dx * smooth.reach, 0.0, dz * smooth.reach);
		return LightPlacement.settle(level, entity, key, axis, hand);
	}

	private static void prune(long now) {
		Iterator<Smooth> iterator = SMOOTH.values().iterator();
		while (iterator.hasNext()) {
			if (now - iterator.next().nanos > 5_000_000_000L) {
				iterator.remove();
			}
		}
	}
}
