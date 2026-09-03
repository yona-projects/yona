Watching
--------

Ported from legacy Yona's `docs/technical/watch.md`. The algorithm (explicit watchers/ignorers,
automatic watcher inference) is preserved in `WatchServiceImpl` — verified in code
(`findUnwatchers()` etc.).

If you click the Watch button on an object, you will receive notifications every time the object
changes or is commented on.

Stop Watching
-------------

If you click the Unwatch button on an object, you will ignore the object such that you no longer
receive any notifications even though the object changes or is commented on.

Automatic Watching
------------------

yona automatically considers the following users as watchers of an object:

* the author of the object
* the assignee of the object
* users who commented on the object
* users who watch the project the object belongs to

But if a user ignores an object by clicking the Unwatch button, they will no longer be
considered a watcher of that object.

How Watching Works Internally
-----------------------------

### If a user clicks the Watch button

* yona adds the user to the "Explicit Watchers" list of the object and removes them from the
  "Explicit Ignorers" list. But if the user doesn't have permission to read the object, yona
  returns 403 Forbidden.

* If a user clicks the Unwatch button, yona removes them from the "Explicit Watchers" list and
  adds them to the "Explicit Ignorers" list. This works even if the user doesn't have permission
  to read the object.

### Getting the watcher list of an object

1. Get the list of the following users:
    * the author of the object
    * the assignee of the object
    * users who commented on the object
    * users who watch the project the object belongs to
    * users who explicitly watch the object

2. Then remove the following users from the list:
    * users who explicitly ignore the object
    * users who don't have permission to read the object

### Sending Notifications

If an event that requires sending a notification occurs, send a notification to users on the
above list, except:

* the user who triggered the event
* users who watch the project but ignore that type of event
