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

import java.util.function.Supplier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.retry.RetryContext;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * {@link BackOffPolicy} 的实现，每次重试时都会增加退避时间，直到达到上限。
 * <p>
 * 该实现是线程安全的，适用于并发访问。对配置的修改不会影响已经在进行中的重试集。
 * <p>
 * {@link #setInitialInterval(long)} 属性控制第一次重试的初始延迟值，{@link #setMultiplier(double)} 属性控制每次重试延迟递增的倍数。
 * 延迟间隔会被 {@link #setMaxInterval(long)} 限制在最大值。
 *
 * @author Rob Harrop
 * @author Dave Syer
 * @author Gary Russell
 * @author Artem Bilan
 * @author Marius Lichtblau
 * @author Anton Aharkau
 * @author Kim Sumin
 */
@SuppressWarnings("serial")
public class ExponentialBackOffPolicy implements SleepingBackOffPolicy<ExponentialBackOffPolicy> {

	protected final Log logger = LogFactory.getLog(this.getClass());

	/**
     * 默认的 'initialInterval'（初始间隔）值为 100 毫秒。
     * 结合默认的 'multiplier'（乘数）值，可以为 1-5 次重试提供合理的初始延迟分布。
	 */
	public static final long DEFAULT_INITIAL_INTERVAL = 100L;

	/**
	 * 默认的最大重试延迟时间（30秒）。
	 */
	public static final long DEFAULT_MAX_INTERVAL = 30000L;

	/**
	 * 默认的“乘数”值 - 2（每次重试增加100%的退避间隔）
	 */
	public static final double DEFAULT_MULTIPLIER = 2;

	/**
	 * 初始退避间隔
	 */
	private long initialInterval = DEFAULT_INITIAL_INTERVAL;

	/**
	 * 以毫秒为单位的最大的间隔时间
	 */
	private long maxInterval = DEFAULT_MAX_INTERVAL;

	/**
	 * 每个重试中要增加的延迟时间值。
	 */
	private double multiplier = DEFAULT_MULTIPLIER;

	/**
	 * 初始退避间隔Supplier
	 */
	private Supplier<Long> initialIntervalSupplier;

	/**
	 * 以毫秒为单位的最大的间隔时间Supplier
	 */
	private Supplier<Long> maxIntervalSupplier;

	/**
	 * 每个重试中要增加的延迟时间值Supplier
	 */
	private Supplier<Double> multiplierSupplier;

    /**
     * Sleeper
     */
	private Sleeper sleeper = new ThreadWaitSleeper();

	/**
	 * Public setter for the {@link Sleeper} strategy.
	 * @param sleeper the sleeper to set defaults to {@link ThreadWaitSleeper}.
	 */
	public void setSleeper(Sleeper sleeper) {
		this.sleeper = sleeper;
	}

    /**
     * 创建一个新的实例，并使用提供的 {@link Sleeper}。
     * @param sleeper Target to be invoked any time the backoff policy sleeps
     * @return
     */
	@Override
	public ExponentialBackOffPolicy withSleeper(Sleeper sleeper) {
		ExponentialBackOffPolicy res = newInstance();
		cloneValues(res);
		res.setSleeper(sleeper);
		return res;
	}

	protected ExponentialBackOffPolicy newInstance() {
		return new ExponentialBackOffPolicy();
	}

	protected void cloneValues(ExponentialBackOffPolicy target) {
		target.setInitialInterval(getInitialInterval());
		target.setMaxInterval(getMaxInterval());
		target.setMultiplier(getMultiplier());
		target.setSleeper(this.sleeper);
	}

	/**
	 * 设置初始睡眠间隔值。默认值为 {@code 100} 毫秒。不能设置为小于 1 的值。
	 * @param initialInterval the initial interval
	 */
	public void setInitialInterval(long initialInterval) {
		if (initialInterval < 1) {
			logger.warn("Initial interval must be at least 1, but was " + initialInterval);
		}
		this.initialInterval = initialInterval > 1 ? initialInterval : 1;
	}

	/**
	 * 设置乘数值。默认值为 '<code>2.0</code>’。提示：请勿使用远大于 1.0 的值（否则回退过程会非常漫长）。
	 * @param multiplier the multiplier
	 */
	public void setMultiplier(double multiplier) {
		if (multiplier <= 1.0) {
			logger.warn("Multiplier must be > 1.0 for effective exponential backoff, but was " + multiplier);
		}
		this.multiplier = Math.max(multiplier, 1.0);
	}

	/**
	 * 设置最大回退周期。默认值为30000（30秒）。如果使用小于1的值调用此方法，其会将值重置为1。
     * 通过设置此值可避免因多次回退（或乘数设置过高）而导致无限等待。
	 * @param maxInterval in milliseconds.
	 */
	public void setMaxInterval(long maxInterval) {
		if (maxInterval < 1) {
			logger.warn("Max interval must be positive, but was " + maxInterval);
		}
		this.maxInterval = maxInterval > 0 ? maxInterval : 1;
	}

	/**
	 * 设置初始睡眠间隔值Supplier。默认Supplier提供为 {@code 100} 毫秒。
	 * @param initialIntervalSupplier the initial interval
	 * @since 2.0
	 */
	public void initialIntervalSupplier(Supplier<Long> initialIntervalSupplier) {
		Assert.notNull(initialIntervalSupplier, "'initialIntervalSupplier' cannot be null");
		this.initialIntervalSupplier = initialIntervalSupplier;
	}

	/**
	 * Set the multiplier value. Default supplier supplies '<code>2.0</code>'. Hint: do
	 * not use values much in excess of 1.0 (or the backoff will get very long very fast).
	 * @param multiplierSupplier the multiplier
	 * @since 2.0
	 */
	public void multiplierSupplier(Supplier<Double> multiplierSupplier) {
		Assert.notNull(multiplierSupplier, "'multiplierSupplier' cannot be null");
		this.multiplierSupplier = multiplierSupplier;
	}

	/**
	 * Setter for maximum back off period. Default is 30000 (30 seconds). the value will
	 * be reset to 1 if this method is called with a value less than 1. Set this to avoid
	 * infinite waits if backing off a large number of times (or if the multiplier is set
	 * too high).
	 * @param maxIntervalSupplier in milliseconds.
	 * @since 2.0
	 */
	public void maxIntervalSupplier(Supplier<Long> maxIntervalSupplier) {
		Assert.notNull(maxIntervalSupplier, "'maxIntervalSupplier' cannot be null");
		this.maxIntervalSupplier = maxIntervalSupplier;
	}

	protected Supplier<Long> getInitialIntervalSupplier() {
		return initialIntervalSupplier;
	}

	protected Supplier<Long> getMaxIntervalSupplier() {
		return maxIntervalSupplier;
	}

	protected Supplier<Double> getMultiplierSupplier() {
		return multiplierSupplier;
	}

	/**
	 * The initial period to sleep on the first backoff.
	 * @return the initial interval
	 */
	public long getInitialInterval() {
		return this.initialIntervalSupplier != null ? this.initialIntervalSupplier.get() : this.initialInterval;
	}

	/**
	 * The maximum interval to sleep for. Defaults to 30 seconds.
	 * @return the maximum interval.
	 */
	public long getMaxInterval() {
		return this.maxIntervalSupplier != null ? this.maxIntervalSupplier.get() : this.maxInterval;
	}

	/**
	 * The multiplier to use to generate the next backoff interval from the last.
	 * @return the multiplier in use
	 */
	public double getMultiplier() {
		return this.multiplierSupplier != null ? this.multiplierSupplier.get() : this.multiplier;
	}

	/**
	 * 返回具有配置属性的特定的 {@link BackOffContext} 实现
	 */
	@Override
	public BackOffContext start(RetryContext context) {
		return new ExponentialBackOffContext(
                this.initialInterval,
                this.multiplier,
                this.maxInterval,
				this.initialIntervalSupplier,
                this.multiplierSupplier,
                this.maxIntervalSupplier
        );
	}

	/**
	 * 暂停以等待当前的超时间隔。
	 */
	@Override
	public void backOff(BackOffContext backOffContext) throws BackOffInterruptedException {
		ExponentialBackOffContext context = (ExponentialBackOffContext) backOffContext;
		try {
			long sleepTime = context.getSleepAndIncrement();
			if (this.logger.isDebugEnabled()) {
				this.logger.debug("Sleeping for " + sleepTime);
			}
			this.sleeper.sleep(sleepTime);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new BackOffInterruptedException("Thread interrupted while sleeping", e);
		}
	}

    /**
     * 指数退避上下文
     */
	static class ExponentialBackOffContext implements BackOffContext {

        /**
         * 乘数, 每次重试时延迟增加的倍数
         */
		private final double multiplier;

        /**
         * 当前间隔 -> 当前退避时间
         */
		private long interval;

        /**
         * 最大间隔 -> 最大的退避时间
         */
		private final long maxInterval;

        /**
         * 初始间隔供应商（仅在第一次调用 getInterval() 时使用，之后设为 null ）
         */
		private Supplier<Long> initialIntervalSupplier;

        /**
         * 乘数供应商（如果不为 null，则每次调用 getMultiplier() 时使用）
         */
		private final Supplier<Double> multiplierSupplier;

        /**
         * 最大间隔供应商（如果不为 null，则每次调用 getMaxInterval() 时使用）
         */
		private final Supplier<Long> maxIntervalSupplier;

		public ExponentialBackOffContext(long interval, double multiplier, long maxInterval,
				Supplier<Long> intervalSupplier, Supplier<Double> multiplierSupplier,
				Supplier<Long> maxIntervalSupplier) {
			this.interval = interval;
			this.multiplier = multiplier;
			this.maxInterval = maxInterval;
			this.initialIntervalSupplier = intervalSupplier;
			this.multiplierSupplier = multiplierSupplier;
			this.maxIntervalSupplier = maxIntervalSupplier;
		}

        /**
         * 获取当前的睡眠时间并递增间隔
         * @return
         */
		public synchronized long getSleepAndIncrement() {
			long sleep = getInterval();
			long max = getMaxInterval();
			if (sleep > max) {
				sleep = max;
			}
			else {
				this.interval = getNextInterval();
			}
			return sleep;
		}

        /**
         * 获取下一次的间隔
         * @return
         */
		protected long getNextInterval() {
			return (long) (this.interval * getMultiplier());
		}

		public double getMultiplier() {
			return this.multiplierSupplier != null ? this.multiplierSupplier.get() : this.multiplier;
		}

        /**
         * 获取当前间隔，即: 当前的睡眠时间
         * @return
         */
		public long getInterval() {
			if (this.initialIntervalSupplier != null) {
				this.interval = this.initialIntervalSupplier.get();
				this.initialIntervalSupplier = null;
			}
			return this.interval;
		}

        /**
         * 获取最大间隔, 即：最大的睡眠时间
         * @return
         */
		public long getMaxInterval() {
			return this.maxIntervalSupplier != null ? this.maxIntervalSupplier.get() : this.maxInterval;
		}
	}

	@Override
	public String toString() {
		return ClassUtils.getShortName(getClass()) + "[initialInterval=" + getInitialInterval() + ", multiplier="
				+ getMultiplier() + ", maxInterval=" + getMaxInterval() + "]";
	}

}
