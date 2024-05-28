package ca.spottedleaf.moonrise.patches.fast_palette;

public interface FastPalette<T> {

    public default T[] moonrise$getRawPalette(final FastPaletteData<T> src) {
        return null;
    }

}
