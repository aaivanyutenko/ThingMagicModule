package com.thingmagic;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

public class SingleThreadPooledObject<T> {
    /**
     * Factory which created object instances.
     */
    private final PooledObjectFactory<T> factory;
    /**
     * Current thread.
     */
    private final Thread startedInThread;

    /**
     * Queue of objects which can be used.
     */
    private final Queue<T> idleObjects = new LinkedList<>();
    /**
     * The count of the currently created objects.
     */
    private final AtomicLong createCount = new AtomicLong(0);


    /**
     * Default constructor.
     */
    public SingleThreadPooledObject(final PooledObjectFactory<T> factory) {
        this.factory = factory;
        this.startedInThread = Thread.currentThread();
    }

    /**
     * @return object from the pool or create new one.
     */
    public T borrowObject() {
        checkThread();

        T o = idleObjects.poll();
        if (o == null) {
            o = factory.makeObject();
            createCount.incrementAndGet();
        }

        factory.activateObject(o);

        return o;
    }

    /**
     * Return object to the pool or add new one.
     */
    public void returnObject(final T o) {
        checkThread();

        if (o != null) {
            factory.passivateObject(o);
            idleObjects.add(o);
        }
    }


    /**
     * If thread is different than start thread - throw an exception.
     */
    private void checkThread() {
//        if (startedInThread != Thread.currentThread()) {
//            throw new IllegalStateException("You can use pool only in one thread. startedInThread = " + startedInThread.getName() + ", Thread.currentThread() = " + Thread.currentThread());
//        }
    }

}
