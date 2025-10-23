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


# 3. 🔥🔥🔥 最終的、最權威的修正：直接將 S3 設定寫入 Flink 的核心設定檔 🔥🔥🔥   本地打开它，因为会需要它执行的参数，用参数连接到minio，其他方式不行。
#    這會強制 Flink 和它所有的元件都使用這些設定
# RUN echo "" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "# S3 FileSystem Configuration for MinIO" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.endpoint: http://minio:9000" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.path.style.access: true" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.access-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml && \
#     echo "s3.secret-key: minioadmin" >> /opt/flink/conf/flink-conf.yaml