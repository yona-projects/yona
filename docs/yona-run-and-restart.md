How to run yona
===

Ported from legacy Yona's `docs/yona-run-and-restart.md`, adapted for yona.

```bash
java -jar build/libs/yona-0.0.1-SNAPSHOT.jar --spring.profiles.active=mariadb
```

First Page
----
The default port is `8080` (not legacy's `9000`). Locally, visit
[http://127.0.0.1:8080](http://127.0.0.1:8080).

If no user is registered yet, you'll be redirected automatically to the initial admin setup
screen (`/bootstrap-setup`) instead of legacy's roundabout "wrong password" → `welcome/secret`
flow — see [`install-yona-server.md`](install-yona-server.md).

### To restart

- Foreground process: `Ctrl-C`, then re-run the `java -jar ...` command.
- Running as a standing service: use the systemd unit example at
  [`support-script/systemd/yona.service`](../support-script/systemd/yona.service) —

  ```bash
  sudo systemctl restart yona
  ```
