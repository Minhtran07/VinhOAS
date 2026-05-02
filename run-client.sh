#!/usr/bin/env bash
# Build & chạy client qua javafx-maven-plugin. Đây là cách KHUYẾN DÙNG
# vì JavaFX cần native modules được Maven set sẵn trên module-path,
# `java -jar` thường không đủ.
set -e
cd "$(dirname "$0")"

echo "[1/2] Building client (mvn package -DskipTests)..."
mvn -pl shared,client -am -DskipTests package

echo "[2/2] Starting JavaFX client via mvn javafx:run..."
mvn -pl client javafx:run
