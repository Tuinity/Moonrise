package ca.spottedleaf.moonrise.patches.fast_palette;

public interface FastPaletteData<T> {

    public T[] moonrise$getPalette();

    public void moonrise$setPalette(final T[] palette);

}
