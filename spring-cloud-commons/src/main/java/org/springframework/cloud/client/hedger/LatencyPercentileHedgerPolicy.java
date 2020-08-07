/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.client.hedger;

import java.time.Duration;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Predicate;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSnapshot;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.micrometer.core.instrument.distribution.TimeWindowPercentileHistogram;

import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * @author Csaba Kos
 * @author Kevin Binswanger
 */
public class LatencyPercentileHedgerPolicy implements HedgerPolicy {
	private final Predicate<ClientRequest> shouldTrackRequestPredicate;
	private final int numHedgedRequests;
	private final TimeWindowMax max;
	private final TimeWindowPercentileHistogram histogram;
	private final LongAdder count = new LongAdder();
	private final DoubleAdder total = new DoubleAdder();

	public LatencyPercentileHedgerPolicy(
			int numHedgedRequests,
			double percentile
	) {
		this(clientRequest -> true, numHedgedRequests, percentile, Clock.SYSTEM);
	}

	public LatencyPercentileHedgerPolicy(
			Predicate<ClientRequest> shouldTrackRequestPredicate,
			int numHedgedRequests,
			double percentile,
			Clock clock
	) {
		this(shouldTrackRequestPredicate, numHedgedRequests, makeDefaultConfig(percentile), clock);
	}

	public LatencyPercentileHedgerPolicy(
			Predicate<ClientRequest> shouldTrackRequestPredicate,
			int numHedgedRequests,
			DistributionStatisticConfig distributionStatisticConfig,
			Clock clock
	) {
		this.shouldTrackRequestPredicate = shouldTrackRequestPredicate;
		this.numHedgedRequests = numHedgedRequests;

		max = new TimeWindowMax(clock, distributionStatisticConfig);
		histogram = new TimeWindowPercentileHistogram(clock, distributionStatisticConfig, false);
	}

	private static DistributionStatisticConfig makeDefaultConfig(double percentile) {
		return DistributionStatisticConfig.builder()
				.percentilesHistogram(true)
				.percentiles(percentile)
				.build()
				.merge(DistributionStatisticConfig.DEFAULT);
	}

	@Override
	public boolean shouldHedge(ClientRequest request) {
		return shouldTrackRequestPredicate.test(request);
	}

	@Override
	public int getNumberOfHedgedRequests(ClientRequest request) {
		return numHedgedRequests;
	}

	public Duration getDelayBeforeHedging(ClientRequest request) {
		return Duration.ofMillis((long) getHistogramSnapshot().percentileValues()[0].value());
	}

	HistogramSnapshot getHistogramSnapshot() {
		return histogram.takeSnapshot(count.longValue(), total.doubleValue(), max.poll());
	}

	@Override
	public void record(
			ClientRequest request,
			ClientResponse response,
			long elapsedMillis,
			@Nullable Integer hedgeNumber
	) {
		if (shouldTrackRequestPredicate.test(request) && response.statusCode().is2xxSuccessful()) {
			record(elapsedMillis);
		}
	}

	private void record(long elapsedMillis) {
		count.increment();
		total.add(elapsedMillis);
		max.record(elapsedMillis);
		histogram.recordLong(elapsedMillis);
	}
}
