#!/usr/bin/env bash

set -Eeuo pipefail

readonly JUDGING_CONTRACT_COMMIT="c56bc7b"
repository_root="$(pwd -P)"
readonly repository_root

[[ -x ./mvnw ]] || {
  printf 'ERROR: run this script from croj-backend root\n' >&2
  exit 1
}
if [[ -z ${JUDGING_REPOSITORY:-} ]]; then
  for candidate in \
    "$repository_root/../croj-judging-server" \
    "$repository_root/../../repos/croj-judging-server"; do
    if [[ -f "$candidate/go.mod" ]]; then
      JUDGING_REPOSITORY="$candidate"
      break
    fi
  done
fi
readonly JUDGING_REPOSITORY="${JUDGING_REPOSITORY:-}"
[[ -n "$JUDGING_REPOSITORY" && -f "$JUDGING_REPOSITORY/go.mod" ]] || {
  printf 'ERROR: JUDGING_REPOSITORY must point to croj-judging-server\n' >&2
  exit 1
}
git -C "$JUDGING_REPOSITORY" cat-file -e "${JUDGING_CONTRACT_COMMIT}^{commit}" || {
  printf 'ERROR: judging contract commit %s is unavailable\n' "$JUDGING_CONTRACT_COMMIT" >&2
  exit 1
}

contract_dir="$(mktemp -d)"
cleanup() {
  rm -rf "$contract_dir"
}
trap cleanup EXIT INT TERM

readonly v1_archive="$contract_dir/backend-v1.zip"
readonly v2_archive="$contract_dir/backend-v2.zip"
readonly judging_copy="$contract_dir/judging"

./mvnw --batch-mode --no-transfer-progress \
  -Dtest=TestBundleContractExportTest,TestBundleV2ContractExportTest \
  -Dcroj.contract.output="$v1_archive" \
  -Dcroj.contract.v2.output="$v2_archive" \
  test

mkdir -p "$judging_copy"
git -C "$JUDGING_REPOSITORY" archive "$JUDGING_CONTRACT_COMMIT" \
  | tar -xf - -C "$judging_copy"
cat >"$judging_copy/internal/bundle/backend_v2_external_contract_test.go" <<'GO_TEST'
package bundle

import (
	"os"
	"testing"
)

func TestBackendProducedManifestV2Archive(t *testing.T) {
	path := os.Getenv("CROJ_BACKEND_TEST_BUNDLE_V2")
	if path == "" {
		t.Fatal("CROJ_BACKEND_TEST_BUNDLE_V2 is required")
	}
	manifest, canonical, err := InspectArchive(path, DefaultArchiveLimits())
	if err != nil {
		t.Fatalf("InspectArchive backend v2: %v", err)
	}
	if manifest.SchemaVersion != 2 || manifest.JudgeMode != JudgeModeOI ||
		manifest.Checker != CheckerSpecial || manifest.TotalScore == nil ||
		*manifest.TotalScore != 100 || len(manifest.Cases) != 2 {
		t.Fatalf("backend v2 manifest changed: %+v", manifest)
	}
	artifact, err := OpenArchive(path, canonical, DefaultArchiveLimits())
	if err != nil {
		t.Fatalf("OpenArchive backend v2: %v", err)
	}
	defer artifact.Close()
	source, err := artifact.ReadSpecialJudge()
	if err != nil || source == "" {
		t.Fatalf("ReadSpecialJudge backend v2: %q %v", source, err)
	}
}
GO_TEST

(
  cd "$judging_copy"
  CROJ_BACKEND_TEST_BUNDLE_V1="$v1_archive" \
  CROJ_BACKEND_TEST_BUNDLE_V2="$v2_archive" \
  GOCACHE="${GOCACHE:-$repository_root/.cache/go-build}" \
  GOPROXY="${GOPROXY:-https://proxy.golang.org,direct}" \
    go test -count=1 ./internal/bundle
)

printf 'Backend v1/v2 TestBundle producer is compatible with Judging consumer.\n'
