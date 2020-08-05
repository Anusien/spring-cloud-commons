/*
 * Copyright 2012-2020 the original author or authors.
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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Kevin Binswanger
 */
@RunWith(MockitoJUnitRunner.class)
public class HedgedRequestsExchangeFilterFunctionTests {
	private HedgingClient hedgingClient;
	private ClientRequest clientRequest;
	private ExchangeFunction exchangeFunction;
	private HedgingMetricsReporter metricsReporter;

	@Before
	public void setup() {
		this.hedgingClient = mock(HedgingClient.class);
		this.clientRequest = mock(ClientRequest.class);
		this.exchangeFunction = mock(ExchangeFunction.class);
		this.metricsReporter = mock(HedgingMetricsReporter.class);
	}

	@After
	public void teardown() {
		this.hedgingClient = null;
		this.clientRequest = null;
		this.exchangeFunction = null;
		this.metricsReporter = null;
	}

	@Test
	public void noHedgesShouldNotHedge() {
		when(hedgingClient.shouldHedge(clientRequest)).thenReturn(false);
		when(hedgingClient.getDelayBeforeHedging(clientRequest)).thenReturn(Duration.of(100, ChronoUnit.SECONDS));
		when(exchangeFunction.exchange(clientRequest)).thenReturn(Mono.empty()); // to make post-success reporting work

		HedgedRequestsExchangeFilterFunction filter = new HedgedRequestsExchangeFilterFunction(hedgingClient, Collections
				.emptyList());
		filter.filter(clientRequest, exchangeFunction);

		verify(exchangeFunction, times(1)).exchange(clientRequest);
	}

	@Test
	public void negativeDelayShouldNotHedge() {
		when(hedgingClient.shouldHedge(clientRequest)).thenReturn(true);
		when(hedgingClient.getDelayBeforeHedging(clientRequest)).thenReturn(Duration.of(-1, ChronoUnit.SECONDS));
		when(exchangeFunction.exchange(clientRequest)).thenReturn(Mono.empty()); // to make post-success reporting work

		HedgedRequestsExchangeFilterFunction filter = new HedgedRequestsExchangeFilterFunction(hedgingClient, Collections
				.emptyList());
		filter.filter(clientRequest, exchangeFunction);

		verify(exchangeFunction, times(1)).exchange(clientRequest);
	}

	@Test
	public void correctlyReportMetrics() {
		when(hedgingClient.shouldHedge(clientRequest)).thenReturn(false);
		when(hedgingClient.getDelayBeforeHedging(clientRequest)).thenReturn(Duration.of(100, ChronoUnit.SECONDS));

		ClientResponse response = mock(ClientResponse.class);
		when(exchangeFunction.exchange(clientRequest)).thenReturn(Mono.just(response));

		HedgedRequestsExchangeFilterFunction filter = new HedgedRequestsExchangeFilterFunction(hedgingClient, Collections
				.singletonList(metricsReporter));
		Mono<ClientResponse> actual = filter.filter(clientRequest, exchangeFunction);

		then(actual.block()).isEqualTo(response);
		verify(hedgingClient).record(eq(clientRequest), eq(response), anyLong(), isNull());
		verify(metricsReporter).record(eq(clientRequest), eq(response), anyLong(), isNull());
	}

	@Test
	public void hedgeOnceButOriginalReturnsFirst() {
		when(hedgingClient.shouldHedge(clientRequest)).thenReturn(true);
		when(hedgingClient.getNumberOfHedgedRequests(clientRequest)).thenReturn(1);
		when(hedgingClient.getDelayBeforeHedging(clientRequest)).thenReturn(Duration.of(100, ChronoUnit.MILLIS));

		ClientResponse response = mock(ClientResponse.class);
		StepVerifier.withVirtualTime(() -> {
			AtomicInteger count = new AtomicInteger(0);
			Mono<ClientResponse> first = Mono.just(response).delayElement(Duration.ofMillis(200));
			Mono<ClientResponse> second = Mono.never();
			when(exchangeFunction.exchange(clientRequest)).then(invocation -> count.getAndIncrement() == 0 ? first : second);

			HedgedRequestsExchangeFilterFunction filter = new HedgedRequestsExchangeFilterFunction(hedgingClient, Collections
					.singletonList(metricsReporter));
			return filter.filter(clientRequest, exchangeFunction);
		})
				.expectSubscription()
				.expectNoEvent(Duration.of(200, ChronoUnit.MILLIS))
				.expectNext(response)
				.verifyComplete();

		verify(hedgingClient).record(clientRequest, response, 200, null);
		verify(metricsReporter).record(clientRequest, response, 200, null);
	}

	@Test
	public void hedgeOnceOriginalNeverReturns() {
		when(hedgingClient.shouldHedge(clientRequest)).thenReturn(true);
		when(hedgingClient.getNumberOfHedgedRequests(clientRequest)).thenReturn(1);
		when(hedgingClient.getDelayBeforeHedging(clientRequest)).thenReturn(Duration.ofMillis(100));

		ClientResponse response = mock(ClientResponse.class);
		StepVerifier.withVirtualTime(() -> {

			AtomicInteger count = new AtomicInteger(0);
			Mono<ClientResponse> first = Mono.never();
			Mono<ClientResponse> second = Mono.just(response).delayElement(Duration.ofMillis(150));
			when(exchangeFunction.exchange(clientRequest)).then(invocation -> count.getAndIncrement() == 0 ? first : second);

			HedgedRequestsExchangeFilterFunction filter = new HedgedRequestsExchangeFilterFunction(hedgingClient, Collections
					.singletonList(metricsReporter));
			return filter.filter(clientRequest, exchangeFunction);
		})
				.expectSubscription()
				.expectNoEvent(Duration.ofMillis(250))
				.expectNext(response)
				.verifyComplete();

		verify(hedgingClient).record(clientRequest, response, 150, 1);
		verify(metricsReporter).record(clientRequest, response, 150, 1);
	}
}
