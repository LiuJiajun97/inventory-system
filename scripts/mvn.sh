#!/usr/bin/env bash
# Maven 包装脚本:本机 bash 下直接 `mvn` 因 MSYS 路径 glob 问题报
# ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher。
# 本脚本用 Windows 原生路径手动拼 classpath 调用,功能等价于 mvn。
# 用法: bash scripts/mvn.sh [maven 参数...]
set -e

JAVA_HOME_WIN="D:\\DevTools\\jdk-21.0.10"
MAVEN_HOME_WIN="D:\\apache-maven-3.6.3"
JAVA="${JAVA_HOME_WIN}/bin/java"
CLASSWORLDS="${MAVEN_HOME_WIN}/boot/plexus-classworlds-2.6.0.jar"

# 定位项目根(脚本在 scripts/ 下)
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PROJECT_DIR_WIN="$(cygpath -w -m "$PROJECT_DIR")"

exec "$JAVA" \
  -classpath "$CLASSWORLDS" \
  "-Dmaven.home=${MAVEN_HOME_WIN}" \
  "-Dlibrary.jansi.path=${MAVEN_HOME_WIN}\\lib\\jansi-native" \
  "-Dclassworlds.conf=${MAVEN_HOME_WIN}\\bin\\m2.conf" \
  "-Dmaven.multiModuleProjectDirectory=${PROJECT_DIR_WIN}" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
