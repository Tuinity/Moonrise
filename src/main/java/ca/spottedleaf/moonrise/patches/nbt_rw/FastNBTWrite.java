package ca.spottedleaf.moonrise.patches.nbt_rw;

import ca.spottedleaf.ioutil.stream.AbstractBufferOutputStream;
import java.io.IOException;

public interface FastNBTWrite {

    public void moonrise$writeWithId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException;

    public void moonrise$writeWithoutId(final AbstractBufferOutputStream out, final boolean stringFallback) throws IOException;

    public void moonrise$writeWithIdAndName(final AbstractBufferOutputStream out, final String name, final boolean stringFallback) throws IOException;

}
