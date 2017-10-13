package co.ledger.wallet.daemon.async;

import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NamedThreadPoolExecutor extends ThreadPoolExecutor {

    public NamedThreadPoolExecutor(final int corePoolSize,
                                   final int maximumPoolSize,
                                   final long keepAliveTime,
                                   final TimeUnit unit,
                                   final String namePrefix) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new LinkedBlockingDeque<>(),
                new ThreadFactory() {

                    @Override
                    public Thread newThread(Runnable r) {
                        final String threadName = String.format(THREAD_NAME_PATTERN, namePrefix, counter.incrementAndGet());
                        return new Thread(r, threadName);
                    }

                    private final AtomicInteger counter = new AtomicInteger();
                });
    }

    private static final String THREAD_NAME_PATTERN = "%s-%d";
}
