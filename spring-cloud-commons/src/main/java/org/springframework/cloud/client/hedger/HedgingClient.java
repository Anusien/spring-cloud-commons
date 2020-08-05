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

public interface HedgingClient {
	boolean shouldHedge(ClientRequest request);

	int getNumberOfHedgedRequests(ClientRequest request);

	Duration getDelayBeforeHedging(ClientRequest request);

	void record(ClientRequest request,
				ClientResponse response,
				long elapsedMillis,
				@Nullable Integer hedgeNumber);
}
