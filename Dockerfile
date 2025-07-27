# 从自己构建的镜像中获取
# FROM my-flink-hadoop-base:1.0

# 1. 直接從官方的、乾淨的 Flink 鏡像開始
FROM apache/flink:1.17.2-scala_2.12

# 复制你的 flink_lib 到 /opt/flink/lib
COPY flink_lib/ /opt/flink/plugins
