/*
 * Copyright 2017-2020 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.opentracing.contrib.kafka.spring;

import io.opentracing.mock.MockTracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.util.Map;

import static io.opentracing.contrib.kafka.spring.TracingSpringKafkaTest.embeddedKafka;

@Configuration
@EnableKafka
@ComponentScan
@EnableAspectJAutoProxy
public class TestConfiguration {

  @Bean
  public MockTracer tracer() {
    return new MockTracer();
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<Integer, String>
  kafkaListenerContainerFactory() {
    ConcurrentKafkaListenerContainerFactory<Integer, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory());
    return factory;
  }

  @Bean
  public ConsumerFactory<Integer, String> consumerFactory() {
    final Map<String, Object> consumerProps = KafkaTestUtils
        .consumerProps("sampleRawConsumer", "false", embeddedKafka.getEmbeddedKafka());
    consumerProps.put("auto.offset.reset", "earliest");

    return new TracingConsumerFactory<>(new DefaultKafkaConsumerFactory<>(consumerProps), tracer());
  }


  @Bean
  public ProducerFactory<Integer, String> producerFactory() {
    return new TracingProducerFactory<>(new DefaultKafkaProducerFactory<>(
        KafkaTestUtils.producerProps(embeddedKafka.getEmbeddedKafka())), tracer());
  }

  @Bean
  public KafkaTemplate<Integer, String> kafkaTemplate() {
    return new KafkaTemplate<>(producerFactory());
  }

  @Bean
  public TracingKafkaAspect tracingKafkaAspect() {
    return new TracingKafkaAspect(tracer());
  }
}
