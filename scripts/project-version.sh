#!/bin/sh
set -eu

project_version="$(
  sed -n '
    /<groupId>com\.zephyr<\/groupId>/ {
      n
      /<artifactId>croj<\/artifactId>/ {
        n
        s/^[[:space:]]*<version>\([^<][^<]*\)<\/version>[[:space:]]*$/\1/p
      }
    }
  ' pom.xml
)"

test -n "$project_version"
printf '%s\n' "$project_version"
