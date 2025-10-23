# 使用一个精简的 Python 镜像作为基础
FROM python:3.9-slim

# 设置工作目录，后续的 COPY 和 CMD 都在这个目录下执行
WORKDIR /app

# 1. 复制【仓库根目录】下的 requirements.txt 到镜像中
COPY requirements.txt ./

# 2. 安装所有依赖
#    --no-cache-dir 选项可以减小镜像体积
RUN pip install --no-cache-dir -r requirements.txt

# 3. 复制【整个 mock_data 文件夹】到镜像的 /app/mock_data 目录下
COPY mock_data/ ./mock_data/

# 4. 复制【整个 test_data 文件夹】到镜像的 /app/test_data 目录下
#    这样脚本就能在容器内部访问到它需要的数据文件了
COPY test_data/ ./test_data/

# 5. 复制【整个 mykafka 文件夹】到镜像的 /app/mykafka 目录下
#    这样脚本就能在容器内部访问到它需要的util了
COPY mykafka/ ./mykafka/

# 6. 设置默认的启动命令
#    执行位于 /app/mock_data/ 目录下的 user_actions_generator.py 脚本
CMD ["python", "mock_data/user_actions_generator.py"]
