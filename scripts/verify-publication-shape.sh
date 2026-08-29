#!/usr/bin/env bash

set -euo pipefail

repository="$1"
platform_group="$2"
platform_version="$3"
implementation_artifact="magic-android-platform-gradle-plugin"
plugin_ids=(
  "io.github.magic-xu.magic-android-application"
  "io.github.magic-xu.magic-android-compose"
  "io.github.magic-xu.magic-android-pulse"
  "io.github.magic-xu.magic-android-quality"
)

fail() {
  echo "Publication shape verification failed: $1" >&2
  exit 1
}

count_files() {
  find "$repository" -type f "$@" -print | wc -l | tr -d '[:space:]'
}

[[ -d "$repository" ]] || fail "repository does not exist: $repository"

implementation_jar_count="$(count_files -name '*.jar' ! -name '*-sources.jar')"
source_jar_count="$(count_files -name '*-sources.jar')"
pom_count="$(count_files -name '*.pom')"

[[ "$implementation_jar_count" == "1" ]] ||
  fail "expected 1 implementation JAR, found $implementation_jar_count"
[[ "$source_jar_count" == "1" ]] ||
  fail "expected 1 sources JAR, found $source_jar_count"
[[ "$pom_count" == "5" ]] ||
  fail "expected 1 implementation POM and 4 marker POMs, found $pom_count POMs"

implementation_pom_count="$(count_files -name "${implementation_artifact}-*.pom")"
[[ "$implementation_pom_count" == "1" ]] ||
  fail "expected exactly 1 implementation POM, found $implementation_pom_count"

for plugin_id in "${plugin_ids[@]}"; do
  marker_artifact="${plugin_id}.gradle.plugin"
  marker_pom_count="$(count_files -name "${marker_artifact}-*.pom")"
  [[ "$marker_pom_count" == "1" ]] ||
    fail "expected exactly 1 marker POM for $plugin_id, found $marker_pom_count"

  marker_pom="$(find "$repository" -type f -name "${marker_artifact}-*.pom" -print -quit)"
  grep -Fq "<groupId>${platform_group}</groupId>" "$marker_pom" ||
    fail "$plugin_id marker does not depend on group $platform_group"
  grep -Fq "<artifactId>${implementation_artifact}</artifactId>" "$marker_pom" ||
    fail "$plugin_id marker does not depend on $implementation_artifact"
  grep -Fq "<version>${platform_version}</version>" "$marker_pom" ||
    fail "$plugin_id marker does not use platform version $platform_version"
done

echo "Verified 1 implementation JAR, 1 sources JAR, and 4 plugin marker POMs."
