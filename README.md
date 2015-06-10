# clojurebridgemn.org

Source for the clojurebridgemn.org website

## Deploying

Upon ```git push``` the changes will be pulled by the hosting VPS
and your changes will be live on http://clojurebridgemn.org

Just after running the pull on the VPS the script ```bin/update```
is executed. It is extremely important that care be taken to
not comprise any subsequent pulls (by making side effects to
files under git control).

## currently just a static site

This site is running as a static apache2 site with a
DOCROOT of ```resources/public```.

That subdirectory has been chosen to facilitate ClojureScript.

## future

In the future we may update this site to use Om (ClojureScript).
Certain changes will be necessary on the VPS to accomodate this change.

## Copyright and license

Copyright © 2015 Tom Marble

Licensed under the [MIT](http://opensource.org/licenses/MIT) [LICENSE](LICENSE)
