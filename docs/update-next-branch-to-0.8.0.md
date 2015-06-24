If you are using the Yobi’s next branch and H2 1.4.184, then you should follow instructions to update Yobi, or you can lose your data.

## How to check the version of H2 you are using

Open the build.sbt in the project home, and if you can find code like bellow you are using H2 1.4.184

```
"com.h2database" % "h2" % "1.4.184",
```

If there is no code like that or the version is 1.3.176, then you don't have to follow this instruction, 
and just update as usual by using rebasing or pulling.

After 45e1288 commit, the next branch had been using H2 1.4.184, but after cd534d2 commit we've changed to the H2 1.3.176(stable version) now. 
Therefore, please migrate your data with bellow guide:

## 1. First of all, stop your Yobi instance and make new backup of your database.

```
cp yobi.h2.db yobi.h2.db.bak
```

Just copy existing file like yobi.h2.db to another name.
And if there is something wrong, then just rename it to yobi.h2.db and run the Yobi with it.

## 2. Checkout to export-import branch

On the remote repository(mainstream), There is a branch named, export-import.
It supports data backup and restore.

## 3. Run Yobi. (!CAUTION! PREVENT USERS ACCESS)

After login as a site-manager, you can find ‘data’ menu in the site settings.
Click ‘backup’ button and wait until finish the downloading.

You have to do this work only when you prevent other users,
or the data integrity can be broken.

In order to prevent access while you migrate, turn off the proxy server if you are using it,
and run Yobi on new port that was not used to run Yobi before. 

## 4. After finishing the download, turn off Yobi and update the code to the latest version of the next branch.

```
git fetch origin
git rebase origin/next
```

Now, you can update your local Yobi’s code to the upstream’s next branch code by rebasing or pulling.

## 5. Remove existing database file (NOT THE BACKUP FILE)

```
rm yobi.h2.db
```

You should delete existing database file, then new database file will be generated.

## 6. Run Yobi. (!CAUTION! PREVENT USERS ACCESS UNTIL NOW)

Again, login as a site-admi and go to the ‘data’ menu. Select the downloaded file and click ‘restore’ button.

After all data restored, the page will be moved to the front page, so just wait until you see the first page.
And, of course, you have to do this work while you prevent users except you.

## 7. The End

Until now, if you didn’t meet any problems, you can allow users to use Yobi by turn on proxy server or restart Yobi on the port that it used to use.

Thanks.

