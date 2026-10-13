package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class EntityLights {
	private static final double RANGE_SQUARED = 64.0 * 64.0;

	private EntityLights() {
	}

	public static List<LightSource> collect(Minecraft minecraft, Vec3 camera, float partialTick) {
		ClientLevel level = minecraft.level;
		List<LightSource> lights = new ArrayList<>();
		if (level == null) {
			return lights;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.distanceToSqr(camera) > RANGE_SQUARED) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				HeldLights.collect(living, partialTick, HeldLights.isFirstPerson(living), lights);
			} else if (entity instanceof ItemEntity item) {
				addDropped(lights, item, partialTick);
			}
		}
		if (lights.size() > LightBuffer.MAX_LIGHTS) {
			lights.sort(Comparator.comparingDouble(light -> distanceSquared(light, camera)));
			return new ArrayList<>(lights.subList(0, LightBuffer.MAX_LIGHTS));
		}
		return lights;
	}

	public static int brighten(Entity entity, int lightCoords) {
		if (entity instanceof LivingEntity living) {
			return HeldLights.brighten(living, lightCoords);
		}
		if (entity instanceof ItemEntity item) {
			return HeldLights.brighten(LightSpecs.of(item.getItem(), item.isInWater()).level(), lightCoords);
		}
		return lightCoords;
	}

	private static void addDropped(List<LightSource> out, ItemEntity entity, float partialTick) {
		LightSpec spec = LightSpecs.of(entity.getItem(), entity.isInWater());
		if (spec.level() > 0) {
			Vec3 position = entity.getPosition(partialTick);
			out.add(new LightSource(position.x, position.y + 0.25, position.z, spec.red(), spec.green(), spec.blue(), spec.level()));
		}
	}

	private static double distanceSquared(LightSource light, Vec3 camera) {
		double x = light.x() - camera.x;
		double y = light.y() - camera.y;
		double z = light.z() - camera.z;
		return x * x + y * y + z * z;
	}
}
