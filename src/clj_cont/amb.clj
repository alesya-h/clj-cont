(ns clj-cont.amb
  "Nondeterministic programming with backtracking using continuations.
   
   Implements McCarthy's amb operator for exploring search spaces.
   
   The implementation uses continuations to yield choice points back to
   a driver that manages the search. Each amb call yields control, allowing
   the driver to track choices and implement backtracking by re-running
   the computation with different predetermined choices.
   
   Example:
     (run-amb
       (let [x (amb 1 2 3)
             y (amb 4 5 6)]
         (require! (= (+ x y) 7))
         [x y]))
     ;=> [[1 6] [2 5] [3 4]]"
  (:require [clj-cont.core :as c]))

;; -----------------------------------------------------------------------------
;; Dynamic context
;; -----------------------------------------------------------------------------

(def ^:dynamic *amb-scope* nil)
(def ^:dynamic *choices-path* nil)  ; atom: list of predetermined choices (indices)
(def ^:dynamic *choice-counts* nil) ; atom: vector of choice counts at each amb

;; -----------------------------------------------------------------------------
;; Core operations
;; -----------------------------------------------------------------------------

(defn fail!
  "Signal failure, triggering backtracking."
  []
  (c/yield! *amb-scope* :fail))

(defn amb*
  "Implementation of amb - takes a vector of choices."
  [choices]
  (if (empty? choices)
    (fail!)
    (let [n (count choices)
          ;; Record that this amb has n choices
          _ (swap! *choice-counts* conj n)
          ;; Get predetermined choice index, or default to 0
          idx (if-let [path (seq @*choices-path*)]
                (do (swap! *choices-path* rest)
                    (first path))
                0)]
      (if (< idx n)
        (nth choices idx)
        ;; Index out of bounds - fail (shouldn't happen with correct driver)
        (fail!)))))

(defmacro amb
  "Nondeterministically choose one of the alternatives.
   
   Example: (amb 1 2 3) may return 1, 2, or 3"
  [& choices]
  `(amb* [~@choices]))

(defn require!
  "Require predicate to be truthy, otherwise backtrack."
  [pred]
  (when-not pred
    (fail!)))

;; Aliases
(def assert! require!)
(def guard! require!)

;; -----------------------------------------------------------------------------
;; Path enumeration
;; -----------------------------------------------------------------------------

(defn next-path
  "Given current path and choice counts, return next path to explore or nil.
   Uses odometer-style incrementing from the rightmost position."
  [path counts]
  (when (and (seq path) (seq counts))
    (let [path (vec path)
          counts (vec counts)
          len (min (count path) (count counts))]
      (loop [i (dec len)]
        (if (neg? i)
          nil  ; All paths exhausted
          (let [next-val (inc (get path i 0))
                max-val (get counts i 1)]
            (if (< next-val max-val)
              ;; Can increment here - reset all positions to the right to 0
              (into (subvec path 0 i) 
                    (cons next-val (repeat (- len i 1) 0)))
              ;; Carry over
              (recur (dec i)))))))))

;; -----------------------------------------------------------------------------
;; Search driver
;; -----------------------------------------------------------------------------

(defn run-with-path
  "Run the computation with a predetermined path of choices.
   Returns {:result value} on success, {:fail true} on failure,
   or {:fail true :counts [...]} on failure with choice count info."
  [scope make-body path]
  (let [choices-path (atom (seq path))
        choice-counts (atom [])
        result (atom nil)
        failed (atom false)]
    
    (let [cont (c/make-continuation scope
                 (fn [s]
                   (binding [*amb-scope* s
                             *choices-path* choices-path
                             *choice-counts* choice-counts]
                     (let [r (make-body)]
                       (c/yield! s [:result r])))))]
      
      ;; Run continuation until done or fail
      (loop []
        (when-not (c/done? cont)
          (c/resume! cont)
          (let [v c/*yielded-value*]
            (cond
              (= :fail v)
              (reset! failed true)
              
              (and (vector? v) (= :result (first v)))
              (reset! result (second v))
              
              :else
              (recur)))))
      
      (if @failed
        {:fail true :counts @choice-counts}
        {:result @result :counts @choice-counts}))))

(defmacro run-amb
  "Execute amb computation and return all solutions as a vector.
   
   Example:
     (run-amb
       (let [x (amb 1 2 3)]
         (require! (odd? x))
         (* x x)))
     ;=> [1 9]"
  [& body]
  `(let [make-body# (fn [] ~@body)
         results# (atom [])
         scope# (c/make-scope "amb")]
     
     ;; Start with empty path (all first choices)
     (loop [path# []]
       (let [{:keys [~'result ~'fail ~'counts]} 
             (clj-cont.amb/run-with-path scope# make-body# path#)]
         
         (when (and (not ~'fail) ~'result)
           (swap! results# conj ~'result))
         
         ;; Find next path to try
         ;; Use the counts we discovered on this run
         (let [path-len# (count ~'counts)
               ;; Extend current path with zeros if needed
               full-path# (vec (concat path# (repeat (- path-len# (count path#)) 0)))]
           (when-let [next# (clj-cont.amb/next-path full-path# ~'counts)]
             (recur next#)))))
     
     @results#))

(defmacro run-amb-1
  "Execute amb computation and return just the first solution, or nil."
  [& body]
  `(first (run-amb ~@body)))

(defmacro run-amb-n
  "Execute amb computation and return up to n solutions."
  [n & body]
  `(take ~n (run-amb ~@body)))

;; -----------------------------------------------------------------------------
;; Convenience functions
;; -----------------------------------------------------------------------------

(defn amb-range
  "Nondeterministically choose a number from start (inclusive) to end (exclusive)."
  ([end] (amb-range 0 end))
  ([start end] (amb* (vec (range start end)))))

(defn amb-list
  "Nondeterministically choose an element from a collection."
  [coll]
  (amb* (vec coll)))

(def one-of amb-list)

(defn amb-boolean
  "Nondeterministically choose true or false."
  []
  (amb* [true false]))

;; -----------------------------------------------------------------------------
;; Classic examples
;; -----------------------------------------------------------------------------

(defn pythagorean-triples
  "Find Pythagorean triples with components up to n."
  [n]
  (run-amb
    (let [a (amb-range 1 (inc n))
          b (amb-range a (inc n))  ; b >= a to avoid duplicates
          c (amb-range b (inc n))] ; c >= b
      (require! (= (+ (* a a) (* b b)) (* c c)))
      [a b c])))

(defn n-queens
  "Solve the N-queens problem. Returns all solutions."
  [n]
  (letfn [(safe? [placements row col]
            (every? (fn [[r c]]
                      (and (not= c col)
                           (not= (Math/abs (- r row)) 
                                 (Math/abs (- c col)))))
                    placements))]
    (run-amb
      (loop [row 0
             placements []]
        (if (= row n)
          placements
          (let [col (amb-range n)]
            (require! (safe? placements row col))
            (recur (inc row) (conj placements [row col]))))))))

(defn subset-sum
  "Find all subsets of numbers that sum to target."
  [numbers target]
  (run-amb
    (let [subset (reduce (fn [chosen x]
                           (if (amb* [true false])
                             (conj chosen x)
                             chosen))
                         []
                         numbers)]
      (require! (= (reduce + 0 subset) target))
      subset)))

(defn graph-coloring
  "Color a graph with k colors. Graph is {node -> #{neighbors}}."
  [graph k]
  (let [nodes (keys graph)
        colors (vec (range k))]
    (run-amb-1
      (reduce (fn [coloring node]
                (let [color (amb-list colors)
                      neighbors (get graph node #{})]
                  (require! (every? #(not= color (get coloring %)) neighbors))
                  (assoc coloring node color)))
              {}
              nodes))))

(defn send-more-money
  "Solve the classic SEND + MORE = MONEY cryptarithmetic puzzle."
  []
  (run-amb-1
    (let [digits (vec (range 10))
          s (amb-list (remove #{0} digits))  ; S can't be 0
          e (amb-list (remove #{s} digits))
          n (amb-list (remove #{s e} digits))
          d (amb-list (remove #{s e n} digits))
          m (amb-list (remove #{0 s e n d} digits))  ; M can't be 0
          o (amb-list (remove #{s e n d m} digits))
          r (amb-list (remove #{s e n d m o} digits))
          y (amb-list (remove #{s e n d m o r} digits))
          send (+ (* 1000 s) (* 100 e) (* 10 n) d)
          more (+ (* 1000 m) (* 100 o) (* 10 r) e)
          money (+ (* 10000 m) (* 1000 o) (* 100 n) (* 10 e) y)]
      (require! (= (+ send more) money))
      {:s s :e e :n n :d d :m m :o o :r r :y y
       :send send :more more :money money})))

(comment
  ;; Examples:
  
  (run-amb
    (let [x (amb 1 2 3)
          y (amb 4 5 6)]
      (require! (= (+ x y) 7))
      [x y]))
  ;; => [[1 6] [2 5] [3 4]]
  
  (run-amb
    (let [x (amb 1 2 3 4 5)]
      (require! (even? x))
      x))
  ;; => [2 4]
  
  (pythagorean-triples 20)
  ;; => [[3 4 5] [5 12 13] [6 8 10] [8 15 17] [9 12 15] [12 16 20]]
  
  (n-queens 4)
  ;; => [[[0 1] [1 3] [2 0] [3 2]] [[0 2] [1 0] [2 3] [3 1]]]
  
  (subset-sum [1 2 3 4 5] 9)
  
  (graph-coloring {:a #{:b :c} :b #{:a :c} :c #{:a :b}} 3)
  ;; => {:a 0, :b 1, :c 2}
  
  (send-more-money)
  ;; => {:s 9, :e 5, :n 6, :d 7, :m 1, :o 0, :r 8, :y 2, ...}
  )
