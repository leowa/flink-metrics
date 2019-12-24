/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.flink.metrics;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.fs.FileSystem;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.source.RichSourceFunction;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.Collector;

import org.apache.flink.metrics.Counter;
import org.apache.flink.metrics.Histogram;
import org.apache.flink.runtime.metrics.DescriptiveStatisticsHistogram;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

/**
 * Skeleton for a Flink Streaming Job.
 *
 * <p>For a tutorial how to write a Flink streaming application, check the
 * tutorials and examples on the <a href="http://flink.apache.org/docs/stable/">Flink Website</a>.
 *
 * <p>To package your application into a JAR file for execution, run
 * 'mvn clean package' on the command line.
 *
 * <p>If you change the name of the main class (with the public static void main(String[] args))
 * method, change the respective entry in the POM.xml file (simply search for 'mainClass').
 */
public class WindowWordCountJob {

    public static void main(String[] args) throws Exception {
        // set up the streaming execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		DataStream<Tuple2<String, Integer>> dataStream = env
				.addSource(new WordSource())
				.flatMap(new Splitter())
				.keyBy(0)
				.timeWindow(Time.seconds(10))
				.sum(1);
        dataStream.writeAsText("/tmp/flink.out", FileSystem.WriteMode.OVERWRITE).setParallelism(1);
        dataStream.print().setParallelism(1);
        env.getConfig().setGlobalJobParameters(ParameterTool.fromArgs(args));
		env.execute("Window WordCount");
    }

    public static final class WordSource extends RichSourceFunction<String> {

        private static final long serialVersionUID = 1L;
        private transient Random random;
        private transient int waitMS; // wait milliseconds
        private transient boolean isCancel;
        private transient String[] lines;

        private transient Counter eventCounter;
        private transient Histogram valueHistogram;

        @Override
        public void open(Configuration parameters) throws Exception {
            super.open(parameters);
            this.waitMS = parameters.getInteger("interval", 3000);
            random = new Random();
            InputStream is = getClass().getClassLoader().getResourceAsStream("text.txt");
            Scanner s = new Scanner(is);
            ArrayList<String> raw = new ArrayList<>();
            while(s.hasNext()) {
                String line = s.nextLine();
                if(line.trim().length() > 0) {
                    raw.add(line);
                }
            }
            this.lines = raw.toArray(new String[]{});

            eventCounter = getRuntimeContext().getMetricGroup().counter("events");
            valueHistogram =
                    getRuntimeContext()
                            .getMetricGroup()
                            .histogram("value_histogram", new DescriptiveStatisticsHistogram(10_000_000));
        }

        @Override
        public void run(SourceContext<String> ctx) throws Exception {
            while (! isCancel) {
                eventCounter.inc();
                TimeUnit.MILLISECONDS.sleep(Math.max(100, random.nextInt(this.waitMS)));
                String text = this.lines[random.nextInt(this.lines.length - 1)];
                valueHistogram.update(text.length());
                ctx.collect(text);
            }
        }

        @Override
        public void cancel() {
            isCancel = true;
        }
    }

    public static class Splitter implements FlatMapFunction<String, Tuple2<String, Integer>> {

        @Override
        public void flatMap(String sentence, Collector<Tuple2<String, Integer>> out) throws Exception {
            for (String word : sentence.split(" ")) {
                out.collect(new Tuple2<>(word, 1));
            }
        }
    }
}
