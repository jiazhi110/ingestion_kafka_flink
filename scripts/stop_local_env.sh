#!/bin/bash

# Stop local development environment
# This script stops all services and cleans up resources

set -e  # Exit on any error

echo "Stopping local development environment..."

# Navigate to project root
cd "$(dirname "$0")/.."

# Stop services with Docker Compose
echo "Stopping Docker services..."
docker-compose down

# Optional: Remove unused Docker volumes
echo "Cleaning up Docker volumes..."
docker volume prune -f

# Optional: Remove unused Docker networks
echo "Cleaning up Docker networks..."
docker network prune -f

echo "Local development environment stopped successfully!"

echo ""
echo "To remove all data and start fresh:"
echo "  1. Delete minio_data directory: rm -rf minio_data"
echo "  2. Delete local_output directory: rm -rf local_output"
echo "  3. Delete Flink checkpoints: rm -rf /tmp/flink"