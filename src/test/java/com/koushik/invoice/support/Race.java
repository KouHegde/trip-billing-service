package com.koushik.invoice.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Starts every task at the same instant (behind a latch) to make overlapping requests as likely as possible. */
public final class Race {

	private Race() {
	}

	@SafeVarargs
	public static <T> List<T> run(Callable<T>... tasks) throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		try (ExecutorService pool = Executors.newFixedThreadPool(tasks.length)) {
			for (Callable<T> task : tasks) {
				futures.add(pool.submit(() -> {
					start.await();
					return task.call();
				}));
			}
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		}
	}

}
