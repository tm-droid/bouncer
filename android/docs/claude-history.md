# Claude history

- 2026-11-21: bouncer-archive publish blocked since 2026-11-04 by push protection (row id false-positive as a Databricks token). Every run re-read all rows since, ~200k Appwrite reads/day. Fixed: 16-char row ids, a stale-publish read guard, and a `catch_up` dispatch input (`.claude/rules/archive-data.md`).
