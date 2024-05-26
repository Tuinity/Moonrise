package ca.spottedleaf.moonrise.common.real_dumb_shit;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HolderCompletableFuture<T> extends CompletableFuture<T> {

    public final List<Runnable> toExecute = new ArrayList<>();

}
