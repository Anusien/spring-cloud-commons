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

import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Controls the parameters for a {@link WebClient.Builder} annotated with {@link Hedged}.
 * @author Kevin Binswanger
 */
public interface HedgerPolicy {
	boolean shouldHedge(ClientRequest request);

	int getNumberOfHedgedRequests(ClientRequest request);

	Duration getDelayBeforeHedging(ClientRequest request);

	/**
	 * Similar to {@link HedgerListener}, this method will be invoked on requests by the {@link WebClient.Builder}
	 * this policy attaches to. Will be called at most once for a hedged request sequence, on just the one request
	 * that we actually used.
	 * @param request The HTTP request.
	 * @param response The eventual HTTP response.
	 * @param elapsedMillis The number of milliseconds elapsed just for this attempt (ignores time spent waiting on
	 * 	 *                      previous attempts).
	 * @param hedgeNumber {null} for the original request, or which hedge attempt this is (starting at 1).
	 */
	void record(ClientRequest request,
				ClientResponse response,
				long elapsedMillis,
				@Nullable Integer hedgeNumber);
}
