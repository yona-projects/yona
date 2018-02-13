Yona Install
===
```
Prerequisite
---
Java 8 (Java 9 isn't supported yet)
```

Download the latest version of Yona from https://github.com/yona-projects/yona/releases and unzip it.
Also, consider to use wget like followings.

ex)

    wget https://github.com/yona-projects/yona/releases/download/v1.3.0/yona-v1.3.0-bin.zip
    unzip yona.zip

### application.conf generating and setting

Go to the unpacked location and run `bin/yona`. 

**Note**: You should run it from yona installation folder as below. Windows users should also run `bin/yona.bat` instead of `bin/yona`.

```
cd yona
bin/yona
```

Execution will terminate with an error that the password is wrong. It's noraml. Don't worry. :)
You should now see the conf directory that you were not able to see when you extracted it.

#### DB configuration

You need to modify the DB connection settings to connect the MariaDB you installed earlier.

In the application.conf file under the conf folder, change the password in the following section to the db password set above
```
...
db.default.driver=org.mariadb.jdbc.Driver
db.default.url="jdbc:mariadb://127.0.0.1:3306/yona?useServerPrepStmts=true"
db.default.user=yona
db.default.password="yonadan"
...
```

`yonadan` is just example, recommed change it to your own password.


Run for first page
----

- Create a folder to hold various data including conf folder.
```
ex)

/yona-data
```
- copy conf folder to `/yona-data` created above.
```
Ex) Assuming your current location is /Users/doortts/Download/yona-v1.3.0-bin

cp -r conf /yona-data
```

- Specify the YONA_DATA environment variable and run Yona
```
Ex) Assuming your current location is /Users/doortts/Download/yona-v1.3.0-bin

YONA_DATA=/yona-data;export YONA_DATA
bin/yona
```

Then, please refer to [yona-run-and-upgrade.md](yona-run-and-upgrade.md) for details.

