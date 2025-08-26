package com.thingmagic;

public abstract class DefaultPooledObjectFactory<T> implements PooledObjectFactory<T> {
    @Override
    public void activateObject(final T o) {
    }

    @Override
    public void passivateObject(final T o) {
    }
}
