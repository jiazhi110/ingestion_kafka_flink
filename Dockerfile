# --- Stage 1: Build Stage (Builder) ---
# Use Maven image to compile Java code
FROM maven:3.8.8-eclipse-temurin-11 AS builder
WORKDIR /app
COPY flink_jobs/pom.xml ./flink_jobs/
RUN mvn -f flink_jobs/pom.xml dependency:go-offline -B
COPY flink_jobs/src ./flink_jobs/src
RUN mvn -f flink_jobs/pom.xml clean package -DskipTests

# --- Stage 2: Runtime Stage (Runtime) ---
# 1. Start from official Flink image
FROM apache/flink:1.17.2-scala_2.12

# S3 Plugins
COPY flink_lib/flink-s3-fs-hadoop /opt/flink/plugins/flink-s3-fs-hadoop

# MSK IAM Authentication
COPY flink_lib/aws-msk-iam-auth-1.1.1.jar /opt/flink/lib/aws-msk-iam-auth-1.1.1.jar

# Application Jar from builder stage
COPY --from=builder /app/flink_jobs/target/flink-uber-job-1.0-SNAPSHOT.jar /opt/flink/usrlib/my-app.jar

# Ensure lib directory is loaded
ENV FLINK_CLASSPATH="/opt/flink/lib/*"

# --- State Backend Configuration ---
# Production best practice: Switch state backend from memory (HashMap) to disk (RocksDB)
# to support large-scale states and ensure stability.

# Production security practice: Switch to non-root user
USER flink

# deprecate session mode!
# ENTRYPOINT ["/opt/flink/bin/flink", "run", "-c", "com.myjustin.flink.KafkaConsumerJob", "/opt/flink/usrlib/my-app.jar"]

# CMD ["flink", "run", "--jobmanager", "localhost:8081", "-c", "com.myjustin.flink.KafkaConsumerJob", "/opt/flink/usrlib/KafkaConsumerJob.jar"]

# Enable application mode using standalone-job. This only needs to be in one container. 
# Docs: https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/docker/
# 启用application mode ，使用 standalone-job 这种方式仅仅只放在一个容器中即可。 文档：https://nightlies.apache.org/flink/flink-docs-stable/docs/deployment/resource-providers/standalone/docker/
CMD [ \
    "standalone-job", \
    "--job-classname", "com.myjustin.flink.KafkaConsumerJob", \
    "-Dfs.s3.path.style.access=true" \
]