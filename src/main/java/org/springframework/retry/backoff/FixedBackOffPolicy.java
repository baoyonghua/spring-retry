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

import java.util.function.Supplier;

/**
 * {@link BackOffPolicy} 的实现，在继续执行前会暂停固定的时间。
 * 暂停通过 {@link Sleeper#sleep(long)} 实现。
 * <p>
 * {@link #setBackOffPeriod(long)} 是线程安全的，可以在多个线程中同时调用，但这可能导致单次重试操作的暂停间隔不一致。
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Artem Bilan
 * @author Marius Lichtblau
 */
public class FixedBackOffPolicy extends StatelessBackOffPolicy implements SleepingBackOffPolicy<FixedBackOffPolicy> {

    /**
     * 默认退避周期 - 1000毫秒。
     */
    private static final long DEFAULT_BACK_OFF_PERIOD = 1000L;

    /**
     * 以毫秒为单位的退避间隔。默认为1000毫秒。
     */
    private Supplier<Long> backOffPeriod = () -> DEFAULT_BACK_OFF_PERIOD;

    private Sleeper sleeper = new ThreadWaitSleeper();

    public FixedBackOffPolicy withSleeper(Sleeper sleeper) {
        FixedBackOffPolicy res = new FixedBackOffPolicy();
        res.backOffPeriodSupplier(backOffPeriod);
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
     * 设置复位间隔为毫秒。不能小于1。默认值为1000毫秒。
     *
     * @param backOffPeriod the back off period
     */
    public void setBackOffPeriod(long backOffPeriod) {
        this.backOffPeriod = () -> (backOffPeriod > 0 ? backOffPeriod : 1);
    }

    /**
     * Set a supplier for the back off period in milliseconds. Cannot be &lt; 1. Default
     * supplier supplies 1000ms.
     *
     * @param backOffPeriodSupplier the back off period
     * @since 2.0
     */
    public void backOffPeriodSupplier(Supplier<Long> backOffPeriodSupplier) {
        Assert.notNull(backOffPeriodSupplier, "'backOffPeriodSupplier' cannot be null");
        this.backOffPeriod = backOffPeriodSupplier;
    }

    /**
     * 以毫秒为单位的后退时间。
     *
     * @return the backoff period
     */
    public long getBackOffPeriod() {
        return this.backOffPeriod.get();
    }

    /**
     * 暂停固定的毫秒数
     *
     * @throws BackOffInterruptedException if interrupted during sleep.
     * @see #setBackOffPeriod(long)
     */
    protected void doBackOff() throws BackOffInterruptedException {
        try {
            sleeper.sleep(this.backOffPeriod.get());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
        }
    }

    public String toString() {
        return "FixedBackOffPolicy[backOffPeriod=" + this.backOffPeriod.get() + "]";
    }

}
