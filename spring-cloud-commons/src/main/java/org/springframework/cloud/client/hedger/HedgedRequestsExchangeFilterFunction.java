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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;

/**
 * WebClient filter function that allows for "hedging" requests that take too long: after the specified delay, if
 * the original request was not completed yet, it will fire one or more "hedged" requests in hopes that they will
 * return sooner than the original request.
 *
 * This is useful to try to cut down on the long tail latencies of requests. For example, one could set the delay
 * to the 95th percentile latency of the downstream service. Typically that will result in a significant reduction of
 * 95th percentile latency in exchange for 2-5% traffic increase. (The actual numbers depend on the distribution of
 * latencies.)
 *
 * {@see http://accelazh.github.io/storage/Tail-Latency-Study} for more background on how hedging works.
 *
 * @author Csaba Kos
 * @author Kevin Binswanger
 */
public class HedgedRequestsExchangeFilterFunction implements ExchangeFilterFunction {

	private static final Log log = LogFactory
			.getLog(HedgedRequestsExchangeFilterFunction.class);

	private HedgingClient hedgingClient;
	private List<HedgingMetricsReporter> hedgingMetricsReporterList;

	public HedgedRequestsExchangeFilterFunction(
			HedgingClient hedgingClient, List<HedgingMetricsReporter> hedgingMetricsReportersList) {
		this.hedgingClient = hedgingClient;
		this.hedgingMetricsReporterList = hedgingMetricsReportersList;
	}

	@Override
	@NonNull
	public Mono<ClientResponse> filter(@NonNull ClientRequest request, ExchangeFunction next) {
		Duration delay = hedgingClient.getDelayBeforeHedging(request);
		int numHedges = numberOfHedgedRequestsDelayAware(request, hedgingClient, delay);
		return withSingleMetricsReporting(request, next.exchange(request), null)
				.mergeWith(
						Flux.range(1, numHedges)
								.delayElements(delay)
								.flatMap(hedgeNumber -> withSingleMetricsReporting(request, next.exchange(request), hedgeNumber)
								.onErrorResume(throwable -> {
									if (log.isDebugEnabled()) {
										log.debug("Hedged request " + hedgeNumber + " to " + request.url() + " failed", throwable);
									}
									return Mono.empty();
								}))
				)
				.next();
	}

	private int numberOfHedgedRequestsDelayAware(
			ClientRequest request,
			HedgingClient hedgingClient,
			Duration delay
	) {
		if (!hedgingClient.shouldHedge(request)) {
			return 0;
		}
		else if (delay.isNegative()) {
			return 0;
		}
		else {
			return hedgingClient.getNumberOfHedgedRequests(request);
		}
	}

	private Mono<ClientResponse> withSingleMetricsReporting(
			ClientRequest request,
			Mono<ClientResponse> response,
			@Nullable Integer hedgeNumber
	) {
		return response
				.elapsed()
				.doOnSuccess(tuple -> {
					hedgingClient.record(request, tuple.getT2(), tuple.getT1(), hedgeNumber);
					hedgingMetricsReporterList.forEach(reporter -> reporter.record(request, tuple.getT2(), tuple.getT1(), hedgeNumber));
				})
				.map(Tuple2::getT2);
	}
}
