package org.apache.meecrowave.johnzon;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.johnzon.core.BufferStrategy;
import org.apache.johnzon.core.BufferStrategyFactory;

public class DebugJohnzonBufferStrategy implements BufferStrategy {

    private static AtomicInteger counter = new AtomicInteger(0);
    private BufferStrategy delegate;

    public DebugJohnzonBufferStrategy() {
        counter.incrementAndGet();
        delegate = BufferStrategyFactory.valueOf("BY_INSTANCE");
    }


    public static int getCounter() {
        return counter.get();
    }

    public static void resetCounter() {
        counter.set(0);
    }

    @Override
    public BufferProvider<char[]> newCharProvider(int size) {
        return delegate.newCharProvider(size);
    }
}
