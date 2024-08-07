/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2019-2021 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/main/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.microprofile.faulttolerance.policy;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.interceptor.InvocationContext;

import org.eclipse.microprofile.faulttolerance.exceptions.BulkheadException;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceDefinitionException;
import org.eclipse.microprofile.faulttolerance.exceptions.FaultToleranceException;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;

import fish.payara.microprofile.faulttolerance.FaultToleranceConfig;
import fish.payara.microprofile.faulttolerance.FaultToleranceMethodContext;
import fish.payara.microprofile.faulttolerance.FaultToleranceMethodContext.AsyncFuture;
import fish.payara.microprofile.faulttolerance.FaultToleranceService;
import fish.payara.microprofile.faulttolerance.FaultToleranceMetrics;
import fish.payara.microprofile.faulttolerance.state.CircuitBreakerState;

/**
 * The {@link FaultTolerancePolicy} describes the effective aggregated policies to use for a particular {@link Method}
 * when adding fault tolerant behaviour to it.
 *
 * The policies are extracted from FT annotations and the {@link FaultToleranceConfig}.
 *
 * In contrast to the plain annotations the policies do consider configuration overrides and include validation of the
 * effective values.
 *
 * The policy class also reduces the need to analyse FT annotations for each invocation and works as a consistent source
 * of truth throughout the processing of FT behaviour that is convenient to pass around as a single immutable value.
 *
 * @author Jan Bernitt
 */
public final class FaultTolerancePolicy implements Serializable {

    static final Logger logger = Logger.getLogger(FaultTolerancePolicy.class.getName());

    private static final long TTL = 60 * 1000;

    /**
     * A simple cache with a fix {@link #TTL} with a policy for each target method.
     */
    private static final ConcurrentHashMap<Class<?>, Map<Method, FaultTolerancePolicy>> POLICY_BY_METHOD
        = new ConcurrentHashMap<>();

    /**
     * Removes all expired policies from the cache.
     */
    public static void clean() {
        long now = System.currentTimeMillis();
        POLICY_BY_METHOD.forEachValue(Long.MAX_VALUE,
                map -> map.entrySet().removeIf(entry -> now > entry.getValue().expiresMillis));
    }


    /**
     * Removes all expired policies from the cache and all policies related to this classloader
     */
    public static void clean(ClassLoader appClassLoader) {
        long now = System.currentTimeMillis();
        POLICY_BY_METHOD.entrySet().removeIf(entry -> entry.getKey().getClassLoader().equals(appClassLoader));
        clean();
    }

    public static FaultTolerancePolicy asAnnotated(Class<?> target, Method annotated) {
        return create(new StaticAnalysisContext(target, annotated),
                FaultToleranceConfig.asAnnotated(target, annotated));
    }

    /**
     * Returns the {@link FaultTolerancePolicy} to use for the method invoked in the current context.
     *
     * @param context       current context
     * @param configSpplier supplies the configuration (if needed, in case returned policy needs to be created with help
     *                      of the {@link FaultToleranceConfig})
     * @return the policy to apply
     * @throws FaultToleranceDefinitionException in case the effective policy contains illegal values
     */
    public static FaultTolerancePolicy get(InvocationContext context, Supplier<FaultToleranceConfig> configSpplier)
            throws FaultToleranceDefinitionException {
        return POLICY_BY_METHOD.computeIfAbsent(context.getTarget().getClass(), target -> new ConcurrentHashMap<>())
                .compute(context.getMethod(), (method, policy) ->
                    policy != null && !policy.isExpired() ? policy : create(context, configSpplier.get()));
    }


    private static FaultTolerancePolicy create(InvocationContext context, FaultToleranceConfig config) {
        return new FaultTolerancePolicy(
                config.isNonFallbackEnabled(),
                config.isMetricsEnabled(),
                AsynchronousPolicy.create(context, config),
                BulkheadPolicy.create(context, config),
                CircuitBreakerPolicy.create(context, config),
                FallbackPolicy.create(context, config),
                RetryPolicy.create(context, config),
                TimeoutPolicy.create(context, config));
    }

