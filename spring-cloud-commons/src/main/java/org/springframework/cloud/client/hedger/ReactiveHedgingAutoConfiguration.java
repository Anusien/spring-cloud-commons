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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Kevin Binswanger
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(WebClient.class)
public class ReactiveHedgingAutoConfiguration {
	@Bean
	@ConditionalOnBean(HedgingClient.class)
	HedgingWebClientBuilderPostProcessor hedgingWebClientBuilderPostProcessor(ApplicationContext context) {
		return new HedgingWebClientBuilderPostProcessor(context);
	}

	private static final class HedgingWebClientBuilderPostProcessor implements BeanFactoryAware, BeanPostProcessor {
		@Nullable
		private BeanFactory beanFactory;

		private final ApplicationContext applicationContext;

		private HedgingWebClientBuilderPostProcessor(ApplicationContext applicationContext) {
			this.applicationContext = applicationContext;
		}

		@Nullable
		public Object postProcessBeforeInitialization(@NonNull Object bean, String beanName) throws BeansException {
			if (beanFactory == null || !(bean instanceof WebClient.Builder)) {
				return bean;
			}
			Hedged hedged = applicationContext.findAnnotationOnBean(beanName, Hedged.class);
			if (hedged == null) {
				return bean;
			}

			WebClient.Builder builder = (WebClient.Builder) bean;

			HedgingClient hedgingClient = beanFactory.getBean(hedged.hedgingClient(), HedgingClient.class);
			List<HedgingMetricsReporter> reporters = Arrays.stream(hedged.metricsReporters())
					.map(metricReporter -> beanFactory.getBean(metricReporter, HedgingMetricsReporter.class))
					.collect(Collectors.toList());

			HedgedRequestsExchangeFilterFunction hedgedFilterFunction = new HedgedRequestsExchangeFilterFunction(hedgingClient,
					reporters);
			builder.filter(hedgedFilterFunction);

			return builder;
		}

		@Override
		public void setBeanFactory(@Nullable BeanFactory beanFactory) throws BeansException {
			this.beanFactory = beanFactory;
		}
	}
}
