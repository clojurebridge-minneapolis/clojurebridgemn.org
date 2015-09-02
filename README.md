# ClojureBridgeMN.org

Source for the clojurebridgemn.org website

Are you (or do you know) a developer that might want to learn
Clojure in the Minneapolis / St. Paul area? We still have
openings at the next [ClojureBridgeMN](http://ClojureBridgeMN.org) workshop!

## documentation

_NOTE as of 0.3.0_: This is not a great example of Clojure/ClojureScript
programming for the web, but more like a work in progress. Future direction
will remove excess state mutation and will be based on om.next.

Now [ClojureBridgeMN](http://ClojureBridgeMN.org) is based on ClojureScript and Om!

The organization of the project is based on
[marron](https://github.com/tmarble/marron) which is my
opinionated reprise of
[chestnut](https://github.com/plexus/chestnut).

Cross-browser development and debugging is made easy
with [figwheel](https://github.com/bhauman/lein-figwheel).

The website design is intentionally responsive: nearly all
the dimensions are based on the `<html>` font size (which is
set by CSS media queries and can be adjusted in the settings page).
That being said the responsive heuristics need additional
tuning to ensure a fantastic *out of the box* experience
on mobile, tablets and desktops.

An attempt as been made to adjust to a variety of browser
quirks:
* Fullscreen mode for browser's that [support it](is supported in those browsers)
* Swipe gestures are supported for touch screens
* Mobile Safari (*aka* [the new IE6](http://arstechnica.com/gadgets/2014/08/with-mobile-safari-as-the-new-ie6-microsoft-modifies-windows-phone/)) has adaptations for
  * [clickable](http://www.quirksmode.org/blog/archives/2010/09/click_event_del.html) div elements (see also [Why your click events don't work on Mobile Safari](http://www.shdon.com/blog/2013/06/07/why-your-click-events-don-t-work-on-mobile-safari) and [Click event delegation on the iPhone](http://www.quirksmode.org/blog/archives/2010/09/click_event_del.html))
  * avoiding the dreaded [300ms delay](http://cubiq.org/remove-onclick-delay-on-webkit-for-iphone)
  * fixing the [viewport](http://garybacon.com/post/viewport-bug-in-mobile-safari-on-iphone-and-ipad/)

## deployment

The `bin/check-zip` script compares the size of the optimized and minified ClojureScript vs. a typical jQuery site:

````
      tmarble@ficelle 116 :) ./bin/check-gzip
      check for gzip compression
      starting production server
      downloading app.js
      results...
        server used gzip encoding
        file is gzip data
        uncompressed app.js 487424 bytes
        compressed   app.js  126976 bytes
        a  73.00% reduction
        or 150.00% of production jQuery (84345)
      stopping production server
      tmarble@ficelle 117 :)
```

That means that the current site -- including all the ClojureScript
and JavaScript libaries -- is only 50% bigger than minified
jQuery *by itself*.

# auto deployment

This section is *extra credit* for those curious about system administration
(and auto deployment to a VPS).

The `bin/update` script can be called with the last committer
and last commit timestamp by the `bit/github-push.py` script which,
itself, can be called by a git post-update hook.

The `bin/server` script can be called with the usual **start**,
**stop**, or **restart** commands.

If there is a `bin/logrotate.conf` configuration file then
**logrotate** will be called before starting the server.

The following variables will be used in `bin/server.env` if this
file is present:
* **SERVER_ADMIN** the e-mail address of the server adminstrator
* **SERVER_PROJECT** the name of the project (e.g. "clojurebridgemn")
* **SERVER_JAR** the relative path to the uberjar (e.g. "target/uberjar/clojurebridgemn.jar")
* **SERVER_PORT** the port to run the server on (by default 8080)
* **JSTAT_INTERVAL** the interval to run [jstat](http://docs.oracle.com/javase/8/docs/technotes/tools/unix/jstat.html#BEHBBBDJ) *(optional)*.
* **JVM_OPTS** the JVM tuning options
* **SERVER_COMMITTER** the last committer to the repository
* **SERVER_TIMESTAMP** the timestamp of the last commit

## Plans

There are various features which are needed, but not yet implemented:
* logging!
* using [secretary](https://github.com/gf3/secretary) for routing (to support "deep links")
* using [requestAnimationFrame](https://developer.mozilla.org/en-US/docs/Web/API/window/requestAnimationFrame) to provide timing. This should replace uses of setTimeout and provide a facility for "easing".

## Copyright and license

Copyright © 2015 Tom Marble

Licensed under the [MIT](http://opensource.org/licenses/MIT) [LICENSE](LICENSE)
