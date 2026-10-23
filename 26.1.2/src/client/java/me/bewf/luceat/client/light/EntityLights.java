package me.bewf.luceat.client.light;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

public final class EntityLights {
	private static final double MERGE_DISTANCE_SQUARED = 2.25;

	private EntityLights() {
	}

	public static List<LightSource> collect(Minecraft minecraft, Vec3 camera, float partialTick) {
		ClientLevel level = minecraft.level;
		List<LightSource> lights = new ArrayList<>();
		if (level == null) {
			return lights;
		}
		LuceatConfig config = LuceatConfig.get();
		double range = config.lightRange;
		double rangeSquared = range * range;
		List<LightSource> dropped = null;
		List<Integer> counts = null;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity.distanceToSqr(camera) > rangeSquared) {
				continue;
			}
			if (entity instanceof LivingEntity living) {
				HeldLights.collect(level, living, partialTick, lights);
			}
			if (config.naturalLights && config.entityLights) {
				addNatural(lights, level, entity, partialTick);
			}
			if (!config.entityLights) {
				continue;
			}
			if (entity instanceof ItemEntity item) {
				if (config.droppedItems) {
					if (dropped == null) {
						dropped = new ArrayList<>();
						counts = new ArrayList<>();
					}
					addDropped(dropped, counts, item, partialTick);
				}
			} else if (entity instanceof ItemFrame frame && config.itemFrames) {
				addFrame(lights, frame);
			}
		}
		if (dropped != null) {
			merge(dropped, counts, lights);
		}
		int limit = Math.max(4, Math.min(config.maxLights, LightBuffer.MAX_LIGHTS) / 2);
		if (lights.size() > limit) {
			lights.sort(Comparator.comparingDouble(light -> distanceSquared(light, camera)));
			return new ArrayList<>(lights.subList(0, limit));
		}
		return lights;
	}

	public static int brighten(Entity entity, int lightCoords) {
		if (entity instanceof LivingEntity living) {
			return HeldLights.brighten(living, lightCoords);
		}
		if (entity instanceof ItemEntity item && LuceatConfig.get().droppedItems) {
			return HeldLights.brighten(LightSpecs.of(item.getItem(), item.isInWater()).level(), lightCoords);
		}
		return lightCoords;
	}

	private static void addNatural(List<LightSource> out, ClientLevel level, Entity entity, float partialTick) {
		LightSpec spec = NaturalLights.of(entity);
		if (spec == null) {
			return;
		}
		Vec3 position = entity.getPosition(partialTick);
		Vec3 centre = new Vec3(position.x, position.y + entity.getBbHeight() * 0.5, position.z);
		Vec3 placed = LightPlacement.settle(level, entity, LightSmoother.key(3, entity.getId(), 0), entity.getEyePosition(partialTick), centre);
		out.add(new LightSource(placed.x, placed.y, placed.z, spec.red(), spec.green(), spec.blue(), spec.level(), false, spec.fire(), LightSmoother.key(3, entity.getId(), 0)));
	}

	private static void addFrame(List<LightSource> out, ItemFrame frame) {
		LightSpec spec = LightSpecs.of(frame.getItem(), false);
		if (spec.level() <= 0) {
			return;
		}
		Direction facing = frame.getDirection();
		Vec3 centre = frame.getBoundingBox().getCenter();
		out.add(new LightSource(
			centre.x + facing.getStepX() * 0.4,
			centre.y + facing.getStepY() * 0.4,
			centre.z + facing.getStepZ() * 0.4,
			spec.red(), spec.green(), spec.blue(), spec.level(), false, spec.fire(), LightSmoother.key(4, frame.getId(), 0)
		));
	}

	private static void addDropped(List<LightSource> out, List<Integer> counts, ItemEntity entity, float partialTick) {
		LightSpec spec = LightSpecs.of(entity.getItem(), entity.isInWater());
		if (spec.level() > 0) {
			counts.add(Math.max(1, entity.getItem().getCount()));
			Vec3 position = entity.getPosition(partialTick);
			out.add(new LightSource(position.x, position.y + 0.25, position.z, spec.red(), spec.green(), spec.blue(), spec.level(), false, spec.fire(), LightSmoother.key(2, entity.getId(), 0)));
		}
	}

	private static void merge(List<LightSource> dropped, List<Integer> counts, List<LightSource> out) {
		boolean[] used = new boolean[dropped.size()];
		for (int i = 0; i < dropped.size(); i++) {
			if (used[i]) {
				continue;
			}
			LightSource first = dropped.get(i);
			double x = first.x() * first.radius();
			double y = first.y() * first.radius();
			double z = first.z() * first.radius();
			float red = first.red() * first.radius();
			float green = first.green() * first.radius();
			float blue = first.blue() * first.radius();
			float weight = first.radius();
			float strongest = first.radius();
			int members = counts.get(i);
			for (int j = i + 1; j < dropped.size(); j++) {
				LightSource other = dropped.get(j);
				if (used[j]) {
					continue;
				}
				double dx = other.x() - first.x();
				double dy = other.y() - first.y();
				double dz = other.z() - first.z();
				if (dx * dx + dy * dy + dz * dz > MERGE_DISTANCE_SQUARED) {
					continue;
				}
				used[j] = true;
				x += other.x() * other.radius();
				y += other.y() * other.radius();
				z += other.z() * other.radius();
				red += other.red() * other.radius();
				green += other.green() * other.radius();
				blue += other.blue() * other.radius();
				weight += other.radius();
				strongest = Math.max(strongest, other.radius());
				members += counts.get(j);
			}
			float radius = strongest + (float) Math.min(3.0, Math.log(members) / Math.log(2.0));
			out.add(new LightSource(x / weight, y / weight, z / weight, red / weight, green / weight, blue / weight, radius, false, first.fire(), first.id()));
		}
	}

	private static double distanceSquared(LightSource light, Vec3 camera) {
		double x = light.x() - camera.x;
		double y = light.y() - camera.y;
		double z = light.z() - camera.z;
		return x * x + y * y + z * z;
	}
}
