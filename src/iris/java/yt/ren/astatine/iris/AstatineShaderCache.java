package yt.ren.astatine.iris;

import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import yt.ren.astatine.backend.AstatineDevice;
import yt.ren.astatine.backend.BackendNatives;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public class AstatineShaderCache implements AutoCloseable {
	private static final Pattern DISCARD = Pattern.compile("\\bdiscard\\b");
	private final Set<CompletableFuture<?>> pending = ConcurrentHashMap.newKeySet();
	private final ExecutorService executor;
	private final boolean demote;
	private boolean closing;
	private volatile boolean closed;

	public AstatineShaderCache() {
		int processors = Runtime.getRuntime().availableProcessors();
		int threads = Math.clamp(Math.min(8, (processors + 1) / 2), 1, processors);
		this.executor = Executors.newFixedThreadPool(threads, Thread.ofPlatform().daemon().name("Iris shader compiler-", 0).factory());
		this.demote = AstatineDevice.get().supportsDemote();
	}

	public static <T> T await(CompletableFuture<T> future) {
		try {
			return future.join();
		} catch (CompletionException failure) {
			if (failure.getCause() instanceof RuntimeException cause) {
				throw cause;
			}

			if (failure.getCause() instanceof Error cause) {
				throw cause;
			}

			throw failure;
		}
	}

	private static String demote(String source) {
		if (!DISCARD.matcher(source).find()) {
			return source;
		}

		int version = source.indexOf("#version");
		int line = version < 0 ? 0 : source.indexOf('\n', version) + 1;

		if (version >= 0 && line == 0) {
			source = source + "\n";
			line = source.length();
		}

		String body = DISCARD.matcher(source.substring(line)).replaceAll("demote");
		return source.substring(0, line) + "#extension GL_EXT_demote_to_helper_invocation : require\n" + body;
	}

	public long compile(String name, PatchShaderType stage, String source) {
		if (this.closed) {
			throw new IllegalStateException("shader cache is closed");
		}

		if (stage == PatchShaderType.FRAGMENT && this.demote) {
			source = demote(source);
		}

		return BackendNatives.shaderCompile(stage.ordinal(), name, source);
	}

	public <T> CompletableFuture<T> submit(Supplier<T> task) {
		this.requireOpen();
		return this.track(CompletableFuture.supplyAsync(task, this.executor));
	}

	public <T, R> CompletableFuture<R> thenApply(CompletableFuture<T> future, Function<T, R> task) {
		this.requireOpen();
		return this.track(future.thenApplyAsync(task, this.executor));
	}

	private <T> CompletableFuture<T> track(CompletableFuture<T> future) {
		this.pending.add(future);
		future.whenComplete((result, failure) -> {
			if (failure == null) {
				this.pending.remove(future);
			}
		});
		return future;
	}

	public void finishCompilation() {
		var futures = this.pending.toArray(CompletableFuture[]::new);

		try {
			await(CompletableFuture.allOf(futures));
		} finally {
			for (var future : futures) {
				this.pending.remove(future);
			}
		}
	}

	private void requireOpen() {
		if (this.closing || this.closed) {
			throw new IllegalStateException("shader cache is closed");
		}
	}

	@Override
	public void close() {
		if (this.closing || this.closed) {
			return;
		}

		this.closing = true;

		try {
			this.finishCompilation();
		} catch (RuntimeException | Error ignored) {
		} finally {
			this.executor.close();
		}

		this.closed = true;
	}
}
