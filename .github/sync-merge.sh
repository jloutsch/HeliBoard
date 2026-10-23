#!/bin/bash
# Used by .github/workflows/sync-upstream.yml: merges upstream HeliBoard main into the checked-out commit.
# Writes changed=true and tree=<merged tree> to $GITHUB_OUTPUT, or problem.txt on a conflict.
git config user.name "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git fetch https://github.com/Helium314/HeliBoard.git main || exit 1
if git merge-base --is-ancestor FETCH_HEAD HEAD; then
  echo "already up to date"
  exit 0
fi
if ! git merge --no-edit -m "Merge upstream HeliBoard main" FETCH_HEAD; then
  { echo "Merge conflicts in:"; git diff --name-only --diff-filter=U | sed 's/^/- /'; } > problem.txt
  exit 1
fi
echo "changed=true" >> "$GITHUB_OUTPUT"
echo "tree=$(git rev-parse 'HEAD^{tree}')" >> "$GITHUB_OUTPUT"
