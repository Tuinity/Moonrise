package ca.spottedleaf.moonrise.patches.nbt_rw;

import ca.spottedleaf.ioutil.buffer.Buffer;
import ca.spottedleaf.ioutil.buffer.MemoryAllocator;
import ca.spottedleaf.ioutil.stream.wrapped.SimpleBufferOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public final class CopyingOutputStream extends OutputStream {

    public final OutputStream reference;
    public final Buffer copy;
    public final SimpleBufferOutputStream copyStream;

    public CopyingOutputStream(final OutputStream reference) {
        this.reference = reference;
        this.copy = new Buffer(false, "", MemoryAllocator.UnPooledHeap.INSTANCE, 4096L, (long)Integer.MAX_VALUE);
        this.copyStream = new SimpleBufferOutputStream(this.copy);
    }

    @Override
    public void write(final int b) throws IOException {
        this.reference.write(b);
        this.copyStream.write(b);
    }

    @Override
    public void write(final byte[] b) throws IOException {
        this.write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
       this.reference.write(b, off, len);
       this.copyStream.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        try {
            this.copyStream.flush();
        } finally {
            this.reference.flush();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.copyStream.close();
        } finally {
            this.reference.close();
        }
    }
}
