;; ClojureBridgeMN.org
;; Copyright © 2015 Tom Marble
;; Licensed under the MIT license
;; https://github.com/clojurebridge-minneapolis/clojurebridgemn.org

(ns clojurebridgemn.data)

(defonce quotes
  [{:quote "Technology breeds a lot of misogyny. My hope is that we can tear down barriers to entry."
    :source :mentor}
   {:quote "I would like to work with more women!"
    :source :mentor}
   {:quote "Diversity is an intrinsic calling and a good thing."
    :source :mentor}
   {:quote "I would like to see more diversity in open source software."
    :source :mentor}
   {:quote "Diversity is very good and the technology space does not have a lot of diversity, I would like to help change that."
    :source :mentor}
   {:quote "My hope is that there is more diversity in Twin Cities tech. Coding is awesome, and I want to get women excited about awesome things!"
    :source :mentor}
   {:quote "I am a Professor for Clojure and I am hoping to make Clojure more beginner friendly and am hoping this event helps me to do so."
    :source :mentor}
   {:quote "I would like to expose myself to code and I am excited to learn!"
    :source :student}
   {:quote "I know nothing about coding and I need to learn."
    :source :student}
   {:quote "I would like to see if I can incorporate Clojure into the programming that I am already doing."
    :source :student}
   {:quote "I am an artist and I am really curious about programming."
    :source :student}
   {:quote "I am a librarian and I am really curious about programming."
    :source :student}
   {:quote "I am in finance and I am really curious about programming."
    :source :student}
   {:quote "I am a linguist and like knowing how languages work."
    :source :student}
   {:quote "My husband is learning coding and I wanted to learn it too!"
    :source :student}
   {:quote "I have no idea what I'm doing and that's why I am here!"
    :source :student}
   {:quote "I run a different organization for the Ruby language and I'm interested in learning another JVM language."
    :source :student}
   {:quote "I know just enough to be dangerous! Now, I want to learn more, being to work in the digital space makes me feel like badass!"
    :source :student}
   {:quote "I want to get back into coding."
    :source :student}
   {:quote "I am here because I haven't typed any interesting code in a very long time!"
    :source :student}
   {:quote "I am an Artist. Artists seem to have this 'cross discipline thing'."
    :source :student}
   {:quote "Currently I am teaching digital art and I never thought about coding."
    :source :student}
   {:quote "Both my dad and brother code but it never seemed exciting or friendly. Then I met [a mentor] and she was so enthusiastic! I started to consider the possibility. This event was the entrance point I needed. I'm hoping that this is the beginning of something bigger."
    :source :student}
   ;; from 6/26-27/2015
   {:quote "We're trying to create a community where people can learn programming."
    :source :mentor}
   {:quote "Computer science is modern day logic, everyone should learn it."
    :source :mentor}
   {:quote "Diversity is cool, Clojure is cool."
    :source :mentor}
   {:quote "I love the community aspect of [ClojureBridge] and I welcome the desire to increase diversity."
    :source :student}
   {:quote "I started a non-profit to get more people interested in coding."
    :source :student}
   {:quote "We're looking to get rid of our current stack, so I'm investigating other stacks."
    :source :student}
   {:quote "I do a lot of static HTML/CSS and I'm interested in learning more about dynamic content."
    :source :student}
   {:quote "It's been a while since I've programmed so this seemed like a good opportunity to get back into it."
    :source :student}
   {:quote "I've never done any programming, so this should be interesting but I'm excited."
    :source :student}
   {:quote "I'm interested to see how this goes today."
    :source :student}
   {:quote "Code combat is really fun."
    :source :student}
   {:quote "Diversity is important to me."
    :source :student}
   {:quote "Computer science to me is very creative - very expressive and that requires a lot of different experiences and viewpoints."
    :source :student}
   {:quote " This is so cool I just don't want to stop [for lunch]!"
    :source :student}
   ])

(defonce quotes-total (count quotes))

(defonce pictures
  ["clojurebridge-1.jpg"
   "clojurebridge-2.jpg"
   "clojurebridge-3.jpg"
   "clojurebridge-4.jpg"
   "clojurebridge-5.jpg"
   "clojurebridge-6.jpg"
   "clojurebridge-7.jpg"
   "clojurebridge-8.jpg"
   "clojurebridge-9.jpg"
   "clojurebridge-10.jpg"
   "clojurebridge-11.jpg"
   "clojurebridge-12.jpg"
   "clojurebridge-13.jpg"
   "cbmn-summer-2015-017.jpg"
   "cbmn-summer-2015-042.jpg"
   "cbmn-summer-2015-043.jpg"
   "cbmn-summer-2015-051.jpg"
   "cbmn-summer-2015-062.jpg"
   "cbmn-summer-2015-068.jpg"
   "cbmn-summer-2015-070.jpg"
   "cbmn-summer-2015-072.jpg"
   "cbmn-summer-2015-073.jpg"
   "cbmn-summer-2015-076.jpg"
   "cbmn-summer-2015-079.jpg"
   "cbmn-summer-2015-084.jpg"
   "cbmn-summer-2015-088.jpg"
   "cbmn-summer-2015-097.jpg"
   "cbmn-summer-2015-111.jpg"
   "cbmn-summer-2015-126.jpg"
   "cbmn-fall-2015-001.jpg"
   "cbmn-fall-2015-002.jpg"
   "cbmn-fall-2015-003.jpg"
   "cbmn-fall-2015-004.jpg"
   "cbmn-fall-2015-005.jpg"
   "cbmn-fall-2015-006.jpg"
   "cbmn-fall-2015-007.jpg"
   ])

(defonce pictures-total (count pictures))
