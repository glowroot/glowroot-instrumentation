/*
 * Copyright 2016-2019 the original author or authors.
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
package controllers;

import play.mvc.Controller;
import play.mvc.Result;

import org.glowroot.instrumentation.test.harness.TestSpans;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

public class StreamController extends Controller {

    public static Result stream() {
        Chunks<String> chunks = new StringChunks() {
            public void onReady(Chunks.Out<String> out) {
                registerOutChannelSomewhere(out);
            }
        };
        return ok(chunks);
    }

    public static void registerOutChannelSomewhere(Chunks.Out<String> out) {
        out.write("kiki");
        try {
            MILLISECONDS.sleep(100);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        out.write("foo");
        out.write("bar");
        TestSpans.createLocalSpan();
        out.close();
    }
}
