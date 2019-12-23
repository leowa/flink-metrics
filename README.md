# flink example

## How to run example:

- 首先要启动单机版的flink cluster: `start-cluster.sh`, 在我的mac上，对应的位置是：`/usr/local/Cellar/apache-flink/1.9.1/libexec/bin`。 启动之后，在本地上可以通过`8081`端口来访问flink的UI

- 对于测试streaming的wordcount例子，自动service nc -lk 9999

- 运行类似`flink run target/flink-metrics-1.0-SNAPSHOT.jar`的命令来启动job

- 为了能够正常显示输出，我换成了`datastream.writeAsText`
