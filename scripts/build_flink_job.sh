#!/bin/bash

# 构建Flink作业JAR文件

set -e

echo "Building Flink job..."

# 进入Flink作业目录
cd flink_jobs

# 清理之前的构建
mvn clean

# 编译和打包
mvn package

# 检查JAR文件是否生成
if [ -f "target/*.jar" ]; then
    echo "Flink job built successfully!"
    ls -la target/*.jar
else
    echo "Error: JAR file not found!"
    exit 1
fi

echo "You can now submit the job to Flink cluster."