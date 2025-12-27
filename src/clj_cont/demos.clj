(ns clj-cont.demos
  "Demo examples ported from the Java gist."
  (:require [clj-cont.core :as c]))

;; -----------------------------------------------------------------------------
;; Demo 1: Simple counter with manual stepping
;; (Port of ContinuationDemo.java)
;; -----------------------------------------------------------------------------

(defn demo1-count-up
  "Creates a continuation that counts from 0 to 9, yielding after each number."
  []
  (c/cont "counter" [scope]
    (dotimes [i 10]
      (println i)
      (c/yield! scope))
    (println "Done")))

(defn run-demo1
  "Interactive demo - press enter to step through the counter."
  []
  (let [continuation (demo1-count-up)]
    (while (not (c/done? continuation))
      (print "Press enter to run one more step: ")
      (flush)
      (read-line)
      (c/resume! continuation))
    (println "No more input.")))

;; -----------------------------------------------------------------------------
;; Demo 2: Two independent counters
;; (Port of ContinuationDemo2.java)
;; -----------------------------------------------------------------------------

(defn demo2-counter
  "Creates a named counter continuation."
  [name]
  (let [scope (c/make-scope name)]
    {:continuation (c/make-continuation scope
                     (fn [s]
                       (dotimes [i 10]
                         (println (str name ": " i))
                         (c/yield! s))
                       (println (str name ": Done"))))
     :scope scope
     :name name}))

(defn run-demo2
  "Interactive demo - choose which counter to increment."
  []
  (let [counter1 (demo2-counter "counter1")
        counter2 (demo2-counter "counter2")]
    (loop []
      (when (or (not (c/done? (:continuation counter1)))
                (not (c/done? (:continuation counter2))))
        (print "Enter 1 or 2 to select counter, or 0 to exit: ")
        (flush)
        (let [input (read-line)
              choice (try (Integer/parseInt input) (catch Exception _ -1))]
          (case choice
            1 (when-not (c/done? (:continuation counter1))
                (c/resume! (:continuation counter1)))
            2 (when-not (c/done? (:continuation counter2))
                (c/resume! (:continuation counter2)))
            0 nil
            (println "Invalid input"))
          (when-not (= choice 0)
            (recur)))))
    (println "No more input.")))

;; -----------------------------------------------------------------------------
;; Demo 3: Generator pattern - yielding values
;; (Port of ContinuationDemo3.java)
;; -----------------------------------------------------------------------------

(defn run-demo3
  "Interactive demo - press enter to get next value from infinite generator."
  []
  (let [gen (c/generator "counter" [s]
              (loop [i 0]
                (c/yield! s i)
                (recur (inc i))))]
    (println "Infinite number generator. Press enter for next value, Ctrl+C to exit.")
    (loop []
      (when-not ((:done? gen))
        (read-line)
        (println (str "State: " ((:next! gen))))
        (recur)))))

;; -----------------------------------------------------------------------------
;; More idiomatic Clojure examples
;; -----------------------------------------------------------------------------

(defn cont->lazy-seq
  "Converts a continuation into a lazy sequence of yielded values.
   This bridges the continuation world with Clojure's sequence abstraction."
  [continuation]
  (lazy-seq
    (when-not (c/done? continuation)
      (let [value (c/next! continuation)]
        (cons value (cont->lazy-seq continuation))))))

(defn demo-fibonacci
  "Demo: Use continuations to create a lazy fibonacci sequence."
  []
  (let [fib-gen (c/generator "fib" [s]
                  (loop [a 0 b 1]
                    (c/yield! s a)
                    (recur b (+ a b))))]
    (println "First 20 Fibonacci numbers via continuation:")
    (println (take 20 (cont->lazy-seq (:continuation fib-gen))))))

(defn demo-coroutines
  "Demo: Simple coroutine-style ping-pong between two continuations."
  []
  (let [ping (c/cont "ping" [s]
               (dotimes [i 5]
                 (println "Ping!" i)
                 (c/yield! s)))
        pong (c/cont "pong" [s]
               (dotimes [i 5]
                 (println "Pong!" i)
                 (c/yield! s)))]
    (println "Coroutine-style ping-pong:")
    (loop []
      (when (or (not (c/done? ping))
                (not (c/done? pong)))
        (when-not (c/done? ping) (c/resume! ping))
        (when-not (c/done? pong) (c/resume! pong))
        (recur)))
    (println "Done!")))

(defn demo-tree-traversal
  "Demo: Use continuations for lazy tree traversal without stack overflow."
  []
  (letfn [(make-tree [depth]
            (if (zero? depth)
              {:value (rand-int 100)}
              {:left (make-tree (dec depth))
               :right (make-tree (dec depth))}))]
    (let [tree (make-tree 4)
          traverse (c/generator "tree" [s]
                     (letfn [(walk [node]
                               (when node
                                 (walk (:left node))
                                 (when (:value node)
                                   (c/yield! s (:value node)))
                                 (walk (:right node))))]
                       (walk tree)))]
      (println "Tree values via continuation-based traversal:")
      (println (take 10 (cont->lazy-seq (:continuation traverse)))))))

(comment
  ;; Run demos from REPL:
  (run-demo1)   ; Interactive - press enter to step
  (run-demo2)   ; Interactive - choose counter 1 or 2
  (run-demo3)   ; Interactive - infinite generator
  
  (demo-fibonacci)    ; Prints first 20 fib numbers
  (demo-coroutines)   ; Ping-pong output
  (demo-tree-traversal) ; Random tree values
  
  ;; Quick test of yielding values:
  (let [g (c/generator "test" [s]
            (c/yield! s :first)
            (c/yield! s :second)
            (c/yield! s :third))]
    [((:next! g))
     ((:next! g))
     ((:next! g))
     ((:done? g))])
  ;; => [:first :second :third false] (false because body hasn't finished after last yield)
  )
