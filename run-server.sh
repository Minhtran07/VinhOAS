#!/usr/bin/env bash
# Build & chạy server. Sau khi build server jar có manifest + bundle deps.
set -e
cd "$(dirname "$0")"

echo "[1/2] Building server (mvn package)..."
mvn -pl shared,server -am -DskipTests package

echo "[2/2] Starting AuctionServer..."
java -jar server/target/server-1.0-SNAPSHOT.jar
