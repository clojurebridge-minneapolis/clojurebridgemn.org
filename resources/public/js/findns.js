// -*- coding: utf-8 -*-

/* ClojureBridgeMN.org
   Copyright © 2015 Tom Marble
   Licensed under the MIT license
   https://github.com/clojurebridge-minneapolis/clojurebridgemn.org
*/

/* The function findns works similarly to Clojure's find-ns
   when given a string of dotted namespaces return that object
   (or null if not found).
   This function also acts as dereferencing global variables
   by string, e.g. "window.innerHeight"
*/
function findns(ns) {
  var names = ns.split('.');
  var n = null;
  var ntype;
  var i;

  for (i = 0; i < names.length; i++) {
    if (i == 0) {
      n = window[names[i]];
    } else {
      n = n[names[i]];
    }
    ntype = typeof n;
    if (ntype === "undefined") {
      n = null;
      break;
    }
  }
  return n;
};

// provide JavaScript's typeof operator in cljs as js/jstypeof
function jstypeof(o) {
  return typeof o;
};
