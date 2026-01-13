# Use a minimal Python image as base
FROM python:3.9-slim

# Set working directory
WORKDIR /app

# 1. Copy requirements.txt from repository root
COPY requirements.txt ./

# 2. Install dependencies and configure AWS Root CA
#    --no-cache-dir reduces image size
#    Cleanup apt cache and temporary files to keep the image minimal
RUN apt-get update && apt-get install -y ca-certificates wget && \
    wget -O /usr/local/share/ca-certificates/AmazonRootCA1.crt https://www.amazontrust.com/repository/AmazonRootCA1.pem && \
    update-ca-certificates && \
    pip install --no-cache-dir -r requirements.txt && \
    apt-get purge -y --auto-remove wget && \
    rm -rf /var/lib/apt/lists/*

# 3. Create non-root user for enhanced security
RUN useradd -m python-user
USER python-user

# 4. Copy source code modules
# Config directory is omitted as app uses Cloud-Native SSM mode
COPY mykafka/ ./mykafka/
COPY mock_data/ ./mock_data/
COPY test_data/ ./test_data/

# 5. Ensure modules are discoverable
ENV PYTHONPATH=/app

# 6. Default startup command
CMD ["python", "-m", "mock_data.user_actions_generator"]
