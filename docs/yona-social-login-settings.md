Set up social sign-in
----
- These settings are set in the social-login.conf file in the conf directory.
- If you want to remove or limit your social login settings, edit the application.social.login.support entry in the conf folder
- If you are running Yona server on public IP network, it is recommended to limit your own subscription and login.
- If you are not an e-mail user of an existing subscription account at the time of first sign-in for social login, you will receive an e-mail if automatic subscription occurs.
- At the end of application.conf If you set up mail play-easymail, you will receive a notification mail to newly registered users.

```
play-easymail {
  from {
    # Mailing from address
    email="you@gmail.com"

    # Mailing name
    name="Your Name"

    # Seconds between sending mail through Akka (defaults to 1)
    # delay=1
  }
}
```