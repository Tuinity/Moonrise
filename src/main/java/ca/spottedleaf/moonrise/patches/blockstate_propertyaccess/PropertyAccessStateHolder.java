package ca.spottedleaf.moonrise.patches.blockstate_propertyaccess;

import java.util.Collection;

public interface PropertyAccessStateHolder<O, S> {

    public long moonrise$getTableIndex();

    public void moonrise$init(final Collection<S> states);

}
