package me.bewf.luceat.client.gui.preview;

import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;

public final class PreviewRenderer {
	public static final float NIGHT = 0.04F;
	public static final double LOOP_SECONDS = 3.0;

	private PreviewRenderer() {
	}

	static Occ[] occluders(Scene scene, PreviewOccluders occluders) {
		Occ[] occ = new Occ[scene.cells.length];
		for (int i = 0; i < occ.length; i++) {
			occ[i] = scene.cells[i] == null ? Occ.EMPTY : occluders.of(scene.cells[i]);
		}
		return occ;
	}

	static double[] lookTarget(Scene scene, double yawDegrees, double pitchDegrees) {
		double[] eye = scene.eye;
		double dx = scene.target[0] - eye[0];
		double dy = scene.target[1] - eye[1];
		double dz = scene.target[2] - eye[2];
		double yaw = Math.toRadians(yawDegrees);
		double rx = dx * Math.cos(yaw) + dz * Math.sin(yaw);
		double rz = -dx * Math.sin(yaw) + dz * Math.cos(yaw);
		double horizontal = Math.max(1.0E-6, Math.hypot(rx, rz));
		double length = Math.hypot(horizontal, dy);
		double pitch = Math.atan2(dy, horizontal) + Math.toRadians(pitchDegrees);
		double scale = length * Math.cos(pitch) / horizontal;
		return new double[]{eye[0] + rx * scale, eye[1] + length * Math.sin(pitch), eye[2] + rz * scale};
	}

	static double zoomFov(double fov, double zoom) {
		return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(fov) / 2.0) / zoom));
	}

	static void prepare(Scene scene) {
		scene.voxels();
		scene.shine();
		scene.kind();
		scene.emit();
		scene.water();
		scene.sky();
		scene.blockLight();
	}

	static Tracer tracer(Scene scene, LuceatConfig config, Occ[] occ, List<Light> lights, double[] eye, double[] target, double fov, float time, double seconds) {
		float shadowDistance = scene.shadowScale == 1.0F ? config.shadowDistance : config.shadowDistance * scene.shadowScale;
		Params params = new Params(config.shadows, config.brightness, config.maxBrightness, config.daylightFade, config.directionalShading, config.shadowStrength, config.vanillaReplace, config.placedStrength, config.shadowSoftness * 0.45F, time, scene.vanilla, shadowDistance, config.lightBounce, config.shine);
		Light[] array = lights.toArray(new Light[0]);
		Tracer tracer = new Tracer(scene.width, scene.height, scene.depth, scene.voxels(), occ, scene.shine(), array, params, eye, target, fov);
		Extras extras = new Extras();
		extras.sky = scene.sky();
		extras.emit = scene.emit();
		extras.water = scene.water();
		extras.blockLight = scene.blockLight();
		extras.fog = scene.fog;
		extras.sunYaw = scene.sunYaw;
		extras.sunShadows = config.sunShadows;
		extras.sunStrength = config.sunShadowStrength;
		extras.reflectivity = scene.reflectivity;
		extras.caustics = config.caustics && scene.hasWater();
		extras.clock = seconds;
		if (config.lightSpread && config.enabled) {
			extras.spread = Extras.lightField(scene.width, scene.height, scene.depth, scene.kind(), array, 1.0F);
			extras.spill = config.spillStrength;
		}
		tracer.extras(extras);
		return tracer;
	}

	public static final class Prepared {
		private final Scene scene;
		private final LuceatConfig config;
		private final Occ[] occ;

		private Prepared(Scene scene, LuceatConfig config) {
			this.scene = scene;
			this.config = config;
			this.occ = occluders(scene, new PreviewOccluders(config));
			PreviewRenderer.prepare(scene);
		}

		public void render(float time, int width, int height, int[] out) {
			double phase = time / LOOP_SECONDS * Math.PI * 2.0;
			double seconds = time;
			List<Light> lights = PreviewLights.build(scene, config, 0.0, Double.NaN, Double.NaN, Double.NaN);
			Light[] flickered = new Light[lights.size()];
			for (int i = 0; i < flickered.length; i++) {
				Light l = lights.get(i);
				if (l.fire() && config.flicker > 0.0F) {
					double seed = (i * 37.31 + 11.0) % 100.0;
					double base = Math.PI * 2.0 / LOOP_SECONDS;
					double noise = 0.55 * Math.sin(seconds * base * 4.0 + seed) + 0.3 * Math.sin(seconds * base * 8.0 + seed * 1.7) + 0.15 * Math.sin(seconds * base * 15.0 + seed * 2.3);
					l = l.withRadius((float) Math.max(0.0, l.radius() + config.flicker * 1.3 * noise));
				}
				if (i == 0 && scene.movable) {
					l = l.at(l.x() + 0.9 * Math.sin(phase), l.y(), l.z() + 0.35 * Math.cos(phase));
				}
				flickered[i] = l;
			}
			double[] eye = {scene.eye[0] + 0.8 * Math.sin(phase), scene.eye[1], scene.eye[2]};
			tracer(scene, config, occ, List.of(flickered), eye, scene.target, scene.fov, NIGHT, seconds).render(out, width, height);
		}
	}

	public static Prepared prepare(SceneId id, LuceatConfig config) {
		return new Prepared(id.scene(), config);
	}

	public static void render(SceneId id, LuceatConfig config, int width, int height, float time, int[] out) {
		prepare(id, config).render(time, width, height, out);
	}
}
