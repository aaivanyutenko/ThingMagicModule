package com.thingmagic;

public interface PooledObjectFactory<T> {

    /**
     * Create an instance that can be served by the pool an object.
     */
    T makeObject();

    /**
     * Reinitialize an instance to be returned by the pool.
     */
    void activateObject(T o);

    /**
     * Uninitialize an instance to be returned to the idle object pool.
     */
    void passivateObject(T o);
}
