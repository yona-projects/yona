Backup and Restore
===

Ported from legacy Yona's `docs/yona-backup-restore.md`, adapted for yona.

Application data backup/restore (all DB tables)
---
Not present in legacy — yona provides a site-admin-only API that exports/imports every DB table
as JSON (`SiteApiController`, `DataBackupService`; tables are discovered automatically, so
nothing gets silently missed).

```bash
# Download a backup (requires admin auth)
curl -u <admin-loginId>:<password> http://127.0.0.1:8080/site/export -o yona-backup.json

# Restore — this is a full replace of every table, so test it in a non-production
# environment before pointing it at real data
curl -u <admin-loginId>:<password> -F "data=@yona-backup.json" http://127.0.0.1:8080/site/import
```

DB backup
---
Independent of the application-level export/import above, using the DB engine's own backup
tooling is the safer, more standard option for large/production data:

- MariaDB/MySQL: `mariadb-dump` (`mysqldump`)
- PostgreSQL: `pg_dump`/`pg_dumpall`
- SQL Server, CUBRID: each vendor's own backup procedure

Backing up settings and files
---
Legacy kept everything under one `YONA_DATA` directory (`conf`/`uploads`/`repo`/`logs`) that you
could just tar up. yona splits physical storage across independent settings instead. Back up
these four locations (see [README's "Deployment configuration"](../README.md#deployment-configuration-especially-on-windows)
for each key's default and purpose):

```
- yona.git.base-dir    - Git bare repositories
- yona.svn.base-dir    - SVN repositories
- yona.lfs.base-dir    - Git LFS objects
- yona.upload.base-dir - Attachment uploads
```

If left at their defaults (`/tmp/yona/...`), reconfigure them to a persistent directory first,
then back those directories up regularly. `application.yml` itself is part of the deployed
artifact and doesn't need separate backup, except for any production-only overrides it contains
(DB passwords, OAuth2 client secrets, etc.) — those should be backed up too.
