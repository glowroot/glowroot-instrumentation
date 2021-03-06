/**
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.instrumentation.camel;

import java.io.Serializable;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.instrumentation.test.harness.AppUnderTest;
import org.glowroot.instrumentation.test.harness.Container;
import org.glowroot.instrumentation.test.harness.Containers;
import org.glowroot.instrumentation.test.harness.IncomingSpan;
import org.glowroot.instrumentation.test.harness.LocalSpan;
import org.glowroot.instrumentation.test.harness.Span;
import org.glowroot.instrumentation.test.harness.TestSpans;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

public class CamelIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.resetAfterEachTest();
    }

    @Test
    public void shouldRoute() throws Exception {
        // when
        IncomingSpan incomingSpan = container.execute(Route.class);

        // then
        Iterator<Span> i = incomingSpan.childSpans().iterator();

        LocalSpan localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        localSpan = (LocalSpan) i.next();
        assertThat(localSpan.message()).isEqualTo("test local span");
        assertThat(localSpan.childSpans()).isEmpty();

        assertThat(i.hasNext()).isFalse();
    }

    public static class Route implements AppUnderTest {

        private static CountDownLatch latch;

        @Override
        public void executeApp(Serializable... args) throws Exception {

            latch = new CountDownLatch(1);

            CamelContext context = new DefaultCamelContext();
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("file:src/test/data?noop=true")
                            .process(new Processor() {
                                @Override
                                public void process(Exchange exchange) throws Exception {
                                    TestSpans.createLocalSpan();
                                    String body = exchange.getIn().getBody(String.class);
                                    exchange.getIn().setBody(body + ".");
                                }
                            })
                            .process(new Processor() {
                                @Override
                                public void process(Exchange exchange) throws Exception {
                                    TestSpans.createLocalSpan();
                                    String body = exchange.getIn().getBody(String.class);
                                    exchange.getIn().setBody(body + ".");
                                }
                            })
                            .to("file://target/test");
                    from("file://target/test").bean(new SomeBean());
                }
            });
            context.start();
            if (!latch.await(10, SECONDS)) {
                throw new Exception("Message never received");
            }
            context.stop();
        }

        public static class SomeBean {

            public void someMethod(@SuppressWarnings("unused") String body) {
                latch.countDown();
            }
        }
    }
}
