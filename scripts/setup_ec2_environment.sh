#!/bin/bash

# EC2环境配置脚本
# 用于在Amazon Linux 2 EC2实例上安装和配置项目所需的所有依赖

set -e  # 遇到错误时退出

echo "开始配置EC2环境..."

# 更新系统包
echo "更新系统包..."
sudo yum update -y

# 1. 安装Java (JDK 11)
echo "安装Java (JDK 11)..."
sudo yum install -y java-11-amazon-corretto

# 验证Java安装
java -version

# 2. 安装Python 3.9和pip
echo "安装Python 3.9和pip..."
sudo yum install -y python3.9 python3.9-pip

# 验证Python安装
python3.9 --version
pip3.9 --version

# 3. 安装项目Python依赖
echo "安装项目Python依赖..."
cd /home/ec2-user/projects/ingestion_kafka_flink
pip3 install -r requirements.txt

# 验证AWS CLI安装
aws --version

echo "EC2环境配置完成!"
echo "请手动配置AWS凭证:"
echo "运行 'aws configure' 来设置您的AWS访问密钥和秘密密钥"