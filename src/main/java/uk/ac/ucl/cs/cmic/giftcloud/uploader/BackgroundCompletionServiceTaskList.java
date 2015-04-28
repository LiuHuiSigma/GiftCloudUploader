package uk.ac.ucl.cs.cmic.giftcloud.uploader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

class BackgroundCompletionServiceTaskList<T> extends BackgroundServiceTaskList<Callable<T>, Future<T>> {
    private final CompletionService<T> completionService;
    private final Map<Future<T>, BackgroundServiceTaskWrapper<Callable<T>, Future<T>>> uploaderResultMap = new HashMap<Future<T>, BackgroundServiceTaskWrapper<Callable<T>, Future<T>>>();
    private final ExecutorService executor;

    BackgroundCompletionServiceTaskList(final int numThreads) {
        executor = Executors.newFixedThreadPool(numThreads);
        completionService = new ExecutorCompletionService<T>(executor);
    }

    @Override
    public final void add(final Callable<T> callable, final BackgroundServiceErrorRecord errorRecord) {
        final Future<T> future = completionService.submit(callable);
        uploaderResultMap.put(future, new BackgroundServiceTaskWrapper<Callable<T>, Future<T>>(callable, future, errorRecord));
    }

    @Override
    public final BackgroundServiceTaskWrapper<Callable<T>, Future<T>> take() throws InterruptedException {
        final Future<T> future = completionService.take();
        return uploaderResultMap.remove(future);
    }

    @Override
    protected final boolean isEmpty() {
        return uploaderResultMap.isEmpty();
    }

}
