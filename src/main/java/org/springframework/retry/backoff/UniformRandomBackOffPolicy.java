/*
 * Copyright 2006-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.retry.backoff;

import org.springframework.util.Assert;

import java.util.Random;
import java.util.function.Supplier;

/**
 * {@link BackOffPolicy} 的实现，在继续执行前会随机暂停一段时间。暂停通过 {@link Sleeper#sleep(long)} 实现。
 * <p>
 * 暂停时间在 {@link #setMinBackOffPeriod(long)} 和 {@link #setMaxBackOffPeriod(long)} 之间均匀分布。
 * </p>
 * <p>
 * {@link #setMinBackOffPeriod(long)} 是线程安全的，并且在多线程执行期间调用 {@link #setMaxBackOffPeriod(long)} 也是安全的，
 * 但这可能导致单次重试操作的暂停间隔不同。
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Tomaz Fernandes
 * @author Marius Lichtblau
 */
public class UniformRandomBackOffPolicy extends StatelessBackOffPolicy
        implements SleepingBackOffPolicy<UniformRandomBackOffPolicy> {

    /**
     * 默认最小延迟周期 - 500毫秒。
     */
    private static final long DEFAULT_BACK_OFF_MIN_PERIOD = 500L;

    /**
     * 默认最大延迟周期 - 1500毫秒。
     */
    private static final long DEFAULT_BACK_OFF_MAX_PERIOD = 1500L;

    private Supplier<Long> minBackOffPeriod = () -> DEFAULT_BACK_OFF_MIN_PERIOD;

    private Supplier<Long> maxBackOffPeriod = () -> DEFAULT_BACK_OFF_MAX_PERIOD;

    private final Random random = new Random(System.currentTimeMillis());

    private Sleeper sleeper = new ThreadWaitSleeper();

    public UniformRandomBackOffPolicy withSleeper(Sleeper sleeper) {
        UniformRandomBackOffPolicy res = new UniformRandomBackOffPolicy();
        res.minBackOffPeriodSupplier(minBackOffPeriod);
        res.maxBackOffPeriodSupplier(maxBackOffPeriod);
        res.setSleeper(sleeper);
        return res;
    }

    /**
     * Public setter for the {@link Sleeper} strategy.
     *
     * @param sleeper the sleeper to set defaults to {@link ThreadWaitSleeper}.
     */
    public void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /**
     * 设置最小回退周期，以毫秒为单位。不能小于1。默认值为500ms。
     *
     * @param backOffPeriod the backoff period
     */
    public void setMinBackOffPeriod(long backOffPeriod) {
        this.minBackOffPeriod = () -> (backOffPeriod > 0 ? backOffPeriod : 1);
    }

    /**
     * Set a supplier for the minimum back off period in milliseconds. Cannot be &lt; 1.
     * Default supplier supplies 500ms.
     *
     * @param backOffPeriodSupplier the backoff period
     * @since 2.0
     */
    public void minBackOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
        Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
        this.minBackOffPeriod = backOffPeriodSupplier;
    }

    /**
     * 最小退避周期，以毫秒为单位。
     *
     * @return the backoff period
     */
    public long getMinBackOffPeriod() {
        return minBackOffPeriod.get();
    }

    /**
     * 设置最大回退周期，以毫秒为单位。不能小于1。默认值为 1500ms。
     *
     * @param backOffPeriod the back off period
     */
    public void setMaxBackOffPeriod(long backOffPeriod) {
        this.maxBackOffPeriod = () -> (backOffPeriod > 0 ? backOffPeriod : 1);
    }

    /**
     * Set a supplier for the maximum back off period in milliseconds. Cannot be &lt; 1.
     * Default supplier supplies 1500ms.
     *
     * @param backOffPeriodSupplier the back off period
     * @since 2.0
     */
    public void maxBackOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
        Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
        this.maxBackOffPeriod = backOffPeriodSupplier;
    }

    /**
     * The maximum backoff period in milliseconds.
     *
     * @return the backoff period
     */
    public long getMaxBackOffPeriod() {
        return maxBackOffPeriod.get();
    }

    /**
     * 随机暂停一段时间，暂停时间介于 {@link #setMaxBackOffPeriod(long)} 和 {@link #setMinBackOffPeriod(long)} 之间
     *
     * @throws BackOffInterruptedException if interrupted during sleep.
     */
    protected void doBackOff() throws BackOffInterruptedException {
        try {
            Long min = this.minBackOffPeriod.get();
            Long max = this.maxBackOffPeriod.get();
            long delta = max <= min ? 0 : this.random.nextInt((int) (max - min));
            this.sleeper.sleep(min + delta);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
        }
    }

    public String toString() {
        return "RandomBackOffPolicy[backOffPeriod=" + minBackOffPeriod + ", " + maxBackOffPeriod + "]";
    }

}
