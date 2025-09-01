/*
 * Copyright 2006-2022 the original author or authors.
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

import java.util.Random;
import java.util.function.Supplier;

import org.springframework.retry.RetryContext;

/**
 * {@link org.springframework.retry.backoff.ExponentialBackOffPolicy} 的实现，
 * 它选择一个来自确定性指数间隔的随机倍数。该随机倍数在 1 和确定性乘数之间均匀分布（因此实际上间隔介于确定性情况下的下一个和下下个间隔之间）。这通常被称为抖动（jitter）。
 * <p>
 * 这在测试场景中已被证明至少是有用的，当测试需要大量重试时会产生过度争用。
 * 在测试中，通常线程会同时启动，因此会一起进入下一个间隔。使用此
 * {@link BackOffPolicy} 可以帮助避免这种情况。
 * <p>
 * 示例：initialInterval = 50 multiplier = 2.0 maxInterval = 3000 numRetries = 5
 * <p>
 * {@link ExponentialBackOffPolicy} 产生：[50, 100, 200, 400, 800]
 * <p>
 * {@link ExponentialRandomBackOffPolicy} 可能产生 [76, 151, 304, 580, 901] 或 [53, 190,
 * 267, 451, 815]（在 [50-100, 100-200, 200-400, 400-800, 800-1600] 区间内的随机分布值）
 *
 * @author Jon Travis
 * @author Dave Syer
 * @author Chase Diem
 */
@SuppressWarnings("serial")
public class ExponentialRandomBackOffPolicy extends ExponentialBackOffPolicy {

	/**
	 * 返回一个新的 {@link org.springframework.retry.backoff.BackOffContext}实例
	 * seeded with this policies settings.
	 */
	public BackOffContext start(RetryContext context) {
		return new ExponentialRandomBackOffContext(
                getInitialInterval(),
                getMultiplier(),
                getMaxInterval(),
				getInitialIntervalSupplier(),
                getMultiplierSupplier(),
                getMaxIntervalSupplier()
        );
	}

	protected ExponentialBackOffPolicy newInstance() {
		return new ExponentialRandomBackOffPolicy();
	}

    /**
     * 随机的指数退避上下文
     */
	static class ExponentialRandomBackOffContext extends ExponentialBackOffPolicy.ExponentialBackOffContext {

		private final Random r = new Random();

		public ExponentialRandomBackOffContext(long expSeed, double multiplier, long maxInterval,
				Supplier<Long> expSeedSupplier, Supplier<Double> multiplierSupplier,
				Supplier<Long> maxIntervalSupplier) {

			super(expSeed, multiplier, maxInterval, expSeedSupplier, multiplierSupplier, maxIntervalSupplier);
		}

		@Override
		public synchronized long getSleepAndIncrement() {
			long next = super.getSleepAndIncrement();
            // 为下一个间隔选择一个随机倍数
			next = (long) (next * (1 + r.nextFloat() * (getMultiplier() - 1)));
			if (next > super.getMaxInterval()) {
				next = super.getMaxInterval();
			}
			return next;
		}

	}

}
