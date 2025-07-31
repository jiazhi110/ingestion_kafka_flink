# 从自己构建的镜像中获取
# FROM my-flink-hadoop-base:1.0

# 1. 直接從官方的、乾淨的 Flink 鏡像開始
FROM apache/flink:1.17.2-scala_2.12

# 复制你的 flink_lib 到 /opt/flink/lib
COPY flink_lib/ /opt/flink/plugins

# 3. 🔥🔥🔥 最終的、最權威的修正：直接將 S3 設定寫入 Flink 的核心設定檔 🔥🔥🔥
#    這會強制 Flink 和它所有的元件都使用這些設定
RUN echo "" >> /opt/flink/conf/flink-conf.yaml && \
    echo "# S3 FileSystem Configuration for MinIO" >> /opt/flink/conf/flink-conf.yaml && \
    echo "s3.endpoint: http://minio:9000" >> /opt/flink/conf/flink-conf.yaml && \
    echo "s3.path.style.access: true" >> /opt/flink/conf/flink-conf.yaml && \
    echo "s3.access-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml && \
    echo "s3.secret-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml