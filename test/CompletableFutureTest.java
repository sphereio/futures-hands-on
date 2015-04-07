import org.junit.Test;
import play.libs.F;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.fest.assertions.Assertions.assertThat;

public class CompletableFutureTest {
    @Test
    public void functionGetWithRuntimeException() throws Exception {
        final Function<String, Integer> f = s -> {
            throw new IllegalArgumentException("whatever");
        };
        executionThrows(() -> CompletableFuture.completedFuture("hello").thenApply(f).get(),
                ExecutionException.class, IllegalArgumentException.class, "whatever");
    }

    @Test
    public void functionJoinWithRuntimeException() throws Exception {
        final Function<String, Integer> f = s -> {
            throw new IllegalArgumentException("whatever");
        };
        executionThrows(() -> CompletableFuture.completedFuture("hello").thenApply(f).join(),
                CompletionException.class, IllegalArgumentException.class, "whatever");
    }

    @Test
    public void functionGetWithCheckedException() throws Exception {
        final Function<String, Integer> f = s -> {
            throw new CompletionException(new IOException("whatever"));
        };
        executionThrows(() -> CompletableFuture.completedFuture("hello").thenApply(f).get(),
                ExecutionException.class, IOException.class, "whatever");
    }

    @Test
    public void functionOnCompleteWithCheckedException() throws Exception {
        final Function<String, Integer> f = s -> {
            throw new CompletionException(new IOException("whatever"));
        };
        final CompletableFuture<Integer> firstFuture = CompletableFuture.completedFuture("hello").thenApply(f);
        final CompletableFuture<Integer> followUp = new CompletableFuture<>();
        firstFuture.whenComplete((value, e) -> {
            if (value != null) {
                followUp.complete(value);
            } else {
                //if you check here the class of the exception e, it will be CompletionException

                followUp.completeExceptionally(e);
            }
        });
        executionThrows(() -> followUp.get(), ExecutionException.class, IOException.class, "whatever");
    }

    @Test
    public void functionJoinWithCheckedException() throws Exception {
        final Function<String, Integer> f = s -> {
            throw new CompletionException(new IOException("whatever!"));
        };
        executionThrows(() -> CompletableFuture.completedFuture("hello").thenApply(f).join(),
                CompletionException.class, IOException.class, "whatever!");
    }

    @Test
    public void promise() throws Exception {
        final F.Promise<String> promise = F.Promise.throwing(new IOException("whatever!"));
        executionThrows(() -> promise.get(0), IOException.class, "whatever!");
    }

    @Test
    public void promise2() throws Exception {
        //F.Function (not java.util.function.Function) declares throws Throwable!!
        final F.Function<String, Integer> f = s -> {
            throw new IOException("whatever");
        };
        final F.Promise<Integer> promise = F.Promise.pure("hi").map(f);
        executionThrows(() -> promise.get(0), IOException.class, "whatever");//easier handling
    }

    @Test
    public void customTimeout() throws Exception {
        //cache and don't forget to close
        final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        final CompletionStage<String> stage = new CompletableFuture<>();
        final CompletionStage<String> timeoutStage = withTimeout(stage, executor);
        executionThrows(() -> timeoutStage.toCompletableFuture().get(), ExecutionException.class, TimeoutException.class, null);
        executor.shutdown();
    }

    private <T> CompletionStage<T> withTimeout(final CompletionStage<T> stage, final ScheduledThreadPoolExecutor executor) {
        //could be parameter of this method
        final int delay = 2;
        final TimeUnit timeUnit = TimeUnit.MILLISECONDS;

        final CompletableFuture<T> future = new CompletableFuture<>();
        stage.whenComplete((value, e) -> {
            if (value != null) {
                future.complete(value);
            } else {
                future.completeExceptionally(e);
            }
        });
        executor.schedule(() -> future.completeExceptionally(new TimeoutException()), delay, timeUnit);

        return future;
    }

    private <T> void executionThrows(final SupplierWithThrows<T> supplier,
                                     final Class<? extends Exception> expectedExceptionClass,
                                     final Class<? extends Exception> expectedExceptionCauseClass,
                                     final String expectedMessage) {
        extractException(supplier, e -> {
            assertThat(e).isInstanceOf(expectedExceptionClass);
            assertThat(e.getCause()).isInstanceOf(expectedExceptionCauseClass);
            assertThat(e.getCause().getMessage()).isEqualTo(expectedMessage);
        });
    }

    private <T> void extractException(final SupplierWithThrows<T> supplier, final Consumer<Exception> consumer) {
        try {
            supplier.get();
            throw new RuntimeException("should fail");
        } catch (final Exception e) {
            consumer.accept(e);
        }
    }

    private <T> void executionThrows(final SupplierWithThrows<T> supplier,
                                     final Class<? extends Exception> expectedExceptionClass,
                                     final String expectedMessage) {
        extractException(supplier, e -> {
            assertThat(e).isInstanceOf(expectedExceptionClass);
            assertThat(e.getMessage()).isEqualTo(expectedMessage);
        });
    }

    @FunctionalInterface
    private static interface SupplierWithThrows<T> {
        T get() throws Exception;
    }
}
