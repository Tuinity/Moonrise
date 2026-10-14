package ca.spottedleaf.moonrise.patches.nbt_rw;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import java.io.IOException;
import java.io.InputStream;

public final class CopyingInputStream extends InputStream {

    public final InputStream reference;
    public final Buffer copy;

    public CopyingInputStream(final InputStream reference) {
        this.reference = reference;
        this.copy = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 4096L, (long)Integer.MAX_VALUE);
    }

    @Override
    public int read() throws IOException {
        final int ret = this.reference.read();
        if (ret < 0) {
            return ret;
        }
        this.copy.writeByte((byte)ret);
        return ret;
    }

    @Override
    public int read(final byte[] b) throws IOException {
        return this.read(b, 0, b.length);
    }

    @Override
    public int read(final byte[] b, final int off, final int len) throws IOException {
        final int ret = this.reference.read(b, off, len);
        if (ret < 0) {
            return ret;
        }
        this.copy.writeBytes(b, off, ret);
        return ret;
    }

    @Override
    public int available() throws IOException {
        return this.reference.available();
    }

    @Override
    public void close() throws IOException {
        this.reference.close();
    }
}