    private final long expiresMillis;
    public final boolean isPresent;
    public final boolean isNonFallbackEnabled;
    public final boolean isMetricsEnabled;
    public final AsynchronousPolicy asynchronous;
    public final BulkheadPolicy bulkhead;
    public final CircuitBreakerPolicy circuitBreaker;
    public final FallbackPolicy fallback;
    public final RetryPolicy retry;
    public final TimeoutPolicy timeout;

    public FaultTolerancePolicy(boolean isNonFallbackEnabled, boolean isMetricsEnabled, AsynchronousPolicy asynchronous,
            BulkheadPolicy bulkhead, CircuitBreakerPolicy circuitBreaker, FallbackPolicy fallback, RetryPolicy retry,
            TimeoutPolicy timeout) {
        this.expiresMillis = System.currentTimeMillis() + TTL;
        this.isNonFallbackEnabled = isNonFallbackEnabled;
        this.isMetricsEnabled = isMetricsEnabled;
        this.asynchronous = asynchronous;
        this.bulkhead = bulkhead;
        this.circuitBreaker = circuitBreaker;
        this.fallback = fallback;
        this.retry = retry;
        this.timeout = timeout;
        this.isPresent = isAsynchronous() || isBulkheadPresent() || isCircuitBreakerPresent()
                || isFallbackPresent() || isRetryPresent() || isTimeoutPresent();
    }

    private boolean isExpired() {
        return System.currentTimeMillis() > expiresMillis;
    }

    public boolean isAsynchronous() {
        return asynchronous != null;
    }

    public boolean isBulkheadPresent() {
        return bulkhead != null;
    }

    public boolean isCircuitBreakerPresent() {
        return circuitBreaker != null;
    }

    public boolean isFallbackPresent() {
        return fallback != null;
    }

    public boolean isRetryPresent() {
        return retry != null;
    }

    public boolean isTimeoutPresent() {
        return timeout != null;
    }

    static final class FaultToleranceInvocation {
        final FaultToleranceMethodContext context;
        final FaultToleranceMetrics metrics;
        final CompletableFuture<Object> asyncResult;
        final Set<Thread> asyncWorkers;

        FaultToleranceInvocation(FaultToleranceMethodContext context, FaultToleranceMetrics metrics,
                CompletableFuture<Object> asyncResult, Set<Thread> asyncWorkers) {
            this.context = context;
            this.metrics = metrics;
            this.asyncResult = asyncResult;
            this.asyncWorkers = asyncWorkers;
        }

        Object runStageWithWorker(Callable<Object> stage) throws Exception {
            timeoutIfConcludedConcurrently();
            Thread current = Thread.currentThread();
            asyncWorkers.add(current);
            try {
                return stage.call();
            } finally {
                asyncWorkers.remove(current);
            }
        }

        void timeoutIfConcludedConcurrently() throws TimeoutException {
            if (asyncResult != null && asyncResult.isDone() || Thread.currentThread().isInterrupted()) {
                throw new TimeoutException("Computation already concluded in a concurrent attempt");
            }
        }

        void trace(String method) {
            context.trace(method);
        }

        void endTrace() {
            context.endTrace();
        }

        @Override
        public String toString() {
            return "FaultToleranceInvocation[context=" + context.toString() + ", isDone=" +
                    (asyncResult == null ? "(sync)" : asyncResult.isDone()) + "]";
        }
    }

