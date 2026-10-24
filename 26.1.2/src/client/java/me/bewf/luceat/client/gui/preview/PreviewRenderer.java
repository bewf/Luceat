package me.bewf.luceat.client.gui.preview;

import java.util.List;

import me.bewf.luceat.client.config.LuceatConfig;

// draws a scene to plain pixels with no gui classes, used for preset cards
public final class PreviewRenderer {
	public static final float NIGHT = 0.04F;
	public static final double LOOP_SECONDS = 3.0;

	private PreviewRenderer() {
	}

	// everything that needs the game thread, done once per config
	public static final class Prepared {
		private final Scene scene;
		private final LuceatConfig config;
		private final Occ[] occ;

		private Prepared(Scene scene, LuceatConfig config) {
			this.scene = scene;
			this.config = config;
			PreviewOccluders occluders = new PreviewOccluders(config);
			this.occ = new Occ[scene.cells.length];
			for (int i = 0; i < occ.length; i++) {
				occ[i] = scene.cells[i] == null ? Occ.EMPTY : occluders.of(scene.cells[i]);
			}
			scene.voxels();
			scene.shine();
		}

		// safe on any thread, time is in seconds and loops every LOOP_SECONDS
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
			float shadowDistance = scene.shadowScale == 1.0F ? config.shadowDistance : config.shadowDistance * scene.shadowScale;
			Params params = new Params(config.shadows, config.brightness, config.maxBrightness, config.daylightFade, config.directionalShading, config.shadowStrength, config.vanillaReplace, config.placedStrength, config.shadowSoftness * 0.45F, NIGHT, scene.vanilla, shadowDistance, config.lightBounce, config.shine);
			Tracer tracer = new Tracer(scene.width, scene.height, scene.depth, scene.voxels(), occ, scene.shine(), flickered, params, eye, scene.target, scene.fov);
			tracer.render(out, width, height);
		}
	}

	public static Prepared prepare(SceneId id, LuceatConfig config) {
		return new Prepared(id.scene(), config);
	}

	public static void render(SceneId id, LuceatConfig config, int width, int height, float time, int[] out) {
		prepare(id, config).render(time, width, height, out);
	}
}
