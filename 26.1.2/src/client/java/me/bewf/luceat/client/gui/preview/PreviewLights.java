package me.bewf.luceat.client.gui.preview;

import java.util.ArrayList;
import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;
import me.bewf.luceat.client.gui.preview.Light.Source;

final class PreviewLights {
	private static final float[] DEFAULT_COLOUR = {1.0F, 0.7F, 0.35F};

	private PreviewLights() {
	}

	// seconds drives the flicker, lx ly lz move the first light when not NaN
	static List<Light> build(Scene scene, LuceatConfig config, double seconds, double lx, double ly, double lz) {
		List<Light> result = new ArrayList<>();
		if (!config.enabled) {
			return result;
		}
		double maxDistance = scene.rangeScale > 0.0F ? config.lightRange * scene.rangeScale : Double.MAX_VALUE;
		int index = 0;
		for (Light light : scene.lights) {
			Light l = light;
			if (index == 0 && scene.movable) {
				if (!Double.isNaN(lx)) {
					l = l.at(lx, Double.isNaN(ly) ? l.y() : ly, lz);
				}
				if (scene.keepAway) {
					double cx = Math.floor(l.x()) + 0.5;
					double x = config.keepLightOutOfWalls ? Math.max(cx - 0.2, Math.min(cx + 0.2, l.x())) : Math.max(cx - 0.47, Math.min(cx + 0.47, l.x()));
					l = l.at(x, l.y(), l.z());
				}
			}
			index++;
			if (!allowed(l, config)) {
				continue;
			}
			if (l.source() == Source.HELD && scene.id.equals("held")) {
				l = l.at(l.x(), 1.0 + 1.8 * config.heldHeight - 0.8, l.z());
			}
			if (l.textured() && !config.textureColours) {
				l = l.withColour(DEFAULT_COLOUR[0], DEFAULT_COLOUR[1], DEFAULT_COLOUR[2]);
			}
			if (l.fire() && config.flicker > 0.0F) {
				double seed = (index * 37.31) % 100.0;
				double noise = 0.55 * Math.sin(seconds * 9.0 + seed) + 0.3 * Math.sin(seconds * 17.3 + seed * 1.7) + 0.15 * Math.sin(seconds * 31.0 + seed * 2.3);
				l = l.withRadius((float) Math.max(0.0, l.radius() + config.flicker * 1.3 * noise));
			}
			double dx = l.x() - scene.eye[0];
			double dy = l.y() - scene.eye[1];
			double dz = l.z() - scene.eye[2];
			if (Math.sqrt(dx * dx + dy * dy + dz * dz) > maxDistance) {
				continue;
			}
			result.add(l);
		}
		if (scene.capLights && result.size() > config.maxLights) {
			double[] eye = scene.eye;
			result.sort((a, b) -> Double.compare(distance(a, eye), distance(b, eye)));
			return new ArrayList<>(result.subList(0, Math.max(0, config.maxLights)));
		}
		return result;
	}

	private static double distance(Light light, double[] eye) {
		double dx = light.x() - eye[0];
		double dy = light.y() - eye[1];
		double dz = light.z() - eye[2];
		return dx * dx + dy * dy + dz * dz;
	}

	private static boolean allowed(Light light, LuceatConfig config) {
		if (light.wet() && config.waterPutsOutLights) {
			return false;
		}
		return switch (light.source()) {
			case HELD -> config.heldLights;
			case PLAYER -> config.otherPlayers;
			case MOB -> config.mobs;
			case DROPPED -> config.droppedItems;
			case FRAME -> config.itemFrames;
			case NATURAL -> config.naturalLights;
			case ENCHANT -> config.enchantGlow;
			case PLACED -> config.placedLights && light.radius() >= config.placedMinLevel;
			case ALWAYS -> true;
		};
	}
}
