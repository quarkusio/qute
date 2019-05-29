package com.github.mkouba.qute;

import java.util.concurrent.CompletableFuture;

class Futures {

    static <T> CompletableFuture<T> failure(Throwable t) {
        CompletableFuture<T> failure = new CompletableFuture<>();
        failure.completeExceptionally(t);
        return failure;
    }
}
