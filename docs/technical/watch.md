Watching
--------

If you click the Watch button on an object, you will receive notifications
every time when the object changes or is commented on.

Stop Watching
-------------

If you click the Unwatch button on an object, you will ignore the object that
you no longer receive any notifications even though the object changes or is
commented on.

If you click the Unwatch button on an object, you will ignore the object that
you no longer receive any notifications

Automatic Watching
------------------

Yobi automatically considers the following users as watchers of an object:

* the author of an object
* the assignee of an object
* users who commented on an object
* users who watch the project that an object belongs to.

But if a user ignores an object by clicking the Unwatch button, the user will
not be considered as the watcher of the object.

How Watching Works Internally
-----------------------------

### If a user click the Watch button

* If a user click the Watch button on an object, Yobi adds the user to the
  "Explicit Watchers" list of the object and removes the user from the list of
  "Explicit Ignorers". But if the user does not have permission to read the
  object, Yobi returns 403 Forbidden.

* If a user click the Unwatch button on an object, Yobi removes the user from
  "Explicit Watchers" list of the object and adds the user to the list of
  "Explicit Ignorers". It works even if the user does not have permission to
  read the object.

### Getting the watcher list of an object

1. Get the list of the following users:
    * the author of an object
    * the assignee of an object
    * users who commented on an object
    * users who watch the project that an object belongs to
    * users who watch an object explicitly

2. Then remove the following users from the list:
    * users who ignore the object explicitly
    * users who does not have permission to read the object

### Sending Notifications

If an event which requires sending notification occurs, send a notification to
users on the above list except the following users:

* the user who triggers the event
* the users who watch the project and ignore the type of the event
