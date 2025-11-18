# 从自己构建的镜像中获取
# FROM my-flink-hadoop-base:1.0

# 1. 直接從官方的、乾淨的 Flink 鏡像開始
FROM apache/flink:1.17.2-scala_2.12

# S3 插件（确保目录结构正确）
COPY flink_lib/flink-s3-fs-hadoop /opt/flink/plugins/flink-s3-fs-hadoop

# MSK IAM 认证
COPY flink_lib/aws-msk-iam-auth-1.1.1.jar /opt/flink/lib/aws-msk-iam-auth-1.1.1.jar

#为了ci-pipeline，因为本地用的是volume mount。
COPY flink_jobs/target/flink-uber-job-1.0-SNAPSHOT.jar /opt/flink/usrlib/my-app.jar

# 保证 lib 目录加载
ENV FLINK_CLASSPATH="/opt/flink/lib/*"

# 🔥 关键修正：为 Application Mode 的容器配置 TaskManager 的处理槽（Slot）和生产级的状态后端 (RocksDB)
#    这样，这一个容器就能同时扮演 JobManager 和 TaskManager 的角色，并拥有健壮的状态管理能力
# ┌──────────┬──────────────────────────────────┬────────────────────────────────────┐
# │ 特性     │ HashMapStateBackend (记在脑子里) │ RocksDBStateBackend (写在笔记本上) │
# ├──────────┼──────────────────────────────────┼────────────────────────────────────┤
# │ 存储位置 │ 内存 (JVM Heap)                  │ 本地磁盘                           │
# │ 容量     │ 小，受内存限制                   │ 大，受磁盘限制                     │
# │ 风险     │ 极易内存溢出导致程序崩溃         │ 稳定，无内存溢出风险               │
# │ 备份效率 │ 低，状态大时影响性能             │ 高，异步快照，不影响主流程         │
# │ 适用场景 │ 本地测试，状态极小的玩具应用     │ 所有生产环境                       │
# └──────────┴──────────────────────────────────┴────────────────────────────────────┘

# 解决方案：切换到生产级的状态后端 RocksDB

# 为了让您的应用变得健壮和可扩展，我们必须将状态后端从内存（HashMap）切换到磁盘（RocksDB）。

#  * RocksDBStateBackend 是 Flink 社区官方推荐的、用于生产环境的状态后端。
#  * 它会将状态存储在 TaskManager 的本地磁盘上，而不是内存里，从而支持非常大的状态。
#  * 它可以异步地、在不影响数据处理的情况下，将状态快照到您配置的 S3 Checkpoint 目录中，效率和稳定性都非常高。

# 最后改在 terraform flink ecs 中设置这两个参数了。因为它依然报这个错误，好用的时间只维持了一分钟：Caused by: java.util.concurrent.CompletionException: org.apache.flink.runtime.jobmanager.scheduler.NoResourceAvailableException: Could not acquire the minimum required resources.

# RUN echo "taskmanager.numberOfTaskSlots: 1" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "state.backend: rocksdb" >> /opt/flink/conf/flink-conf.yaml




# 3. 🔥🔥🔥 最終的、最權威的修正：直接將 S3 設定寫入 Flink 的核心設定檔 🔥🔥🔥   本地打开它，因为会需要它执行的参数，用参数连接到minio，其他方式不行。
#    這會強制 Flink 和它所有的元件都使用這些設定
# RUN echo "" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "# S3 FileSystem Configuration for MinIO" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.endpoint: http://minio:9000" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.path.style.access: true" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.access-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.secret-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml

# deprecate session mode!
# ENTRYPOINT ["/opt/flink/bin/flink", "run", "-c", "com.myjustin.flink.KafkaConsumerJob", "/opt/flink/usrlib/my-app.jar"]

# CMD ["flink", "run", "--jobmanager", "localhost:8081", "-c", "com.myjustin.flink.KafkaConsumerJob", "/opt/flink/usrlib/KafkaConsumerJob.jar"]

# 启用application mode ，使用 standalone-job 这种方式仅仅只放在一个容器中即可。 文档：https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/docker/
CMD [ \
    "standalone-job", \
    "--job-classname", "com.myjustin.flink.KafkaConsumerJob", \
    "-Dfs.s3.path.style.access=true" \
]
