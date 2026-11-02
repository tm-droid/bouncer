---
name: deploy-verify
description: Commit, push, deploy, and verify the change is live
---
1. Run tests/build locally first
2. git add -A && git commit -m "$ARGUMENT"
3. git push (if hangs >30s, use gh api fallback)
4. Wait for deploy, then curl the affected URL and grep for the expected change
5. Report the verified diff, not just the commit SHA
