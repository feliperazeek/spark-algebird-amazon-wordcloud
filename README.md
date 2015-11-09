# Amazon Word Cloud

<img src="http://i.imgur.com/dcxuF4I.png">

## Dependencies

* Redis running on port `6379`.
* SBT.

## Routes

`GET /`

D3-based visualization for Word Cloud, page does full refreshes every few seconds. Ideally we would use something like WebSockets or Comet to push realtime changes but I didn't wanna spend time on the frontend.

`GET /wordcloud.json`

RESTful API endpoint to get the data needed for the Word Cloud visualization, mainly a list of words and their counts in JSON format.

`POST /submit?url=<url>`

Endpoint that returns http status 200 if the url is in valid format and it's not a duplicate. It returns http status 400 if the url has been submitted already.

## How to run the application

```bash
redis-server --port 6379 # on a different terminal
ulimit -S -n 10000 # To avoid too many open files
sbt stage
./target/universal/stage/bin/amazon-wordcloud -J-Xmx1000m -J-Xms1000m -Dhttp.port=9000
```

## How to run simulation

```bash
cd bin
bash simulateRequests.sh localhost 9000 url
```

## Design Decisions

* Duplication check is happening on Redis, through a `Bloom Filter`, not on Spark. I made that decision because it allows the submit endpoint to provide an imediate feedback when a duplicate url is submitted (http status 400). It also allows the system not to store these duplicates downstream (Kafka, HDFS, Spark checkpoint), most likely replicated in multiple replicas. So better user experience and cheaper.

* Kafka is a good option for a reliable Spark receiver in real life but kept it out for the sake of simplicity of this demo. 

* In this application, I am using an Akka actor as the receiver for Spark (see [app/spark/RouterActor.scala](https://github.com/feliperazeek/spark-algebird-amazon-wordcloud/blob/master/app/spark/RouterActor.scala)), events are being published through Akka's event bus. I am not using actors on the read API endpoints because actors are untyped and they also require more boilerplate than standard Scala futures. For read endpoints, Scala futures are simpler and more elegant.

* In a real application, we would be using Avro for its well-defined schema, or something similar, to share data across the system, we are not doing that because in this demo everything is happening in a single vm.

* Using Spark Streaming job (see [app/spark/WordCloud.scala](https://github.com/feliperazeek/spark-algebird-amazon-wordcloud/blob/master/app/spark/WordCloud.scala)) to extract product description from url (see [app/crawler/Amazon.scala](https://github.com/feliperazeek/spark-algebird-amazon-wordcloud/blob/master/app/crawler/Amazon.scala)), tokenize/filter-stop-words/stem (see [app/common/nlp.scala](https://github.com/feliperazeek/spark-algebird-amazon-wordcloud/blob/master/app/common/nlp.scala)).
  * Since the system needs to be durable, we are using Spark's checkpoint and `updateStateByKey`.
  * For the sake of simplicity, I am running Spark in local mode and getting launched from inside the app, not through `spark-submit` in cluster mode.
  * Also for the sake of simplicity `spark.SparkConfig` (see [app/spark/SparkConfig.scala](https://github.com/feliperazeek/spark-algebird-amazon-wordcloud/blob/master/app/spark/SparkConfig.scala)) was created, it wouldn't really exist in a real application.
  * Using Kryo to serialize data in Spark, click [here](http://spark.apache.org/docs/latest/tuning.html#data-serialization) for more details.
  * Even though it's a good idea in real life, not using Spark's [WAL](https://databricks.com/blog/2015/01/15/improved-driver-fault-tolerance-and-zero-data-loss-in-spark-streaming.html) since we don't have a reliable receiver.
  * In a nutshell, Spark helps with the durability aspect as well as being able to handle order of magnitude larger workloads on the data side. It's also nice to use the same tech that's used on the day-to-day, even though it's not ideal to run it in local mode, launched from a webapp. If we didn't need to scale to orders of magnitude larger workloads, a much simpler setup without Spark would be fine.

* Using an actor as the custom receiver for the job, again just because it's a demo, I am using Spark's internal class `org.apache.sparkSparkEnv` to get Spark's actor system. Ideally we would use a reliable receiver such as Kafka which also gives us the ability to replay events.

* Word Cloud is stored on `Redis`, it provides the durability needed for the Word Cloud itself ("restarting the service should go display the word cloud of all the links received up to this point") and very fast and simple read access which is a requirement from an UX prespective. Twitter's Storehaus also makes writing pretty easy, I chose a `Redis` sorted set because it gives the frontend/api the ability to query just a subset of the top-k persisted to `Redis`. Since top-k should be small enough to fit in memory, any mergeable store provided by Storehaus would be fine. It's worth saying that the data stored on  `Redis` is not used on any calculation, that's stored on Spark's checkpoint through `updateStateByKey`.

* Since estimation is fine, I wanted to use a Count-Min sketch data structure, Algebird provides a lot of options that can be integrated into a Spark job very elegantly, such as `TopNCMS`, `TopPctCMS`, `PriorityQueue`, `SpaceSaver`, `SketchMap`).
  * Not using `PriorityQueue` because I needed the frequency.
  * `SketchMap` is slower than `TopCMS` (`0.3k writes/s` compared to `65k writes/s`).
  * `TopNCMS` computes heavy hitters incorrectly when combining instances with `++`, see [issue](https://github.com/twitter/algebird/issues/353) for more details. But it's potentially a pretty good option.
  * Since `TopPctCMS` doesn't have the same issue that `TopNCMS` has, that's what I decided to go with.
  * Here's you can find some really good information on the different alternatives:
    * https://github.com/twitter/algebird/issues/360
    * http://twitter.github.io/algebird/index.html#com.twitter.algebird.TopNCMSMonoid

* I wanted to use `ScalaNLP/Chalk` for tokenization and stemming but unfortunately they don't have a dependency available for 2.11. I didn't wanna spend too much time with it so I just ended up using `Apache OpenNLP`. I didn't use `SparkML` mainly because I didn't want to use DataFrames.

* I am using `Play Framework` because I knew I could easily add visualization of the Word Cloud through this [project](https://github.com/jasondavies/d3-cloud). It's always nice, but not always possible unfortunately, to visualize data in its final form to get a better feeling of the user experience. I really like the productivity aspect of it as well, a lot of things just come ready out of the box such as integration with `sbt-native-packager`, `typesafe-config`, etc. `Spray` would also be a fine option.

## Improvements/Brainstorm

* Unfortunately sometimes application deadlocks during shutdown (see https://issues.apache.org/jira/browse/SPARK-11104). Apparently it's a bug that will be fixed in the very next minor release (1.5.2). In that case `kill -9 <PID>` is required (shame shame shame :<).
* We could use circuit breaker to fail fast in case Redis is down for example.
* We could increase data locality if needed using a partitioner on Spark.

