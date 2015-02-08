// clojurebridgemn.js

(function () {
  var version = "0.2.0",
      oldOnLoad = window.onload;

  CBMN = {
    committer: "anonymous",
    timestamp: "now",
    log: function(arguments) {
      if (window.console) {
        // console.log(Array.prototype.slice.call(arguments));
        // if we have a console we can just pass in the arguments
        console.log(arguments);
      }
    },
    welcome: function () {
      CBMN.log("welcome! website last updated by " + CBMN.committer + " at " + CBMN.timestamp);
      var updated = document.getElementById('updated');
      updated.innerHTML = "Last updated by " + CBMN.committer + " at " + CBMN.timestamp;
    },
    lastcommit: function (committer, timestamp) {
      CBMN.committer = committer;
      CBMN.timestamp = timestamp;
    },
  };

  if (typeof oldOnLoad != 'function') {
    window.onload = CBMN.welcome;
  } else {     // someone already hooked a function
    window.onload = function () {
      oldOnLoad();
      CBMN.welcome();
    }
  }

  window.CBMN = CBMN;
  CBMN.log("loaded!");
})();