    /**
     * Wraps {@link InvocationContext#proceed()} with fault tolerance behaviour.
     *
     * Processing has 6 stages:
     * <pre>
     * 1) Asynchronous
     * 2) Fallback
     * 3) Retry
     * 4) Circuit Breaker
     * 5) Timeout
     * 6) Bulkhead
     * </pre>
     * The call chain goes from 1) down to 6) skipping stages that are not requested by this policy.
     *
     * Asynchronous execution branches to new threads in stage 1) and 3) each executed by the
     * {@link FaultToleranceService#runAsynchronous(CompletableFuture, Callable)}.
     *
     * @param context intercepted call context
     * @param ftmContextSupplier the environment used to execute the FT behaviour
     * @return the result of {@link InvocationContext#proceed()} after applying FT behaviour
     * @throws Exception as thrown by the wrapped invocation or a {@link FaultToleranceException}
     */
    public Object proceed(InvocationContext context, Supplier<FaultToleranceMethodContext> ftmContextSupplier) throws Exception {
        if (!isPresent) {
            logger.log(Level.FINER, "Fault Tolerance not enabled, proceeding normally.");
            return context.proceed();
        }
        FaultToleranceMethodContext ftmContext = ftmContextSupplier.get();
        FaultToleranceMetrics metrics = ftmContext.getMetrics().boundTo(ftmContext, this);
        try {
            Object res = processAsynchronousStage(ftmContext, metrics);
            if (res instanceof AsyncFuture) {
                AsyncFuture async = (AsyncFuture) res;
                async.whenComplete((value, ex) -> { // first evaluate async when the results are in...
                    if (isExceptionThrown(async)) {
                        metrics.incrementInvocationsExceptionThrown();
                    } else {
                        metrics.incrementInvocationsValueReturned();
                    }
                });
            } else {
                metrics.incrementInvocationsValueReturned();
            }
            return res;
        } catch (Exception | Error ex) {
            metrics.incrementInvocationsExceptionThrown();
            throw ex;
        }
    }

    private boolean isExceptionThrown(AsyncFuture async) {
        return async.isExceptionThrown() || async.isCompletedExceptionally() && !asynchronous.isSuccessWhenCompletedExceptionally();
    }

