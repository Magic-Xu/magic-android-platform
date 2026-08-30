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

implementation_jar_count="$(
  count_files -name '*.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar'
)"
source_jar_count="$(count_files -name '*-sources.jar')"
javadoc_jar_count="$(count_files -name '*-javadoc.jar')"
pom_count="$(count_files -name '*.pom')"
module_metadata_count="$(count_files -name '*.module')"

[[ "$implementation_jar_count" == "1" ]] ||
  fail "expected 1 implementation JAR, found $implementation_jar_count"
[[ "$source_jar_count" == "1" ]] ||
  fail "expected 1 sources JAR, found $source_jar_count"
[[ "$javadoc_jar_count" == "1" ]] ||
  fail "expected 1 javadoc JAR, found $javadoc_jar_count"
[[ "$pom_count" == "5" ]] ||
  fail "expected 1 implementation POM and 4 marker POMs, found $pom_count POMs"
[[ "$module_metadata_count" == "1" ]] ||
  fail "expected 1 Gradle module metadata file, found $module_metadata_count"

implementation_pom_count="$(count_files -name "${implementation_artifact}-*.pom")"
[[ "$implementation_pom_count" == "1" ]] ||
  fail "expected exactly 1 implementation POM, found $implementation_pom_count"
implementation_pom="$(
  find "$repository" -type f -name "${implementation_artifact}-*.pom" -print -quit
)"
while IFS= read -r pom_file; do
  for required_section in "<name>" "<description>" "<url>" "<licenses>" "<developers>" "<scm>"; do
    grep -Fq "$required_section" "$pom_file" ||
      fail "${pom_file#$repository/} is missing $required_section metadata"
  done
done < <(find "$repository" -type f -name '*.pom' -print)

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

if [[ "${4:-}" == "--require-signatures" ]]; then
  signable_file_count=0
  while IFS= read -r artifact; do
    signable_file_count=$((signable_file_count + 1))
    [[ -f "${artifact}.asc" ]] ||
      fail "missing signature for ${artifact#$repository/}"
  done < <(
    find "$repository" -type f \
      \( -name '*.jar' -o -name '*.module' -o -name '*.pom' \) \
      ! -name '*.asc' \
      -print
  )
  [[ "$signable_file_count" == "9" ]] ||
    fail "expected 9 signable publication files, found $signable_file_count"
fi

echo "Verified the implementation JAR, sources, javadocs, module metadata, POM, and 4 plugin markers."
