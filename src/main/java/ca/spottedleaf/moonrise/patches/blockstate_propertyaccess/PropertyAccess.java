package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess;

public interface PropertyAccess<T> {

    public int moonrise$getId();

    public int moonrise$getIdFor(final T value);

    public T moonrise$getById(final int id);

    public void moonrise$setById(final T[] values);
}
