package ca.spottedleaf.moonrise.patches.fast_palette;

import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.chunk.Palette;
import net.minecraft.world.level.chunk.PaletteResize;
import java.util.ArrayList;
import java.util.function.Predicate;

// use for comparing palette performance
public final class DualPalette<T> implements Palette<T> {

    public final Palette<T> first;
    public Palette<T> second;
    public final Factory secondFactory;
    public final int bits;

    public DualPalette(final Palette<T> first, final Palette<T> second, final Factory secondFactory, final int bits) {
        this.first = first;
        this.second = second;
        this.secondFactory = secondFactory;
        this.bits = bits;
    }

    private static final PaletteResize NO_OP = (a, b) -> {
        return a;
    };

    @Override
    public int idFor(final T t, final PaletteResize<T> paletteResize) {
        final int second = this.second.idFor(t, NO_OP);
        final int first = this.first.idFor(t, paletteResize);
        return first;
    }

    @Override
    public T valueFor(final int i) {
        final T first = this.first.valueFor(i);
        final T second = this.second.valueFor(i);

        return first;
    }

    @Override
    public boolean maybeHas(final Predicate<T> predicate) {
        return this.first.maybeHas(predicate);
    }

    @Override
    public void read(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> idMap) {
        this.first.read(friendlyByteBuf, idMap);
        this.second = this.secondFactory.create(this.bits, new ArrayList<>());
        for (int i = 0; i < this.first.getSize(); ++i) {
            this.second.idFor(this.first.valueFor(i), PaletteResize.noResizeExpected());
        }
    }

    @Override
    public void write(final FriendlyByteBuf friendlyByteBuf, final IdMap<T> idMap) {
        this.first.write(friendlyByteBuf, idMap);
    }

    @Override
    public int getSerializedSize(final IdMap<T> idMap) {
        return this.first.getSerializedSize(idMap);
    }

    @Override
    public int getSize() {
        return this.first.getSize();
    }

    @Override
    public DualPalette<T> copy() {
        return new DualPalette<>(this.first.copy(), this.second.copy(), this.secondFactory, this.bits);
    }
}
