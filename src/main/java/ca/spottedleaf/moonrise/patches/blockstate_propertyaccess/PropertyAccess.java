package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess;

public interface PropertyAccess<T> {

    public int getId();

    public int getIdFor(final T value);

}
