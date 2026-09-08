#!/bin/sh

set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec mvn -f "$project_dir/backend/pom.xml" "$@"
