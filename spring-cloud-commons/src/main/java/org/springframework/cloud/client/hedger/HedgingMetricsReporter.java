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

import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * Hedging could create multiple requests to a given service. Any monitoring tools put around the request will therefore
 * see a bunch of requests, many of which are superfluous. This is a way to get reporting just around which attempt
 * eventually succeeds.
 * @author Csaba Kos
 * @author Kevin Binswanger
 */
@FunctionalInterface
public interface HedgingMetricsReporter {
	/**
	 * Invoked by {@link HedgedRequestsExchangeFilterFunction} when an attempt actually succeeds.
	 * @param request The object for the request
	 * @param response The object for the response.
	 * @param elapsedMillis The number of milliseconds elapsed just for this attempt (ignores time spent waiting on
	 *                      previous attempts.
	 * @param hedgeNumber {null} for the original request, or which hedge attempt this is (starting at 1).
	 */
	void record(ClientRequest request,
				ClientResponse response,
				long elapsedMillis,
				@Nullable Integer hedgeNumber);
}