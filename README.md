# clojurebridgemn.org

Source for the clojurebridgemn.org website

# Deploying

Upon ```git push``` the changes will be pulled by the hosting VPS
and your changes will be live on http://clojurebridgemn.org

Just after running the pull on the VPS the script ```bin/update```
is executed. It is extremely important that care be taken to
not comprise any subsequent pulls (by making side effects to
files under git control).


## Copyright and license

Copyright © 2014 Tom Marble

Licensed under the [MIT](http://opensource.org/licenses/MIT) [LICENSE](LICENSE)
