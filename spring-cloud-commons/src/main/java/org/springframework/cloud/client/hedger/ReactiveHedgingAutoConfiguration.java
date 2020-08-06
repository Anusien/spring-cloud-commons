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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.MethodMetadata;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * @author Kevin Binswanger
 */
@Configuration
@ConditionalOnClass(WebClient.class)
public class ReactiveHedgingAutoConfiguration implements BeanFactoryAware, BeanPostProcessor {
	@Nullable
	private BeanFactory beanFactory;

	@Nullable
	public Object postProcessBeforeInitialization(@NonNull Object bean, String beanName) throws BeansException {
		if (beanFactory == null || !(bean instanceof WebClient.Builder)) {
			return bean;
		}
		Hedged hedged = lookupAnnotation(beanName);
		if (hedged == null) {
			return bean;
		}

		WebClient.Builder builder = (WebClient.Builder) bean;

		HedgingClient hedgingClient = beanFactory.getBean(hedged.interceptor(), HedgingClient.class);
		List<HedgingMetricsReporter> reporters = Arrays.stream(hedged.metricReporters())
				.map(metricReporter -> beanFactory.getBean(metricReporter, HedgingMetricsReporter.class))
				.collect(Collectors.toList());

		HedgedRequestsExchangeFilterFunction hedgedFilterFunction = new HedgedRequestsExchangeFilterFunction(hedgingClient,
				reporters);
		builder.filter(hedgedFilterFunction);

		return builder;
	}

	private static String HEDGED_ANNOTATION_TYPE = Hedged.class.getName();
	@Nullable
	private Hedged lookupAnnotation(String beanName) {
		if (beanFactory == null) {
			return null;
		}

		BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
		BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
		if (beanDefinition instanceof AnnotatedBeanDefinition) {
			if (beanDefinition.getSource() instanceof MethodMetadata) {
				MethodMetadata beanMethod = (MethodMetadata) beanDefinition.getSource();
				if (beanMethod.isAnnotated(HEDGED_ANNOTATION_TYPE)) {
					Method method;
					try {
						//noinspection OptionalGetWithoutIsPresent
						method = Arrays.stream(Class.forName(beanMethod.getDeclaringClassName()).getDeclaredMethods())
								.filter(m -> m.getName().equals(beanMethod.getMethodName()) && m.getReturnType().getName().equals(beanMethod.getReturnTypeName()))
								.findFirst().get();
					}
					catch (ClassNotFoundException | NoSuchElementException e) {
						throw new RuntimeException(String.format("Could not find class %s for bean with name %s.",
								beanMethod.getDeclaringClassName(), beanName), e);
					}
					Hedged hedged = AnnotatedElementUtils.getMergedAnnotation(method, Hedged.class);
					if (hedged == null) {
						throw new RuntimeException(String.format("Could not find @Hedged annotation for bean with name %s", beanName));
					}
					else {
						return hedged;
					}
				}
			}
		}
		return null;
	}

	@Override
	public void setBeanFactory(@Nullable BeanFactory beanFactory) throws BeansException {
		this.beanFactory = beanFactory;
	}
}
