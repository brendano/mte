package util;
import com.google.common.collect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class ThreadUtil {
	
	public static interface MinibatchProcedure {
		public void apply(int start, int end);
	}
	public static interface MinibatchFunction<T>  {
		public T apply(int start, int end);
	}
	/** Divides up integer tasks 0..(N-1) into hunks, each sized minibatchSize,
	 * and assigns a task that calls the MinibatchFunction on them, giving them the
	 * relevant [start, end) span specification for their hunk.  Wait for results.
	 */
	public static <T> List<T>
	processMinibatches(int numTotal, int minibatchSize, final MinibatchFunction<T> f) {
		List<Callable<T>> tasks = Lists.newArrayList();
		for (final Integer start : Arr.rangeInts(0, numTotal, minibatchSize)) {
			final Integer end = Math.min(start+minibatchSize, numTotal);
			tasks.add(new Callable<T>() {
				@Override
				public T call() throws Exception {
					return f.apply(start, end);
				}
			});
		}
		return runAndWaitForTasks(tasks);
	}
	
	/** same as processMinibatches above but no return value from each subtask */
	public static <T> void 
	processMinibatches(int numTotal, int minibatchSize, final MinibatchProcedure f) {
		processMinibatches(numTotal, minibatchSize, new MinibatchFunction<Object>() {
			@Override
			public Object apply(int start, int end) {
				f.apply(start, end);
				return null;
			}
		});
	}
	
	public static <T> List<T> runAndWaitForTasks(List<Callable<T>> tasks) {
		List<T> results = Lists.newArrayList();
		try {
			for (Future<T> result : threadPool.invokeAll(tasks)) {
				results.add(result.get());
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
			System.exit(-1);
		}
		return results;
	}

	public static ExecutorService threadPool = null;
	
	public static void createPool(int numThreads) {
		if (threadPool != null) threadPool.shutdownNow();
		threadPool = Executors.newFixedThreadPool(numThreads);
	}

}
