package ca.spottedleaf.moonrise.patches.collisions.util;

import org.jetbrains.annotations.NotNull;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;
import java.util.function.ToLongFunction;
import java.util.stream.Collector;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

public final class EmptyStreamForMoveCall<T> implements Stream<T> {

    public static final EmptyStreamForMoveCall INSTANCE = new EmptyStreamForMoveCall();

    @Override
    public boolean noneMatch(Predicate<? super T> predicate) {
        return false; // important: ret false so the branch is never taken by mojang code
    }

    @Override
    public Stream<T> filter(Predicate<? super T> predicate) {
        return null;
    }

    @Override
    public <R> Stream<R> map(Function<? super T, ? extends R> mapper) {
        return null;
    }

    @Override
    public IntStream mapToInt(ToIntFunction<? super T> mapper) {
        return null;
    }

    @Override
    public LongStream mapToLong(ToLongFunction<? super T> mapper) {
        return null;
    }

    @Override
    public DoubleStream mapToDouble(ToDoubleFunction<? super T> mapper) {
        return null;
    }

    @Override
    public <R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper) {
        return null;
    }

    @Override
    public IntStream flatMapToInt(Function<? super T, ? extends IntStream> mapper) {
        return null;
    }

    @Override
    public LongStream flatMapToLong(Function<? super T, ? extends LongStream> mapper) {
        return null;
    }

    @Override
    public DoubleStream flatMapToDouble(Function<? super T, ? extends DoubleStream> mapper) {
        return null;
    }

    @Override
    public Stream<T> distinct() {
        return null;
    }

    @Override
    public Stream<T> sorted() {
        return null;
    }

    @Override
    public Stream<T> sorted(Comparator<? super T> comparator) {
        return null;
    }

    @Override
    public Stream<T> peek(Consumer<? super T> action) {
        return null;
    }

    @Override
    public Stream<T> limit(long maxSize) {
        return null;
    }

    @Override
    public Stream<T> skip(long n) {
        return null;
    }

    @Override
    public void forEach(Consumer<? super T> action) {

    }

    @Override
    public void forEachOrdered(Consumer<? super T> action) {

    }

    @NotNull
    @Override
    public Object[] toArray() {
        return new Object[0];
    }

    @NotNull
    @Override
    public <A> A[] toArray(IntFunction<A[]> generator) {
        return null;
    }

    @Override
    public T reduce(T identity, BinaryOperator<T> accumulator) {
        return null;
    }

    @NotNull
    @Override
    public Optional<T> reduce(BinaryOperator<T> accumulator) {
        return Optional.empty();
    }

    @Override
    public <U> U reduce(U identity, BiFunction<U, ? super T, U> accumulator, BinaryOperator<U> combiner) {
        return null;
    }

    @Override
    public <R> R collect(Supplier<R> supplier, BiConsumer<R, ? super T> accumulator, BiConsumer<R, R> combiner) {
        return null;
    }

    @Override
    public <R, A> R collect(Collector<? super T, A, R> collector) {
        return null;
    }

    @NotNull
    @Override
    public Optional<T> min(Comparator<? super T> comparator) {
        return Optional.empty();
    }

    @NotNull
    @Override
    public Optional<T> max(Comparator<? super T> comparator) {
        return Optional.empty();
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public boolean anyMatch(Predicate<? super T> predicate) {
        return false;
    }

    @Override
    public boolean allMatch(Predicate<? super T> predicate) {
        return false;
    }

    @NotNull
    @Override
    public Optional<T> findFirst() {
        return Optional.empty();
    }

    @NotNull
    @Override
    public Optional<T> findAny() {
        return Optional.empty();
    }


    @NotNull
    @Override
    public Iterator<T> iterator() {
        return null;
    }

    @NotNull
    @Override
    public Spliterator<T> spliterator() {
        return null;
    }

    @Override
    public boolean isParallel() {
        return false;
    }

    @NotNull
    @Override
    public Stream<T> sequential() {
        return null;
    }

    @NotNull
    @Override
    public Stream<T> parallel() {
        return null;
    }

    @NotNull
    @Override
    public Stream<T> unordered() {
        return null;
    }

    @NotNull
    @Override
    public Stream<T> onClose(Runnable closeHandler) {
        return null;
    }

    @Override
    public void close() {

    }
}
