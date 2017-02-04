### "Input line is too long" error in BAT File
On the Windows OS, create a folder under \ so that it does not fall too far into the command line length limit.
We recommend creating the folder directly under the drive.

```
Windows OS Yona recommanded folder
C:\yona\yona-1.3.0 <- Unzip under the yona folder by version
C:\yona-data <- Conf files, logs, uploads, repo where folders are created and maintained. Specify with the YONA_DATA environment variable
```

### Specified key was too long; max key length is 767 bytes
If you see following errors at console,
```
[error] play - Specified key was too long; max key length is 767 bytes [ERROR:1071, SQLSTATE:42000] 
```
See [MariaDB 767 byte error](db-error-767.md) 


### Option adjustment when upgrading Yona version

If you are upgrading, migrate the database schema as follows:
You may experience a situation that does not work with a warning message that you need it.

    [warn] play - Your production database [default] needs evolutions!

In such a case, the migration should be done as follows
ApplyEvolutions.default Sets the Java property to true.

    SET JAVA_OPTS=-DapplyEvolutions.default=true
    bin\yona

### RuntimeException: Provider 'google' missing needed setting 'clientId'

An error that occurs when the related settings can not be read from the application.conf file.

Please add the following settings to the bottom of application.conf.

```
include "social-login.conf"
```
