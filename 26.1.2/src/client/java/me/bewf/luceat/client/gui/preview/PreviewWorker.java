package me.bewf.luceat.client.gui.preview;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

import me.bewf.luceat.client.config.LuceatConfig;

final class PreviewWorker {
	record Request(Scene scene, LuceatConfig config, Occ[] occ, List<Light> lights, double[] eye, double[] target, double fov, float time, double seconds, int traceWidth, int traceHeight, int width, int height, int radius, int fade, int key, int tier) {
	}

	record Result(Request request, int[] pixels, Tracer tracer) {
	}

	private volatile ForkJoinPool pool;
	private final Object lock = new Object();
	private Request waiting;
	private boolean running;
	private volatile int generation;
	private volatile Result ready;

	PreviewWorker() {
		this.pool = newPool();
	}

	private static ForkJoinPool newPool() {
		int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 2);
		return new ForkJoinPool(threads, task -> {
			java.util.concurrent.ForkJoinWorkerThread thread = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(task);
			thread.setName("luceat preview");
			thread.setPriority(Thread.NORM_PRIORITY - 1);
			thread.setDaemon(true);
			return thread;
		}, null, false);
	}

	void submit(Request request) {
		synchronized (lock) {
			if (pool.isShutdown()) {
				pool = newPool();
			}
			waiting = request;
			generation++;
			if (!running) {
				running = true;
				Thread thread = new Thread(this::drain, "luceat preview queue");
				thread.setDaemon(true);
				thread.start();
			}
		}
	}

	Result take() {
		Result result = ready;
		ready = null;
		return result;
	}

	void cancel() {
		synchronized (lock) {
			waiting = null;
			generation++;
		}
		ready = null;
	}

	void shutdown() {
		cancel();
		pool.shutdown();
	}

	private void drain() {
		while (true) {
			Request request;
			int mine;
			synchronized (lock) {
				request = waiting;
				waiting = null;
				mine = generation;
				if (request == null) {
					running = false;
					return;
				}
			}
			try {
				Result result = pool.submit(() -> render(request, mine)).get();
				if (result != null && mine == generation) {
					ready = result;
				}
			} catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				synchronized (lock) {
					running = false;
				}
				return;
			} catch (ExecutionException | RuntimeException error) {
				me.bewf.luceat.Luceat.LOGGER.warn("Luceat preview render failed", error);
			}
		}
	}

	private Result render(Request request, int mine) {
		Tracer tracer = PreviewRenderer.tracer(request.scene(), request.config(), request.occ(), request.lights(), request.eye(), request.target(), request.fov(), request.time(), request.seconds());
		int tw = request.traceWidth();
		int th = request.traceHeight();
		int[] traced = new int[tw * th];
		tracer.render(traced, tw, th, () -> mine != generation);
		if (mine != generation) {
			return null;
		}
		int ow = request.width();
		int oh = request.height();
		int[] pixels = tw == ow && th == oh ? traced : upscale(traced, tw, th, ow, oh);
		float pixelsPerUnit = request.radius() / 12.0F;
		PreviewMask.rim(pixels, ow, oh, traced, tw, th, Math.round(26.0F * pixelsPerUnit), 6.0F, pixelsPerUnit);
		PreviewMask.apply(pixels, ow, oh, request.radius(), request.fade(), false);
		return new Result(request, pixels, tracer);
	}

	private static int[] upscale(int[] src, int sw, int sh, int dw, int dh) {
		int[] dst = new int[dw * dh];
		int[] column = new int[dw];
		int[] next = new int[dw];
		float[] weight = new float[dw];
		for (int x = 0; x < dw; x++) {
			float fx = Math.max(0.0F, Math.min(sw - 1.0F, (x + 0.5F) * sw / dw - 0.5F));
			int x0 = (int) fx;
			column[x] = x0;
			next[x] = Math.min(sw - 1, x0 + 1);
			weight[x] = fx - x0;
		}
		IntStream.range(0, dh).parallel().forEach(y -> {
			float fy = Math.max(0.0F, Math.min(sh - 1.0F, (y + 0.5F) * sh / dh - 0.5F));
			int y0 = (int) fy;
			int y1 = Math.min(sh - 1, y0 + 1);
			float wy = fy - y0;
			for (int x = 0; x < dw; x++) {
				float wx = weight[x];
				int a = src[column[x] + y0 * sw];
				int b = src[next[x] + y0 * sw];
				int c = src[column[x] + y1 * sw];
				int d = src[next[x] + y1 * sw];
				int out = 0xFF000000;
				for (int shift = 0; shift <= 16; shift += 8) {
					float top = ((a >> shift) & 255) * (1.0F - wx) + ((b >> shift) & 255) * wx;
					float bottom = ((c >> shift) & 255) * (1.0F - wx) + ((d >> shift) & 255) * wx;
					out |= Math.round(top * (1.0F - wy) + bottom * wy) << shift;
				}
				dst[x + y * dw] = out;
			}
		});
		return dst;
	}
}
