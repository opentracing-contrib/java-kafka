package io.opentracing.contrib.kafka;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.tag.Tags;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.springframework.kafka.test.rule.KafkaEmbedded;
import org.springframework.kafka.test.utils.KafkaTestUtils;

public class TracingKafkaTest {

  @ClassRule
  public static KafkaEmbedded embeddedKafka = new KafkaEmbedded(2, true, 2, "messages");
  private MockTracer mockTracer = new MockTracer(MockTracer.Propagator.TEXT_MAP);

  @Before
  public void before() throws Exception {
    mockTracer.reset();
  }

  @Test
  public void test() throws Exception {
    Map<String, Object> senderProps = KafkaTestUtils.producerProps(embeddedKafka);
    TracingKafkaProducer<Integer, String> producer = new TracingKafkaProducer<>(senderProps, mockTracer);

    ProducerRecord<Integer, String> record = new ProducerRecord<>("messages", 1, "test");
    // Send 1
    producer.send(record);

    // Send 2
    producer.send(record, new Callback() {
      @Override
      public void onCompletion(RecordMetadata metadata, Exception exception) {
        assertEquals("messages", metadata.topic());
      }
    });

    final CountDownLatch latch = new CountDownLatch(2);

    createConsumer(latch, 1);

    producer.close();

    List<MockSpan> mockSpans = mockTracer.finishedSpans();
    assertEquals(4, mockSpans.size());
    checkSpans(mockSpans);
    assertNull(mockTracer.activeSpan());
  }

  private void createConsumer(final CountDownLatch latch, final Integer key)
      throws InterruptedException {
    ExecutorService executorService = Executors.newSingleThreadExecutor();

    final Map<String, Object> consumerProps = KafkaTestUtils
        .consumerProps("sampleRawConsumer", "false", embeddedKafka);
    consumerProps.put("auto.offset.reset", "earliest");

    executorService.execute(new Runnable() {
      @Override
      public void run() {
        TracingKafkaConsumer<Integer, String> kafkaConsumer = new TracingKafkaConsumer<>(consumerProps, mockTracer);

        kafkaConsumer.subscribe(Collections.singletonList("messages"));

        while (latch.getCount() > 0) {
          ConsumerRecords<Integer, String> records = kafkaConsumer.poll(100);
          for (ConsumerRecord<Integer, String> record : records) {
            assertEquals("test", record.value());
            if (key != null) {
              assertEquals(key, record.key());
            }
            kafkaConsumer.commitSync();
            latch.countDown();
          }
        }
        kafkaConsumer.close();
      }
    });

    assertTrue(latch.await(30, TimeUnit.SECONDS));

  }

  private void checkSpans(List<MockSpan> mockSpans) {
    for (MockSpan mockSpan : mockSpans) {
      assertEquals(Tags.SPAN_KIND_CLIENT, mockSpan.tags().get(Tags.SPAN_KIND.getKey()));
      assertEquals(SpanDecorator.COMPONENT_NAME, mockSpan.tags().get(Tags.COMPONENT.getKey()));
      assertEquals(0, mockSpan.generatedErrors().size());
      String operationName = mockSpan.operationName();
      assertTrue(operationName.equals("send")
          || operationName.equals("receive"));
    }
  }

}
