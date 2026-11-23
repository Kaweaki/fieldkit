# Restoring the previous state

This branch is additive. Nothing in `main` was rewritten and the released
binary was not touched.

| | |
| --- | --- |
| Branch point | `ed025e4` (main, "Add the signed FieldKit 2.6 APK") |
| Branch | `v2` |
| Tag | `v2.7-rc1` |
| APK | `dist/FieldKit-2.6.apk`, unchanged, SHA-256 `d93c20c6e133485b609b853ae5d2df3beda2bbd77e370fd0bdfcd17edf560835` |
| Pushed | no — everything is local until you push |

## Go back to the released tree

```console
git switch main
```

Nothing else is needed: `main` never moved.

## Throw the branch away

```console
git switch main
git branch -D v2
git tag -d v2.7-rc1
```

Delete the tag too if you only want the branch gone:

```console
git tag -d v2.7-rc1
```

## Keep the branch, undo one file

```console
git restore --source=main -- README.md
git restore --source=main -- SKILL.md FIELDKIT-BUILD-PROCESS.txt
```

## Review before merging

```console
git diff main..v2 --stat
git diff main..v2 -- app/AndroidManifest.xml
git switch main
git merge --no-ff v2
```

If the merge turns out wrong and has not been pushed:

```console
git reset --hard ed025e4
```

If it has been pushed, use `git revert -m 1 <merge-commit>` instead; the
history stays intact.

## What cannot be restored from here

The 2.6 APK is the only artefact of the released build in this repository, and
it is untouched. The Java source behind it was never committed, so this branch
cannot rebuild 2.6 byte for byte — that is the first item on the 2.7 list, and
once it is in, `app/build.sh` plus the pinned build-tools version is the restore
path for every later release.