    /**
     * Stage that takes care of the {@link AsynchronousPolicy} handling.
     */
    private Object processAsynchronousStage(FaultToleranceMethodContext context,
            FaultToleranceMetrics metrics) throws Exception {
        if (!isAsynchronous()) {
            return processFallbackStage(new FaultToleranceInvocation(context, metrics, null, null));
        }
        logger.log(Level.FINER, "Proceeding invocation with asynchronous semantics");
        Set<Thread> workers = ConcurrentHashMap.newKeySet();
        AsyncFuture asyncResult = new AsyncFuture() {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                boolean res = super.cancel(mayInterruptIfRunning);
                if (mayInterruptIfRunning) {
                    logger.log(Level.FINE, "Asynchronous computation was cancelled by caller.");
                    if (mayInterruptIfRunning) {
                        workers.forEach(worker -> worker.interrupt());
                    }
                }
                return res;
            }
        };
        FaultToleranceInvocation invocation = new FaultToleranceInvocation(context, metrics, asyncResult, workers);
        context.runAsynchronous(asyncResult,
                () -> invocation.runStageWithWorker(() -> processFallbackStage(invocation)));
        return asyncResult;
    }

    /**
     * Stage that takes care of the {@link FallbackPolicy} handling.
     */
    private Object processFallbackStage(FaultToleranceInvocation invocation) throws Exception {
        if (!isFallbackPresent()) {
            return processRetryStage(invocation);
        }
        logger.log(Level.FINER, "Proceeding invocation with fallback semantics");
        invocation.trace("executeFallbackMethod");
        try {
            return processRetryStage(invocation);
        } catch (Exception | Error ex) {
            if (!fallback.isFallbackApplied(ex)) {
                throw ex;
            }
            invocation.metrics.incrementFallbackCallsTotal();
            if (fallback.isHandlerPresent()) {
                logger.log(Level.FINE, "Using fallback class: {0}", fallback.value.getName());
                return invocation.context.fallbackHandle(fallback.value, ex);
            }
            logger.log(Level.FINE, "Using fallback method: {0}", fallback.method.getName());
            return invocation.context.fallbackInvoke(fallback.method);
        } finally {
            invocation.endTrace();
        }
    }

    /**
     * Stage that takes care of the {@link RetryPolicy} handling.
     */
    private Object processRetryStage(FaultToleranceInvocation invocation) throws Exception {
        if (!retry.isNone()) {
            logger.log(Level.FINER, "Proceeding invocation with retry semantics");
        }
        int totalAttempts = retry.totalAttempts();
        int attemptsLeft = totalAttempts;
        Long retryTimeoutTime = retry.timeoutTimeNow();
        while (attemptsLeft > 0) {
            attemptsLeft--;
            try {
                boolean firstAttempt = attemptsLeft == totalAttempts - 1;
                if (!firstAttempt) {
                    logger.log(Level.FINER, "Attempting retry.");
                    invocation.metrics.incrementRetryRetriesTotal();
                }
                Object resultValue = isAsynchronous()
                        ? processRetryAsync(invocation)
                        : processCircuitBreakerStage(invocation, null);
                invocation.metrics.incrementRetryCallsValueReturned();
                return resultValue;
            } catch (Exception | Error ex) {
                boolean timedOut = retryTimeoutTime != null && System.currentTimeMillis() >= retryTimeoutTime;
                if (!timedOut && !retry.retryOn(ex)) {
                    invocation.metrics.incrementRetryCallsExceptionNotRetryable();
                    throw ex; // counts as "success"
                }
                if (timedOut || attemptsLeft <= 0) {
                    logger.log(Level.FINE, "Retry attemp failed. Giving up{0}", timedOut ? " due to time-out." : ".");
                    if (timedOut) {
                        invocation.metrics.incrementRetryCallsMaxDurationReached();
                    } else {
                        invocation.metrics.incrementRetryCallsMaxRetriesReached();
                    }
                    throw ex;
                }
                logger.log(Level.FINE, "Retry attempt failed. {0} attempts left.", attemptsLeft);
                if (retry.isDelayed()) {
                    invocation.context.delay(retry.jitteredDelay());
                }
            }
        }
        // this line should never be reached as we throw above
        throw new FaultToleranceException("Retry failed");
    }

    private AsyncFuture processRetryAsync(FaultToleranceInvocation invocation) throws Exception {
        AsyncFuture asyncAttempt = new AsyncFuture();
        invocation.context.runAsynchronous(asyncAttempt,
                () -> invocation.runStageWithWorker(() -> processCircuitBreakerStage(invocation, asyncAttempt)));
        try {
            asyncAttempt.get(); // wait and only proceed on success
            invocation.timeoutIfConcludedConcurrently();
            return asyncAttempt;
        } catch (ExecutionException ex) { // this ExecutionException is from calling get() above in case completed exceptionally
            if (!asyncAttempt.isExceptionThrown() && asynchronous.isSuccessWhenCompletedExceptionally()) {
            invocation.timeoutIfConcludedConcurrently();
                return asyncAttempt;
            }
            rethrow(ex.getCause());
            return null; // not reachable
                }
    }

    private static void rethrow(Throwable t) throws Exception {
        if (t instanceof Exception) {
            throw (Exception)t;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw new ExecutionException(t);
    }

    /**
     * Stage that takes care of the {@link CircuitBreakerPolicy} handling.
     */
    private Object processCircuitBreakerStage(FaultToleranceInvocation invocation, AsyncFuture asyncAttempt) throws Exception {
        if (!isCircuitBreakerPresent()) {
            return processTimeoutStage(invocation, asyncAttempt);
        }
        logger.log(Level.FINER, "Proceeding invocation with circuitbreaker semantics");
        CircuitBreakerState state = invocation.context.getState();
        Object resultValue = null;
        switch (state.getCircuitState()) {
        default:
        case OPEN:
            logger.log(Level.FINER, "CircuitBreaker is open, throwing exception");
            invocation.metrics.incrementCircuitbreakerCallsPreventedTotal();
            throw new CircuitBreakerOpenException();
        case HALF_OPEN:
            logger.log(Level.FINER, "Proceeding half open CircuitBreaker context");
            try {
                resultValue = processTimeoutStage(invocation, asyncAttempt);
            } catch (Exception | Error ex) {
                if (circuitBreaker.isFailure(ex)) {
                    invocation.metrics.incrementCircuitbreakerCallsFailedTotal();
                    logger.log(Level.FINE, "Exception causes CircuitBreaker to transit: half-open => open");
                    openCircuit(invocation, state);
                } else {
                    invocation.metrics.incrementCircuitbreakerCallsSucceededTotal();
                }
                throw ex;
            }
            if (state.halfOpenSuccessfulClosedCircuit(circuitBreaker.successThreshold)) {
                logger.log(Level.FINE, "Success threshold causes CircuitBreaker to transit: half-open => closed");
            }
            invocation.metrics.incrementCircuitbreakerCallsSucceededTotal();
            return resultValue;
        case CLOSED:
            logger.log(Level.FINER, "Proceeding closed CircuitBreaker context");
            Throwable failedOn = null;
            try {
                resultValue = processTimeoutStage(invocation, asyncAttempt);
                state.recordClosedOutcome(true);
            } catch (Exception | Error ex) {
                if (circuitBreaker.isFailure(ex)) {
                    state.recordClosedOutcome(false);
                    invocation.metrics.incrementCircuitbreakerCallsFailedTotal();
                } else {
                    state.recordClosedOutcome(true);
                    invocation.metrics.incrementCircuitbreakerCallsSucceededTotal();
                }
                failedOn = ex;
            }
            if (state.isOverFailureThreshold()) {
                logger.log(Level.FINE, "Failure threshold causes CircuitBreaker to transit: closed => open");
                openCircuit(invocation, state);
            }
            if (failedOn != null) {
                rethrow(failedOn);
            }
            invocation.metrics.incrementCircuitbreakerCallsSucceededTotal();
            return resultValue;
        }
    }

    private void openCircuit(FaultToleranceInvocation invocation, CircuitBreakerState state) throws Exception {
        invocation.metrics.incrementCircuitbreakerOpenedTotal();
        state.open();
        if (circuitBreaker.delay == 0L) {
            state.halfOpen();
        } else {
            invocation.context.runDelayed(circuitBreaker.delay, state::halfOpen);
        }
    }

    /**
     * Stage that takes care of the {@link TimeoutPolicy} handling.
     */
    private Object processTimeoutStage(FaultToleranceInvocation invocation, AsyncFuture asyncAttempt) throws Exception {
        if (!isTimeoutPresent()) {
            return processBulkheadStage(invocation);
        }
        logger.log(Level.FINER, "Proceeding invocation with timeout semantics");
        long timeoutDuration = Duration.of(timeout.value, timeout.unit).toMillis();
        long timeoutTime = System.currentTimeMillis() + timeoutDuration;
        Thread current = Thread.currentThread();
        AtomicBoolean timedOut = new AtomicBoolean(false);
        Future<?> timeout = invocation.context.runDelayed(timeoutDuration, () -> {
            logger.log(Level.FINE, "Interrupting attempt due to timeout.");
            timedOut.set(true);
            current.interrupt();
            invocation.metrics.incrementTimeoutCallsTimedOutTotal();
            if (asyncAttempt != null) {
                // we do this since interrupting not necessarily returns directly or ever but the attempt should timeout now
                asyncAttempt.setExceptionThrown(true);
                asyncAttempt.completeExceptionally(new TimeoutException());
            }
        });
        long executionStartTime = System.nanoTime();
        try {
            Object resultValue = processBulkheadStage(invocation);
            if (current.isInterrupted()) {
                Thread.interrupted(); // clear the flag
            }
            if (timedOut.get() || System.currentTimeMillis() > timeoutTime) {
                throw new TimeoutException();
            }
            invocation.metrics.incrementTimeoutCallsNotTimedOutTotal();
            return resultValue;
        } catch (TimeoutException ex) {
            logger.log(Level.FINE, "Execution timed out.");
            throw ex;
        } catch (Exception | Error ex) {
            if (timedOut.get() || System.currentTimeMillis() > timeoutTime) {
                logger.log(Level.FINE, "Execution timed out.");
                throw new TimeoutException(ex);
            }
            throw ex;
        } finally {
            invocation.metrics.addTimeoutExecutionDuration(System.nanoTime() - executionStartTime);
            timeout.cancel(true);
        }
    }

    /**
     * Stage that takes care of the {@link BulkheadPolicy} handling.
     */
    private Object processBulkheadStage(FaultToleranceInvocation invocation) throws Exception {
        if (!isBulkheadPresent()) {
            return proceed(invocation);
        }
        logger.log(Level.FINER, () -> "Proceeding invocation with bulkhead semantics in "+invocation);
        final boolean isAsync = isAsynchronous();
        final boolean exitIsOnCompletion = isAsync && bulkhead.exitIsOnCompletion;
        boolean directExit = false; // Whether or not we semantically leave the bulkhead when leaving this method
        final int runCapacity = bulkhead.value;
        final int queueCapacity = isAsync ? bulkhead.waitingTaskQueue : 0;
        AtomicInteger queuingOrRunning = invocation.context.getQueuingOrRunningPopulation();
        while (true) {
            final int currentlyIn = queuingOrRunning.get();
            if (currentlyIn >= runCapacity + queueCapacity) {
                invocation.metrics.incrementBulkheadCallsRejectedTotal();
                logger.log(Level.FINER, "No free work or queue space.");
                throw new BulkheadException("No free work or queue space.");
            }
            logger.log(Level.FINER, "Attempting to enter bulkhead.");
            // did someone else get next in row in the meantime?
            if (queuingOrRunning.compareAndSet(currentlyIn, currentlyIn + 1)) {
                // we are in the queue, yeah
                directExit = true;
                try {
                    logger.log(Level.FINE, "Entered bulkhead.");
                    invocation.metrics.incrementBulkheadCallsAcceptedTotal();
                    BlockingQueue<Thread> running = invocation.context.getConcurrentExecutions();
                    logger.log(Level.FINER, "Attempting to enter bulkhead execution.");
                    final Thread currentThread = Thread.currentThread();
                    if (isAsync) {
                        long waitingSince = System.nanoTime();
                        try {
                            // wait until we can run...
                            running.put(currentThread);
                        } finally {
                            invocation.metrics.addBulkheadWaitingDuration(Math.max(1, System.nanoTime() - waitingSince));
                        }
                    }
                    // we are in!
                    long executionSince = System.nanoTime();
                    try {
                        logger.log(Level.FINE, "Entered bulkhead execution.");
                        // ok, lets run
                        Object res = proceed(invocation);
                        if (!exitIsOnCompletion) {
                            logger.log(Level.FINER, () -> "Exiting synchronously with "+res);
                            return res;
                        }
                        directExit = false; // if we make if here exit is going to happen on completion
                        CompletionStage<?> asyncResult = ((CompletionStage<?>) res);
                        asyncResult.whenComplete((value, exception) -> {
                            logger.log(Level.FINER, () -> "Bulkhead invocation "+invocation+ " finished " + (exception != null ? "with exception "+exception.getMessage() : "sucessfully"));
                            invocation.metrics.addBulkheadExecutionDuration(Math.max(1, System.nanoTime() - executionSince));
                            // successful or not, we are out...
                            running.remove(currentThread);
                            queuingOrRunning.decrementAndGet();
                        });
                        return asyncResult; //OBS! we do not want to return the result of 'whenComplete' call because this gobbles cancel
                    } finally {
                        if (directExit) {
                            invocation.metrics.addBulkheadExecutionDuration(Math.max(1, System.nanoTime() - executionSince));
                            if (isAsync) {
                                running.remove(currentThread);
                            }
                        }
                    }
                } finally {
                    // get out of bulkhead unless this first occurs on completion of the CompletionStage
                    if (directExit) {
                        queuingOrRunning.decrementAndGet();
                    }
                }
            }
        }
    }

    /**
     * Final stage where the actual wrapped method call occurs.
     */
    private static Object proceed(FaultToleranceInvocation invocation) throws Exception {
        invocation.timeoutIfConcludedConcurrently();
        logger.log(Level.FINER, "Proceeding invocation chain");
        return invocation.context.proceed();
    }

}
