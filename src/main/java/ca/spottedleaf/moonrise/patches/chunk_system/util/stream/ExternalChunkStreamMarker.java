package ca.spottedleaf.moonrise.patches.chunk_system.util.stream;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;

/**
 * Used to mark chunk data streams that are on external files
 */
public class ExternalChunkStreamMarker extends DataInputStream {

    private static final Field IN_FIELD;
    static {
        Field field;
        try {
            field = FilterInputStream.class.getDeclaredField("in");
            field.setAccessible(true);
        } catch (final Throwable throwable) {
            field = null;
        }

        IN_FIELD = field;
    }

    private static InputStream getWrapped(final FilterInputStream in) {
        try {
            return (InputStream)IN_FIELD.get(in);
        } catch (final Throwable throwable) {
            return in;
        }
    }

    public ExternalChunkStreamMarker(final DataInputStream in) {
        super(getWrapped(in));
    }
}
