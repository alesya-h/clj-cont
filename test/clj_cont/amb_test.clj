(ns clj-cont.amb-test
  (:require [clojure.test :refer :all]
            [clj-cont.amb :as amb]))

(deftest basic-amb-test
  (testing "amb chooses from alternatives"
    (is (= [1 2 3] (amb/run-amb (amb/amb 1 2 3)))))
  
  (testing "amb with single choice"
    (is (= [:only] (amb/run-amb (amb/amb :only)))))
  
  (testing "amb with no choices fails"
    (is (= [] (amb/run-amb (amb/amb))))))

(deftest require-test
  (testing "require! filters results"
    (is (= [2 4] (amb/run-amb
                   (let [x (amb/amb 1 2 3 4 5)]
                     (amb/require! (even? x))
                     x)))))
  
  (testing "require! with no valid results"
    (is (= [] (amb/run-amb
                (let [x (amb/amb 1 2 3)]
                  (amb/require! (> x 10))
                  x))))))

(deftest multiple-amb-test
  (testing "multiple ambs explore all combinations"
    (is (= [[1 :a] [1 :b] [2 :a] [2 :b]]
           (amb/run-amb
             (let [x (amb/amb 1 2)
                   y (amb/amb :a :b)]
               [x y])))))
  
  (testing "amb with constraint between choices"
    (is (= [[1 6] [2 5] [3 4]]
           (amb/run-amb
             (let [x (amb/amb 1 2 3)
                   y (amb/amb 4 5 6)]
               (amb/require! (= (+ x y) 7))
               [x y]))))))

(deftest amb-range-test
  (testing "amb-range generates range of choices"
    (is (= [0 1 2 3 4] (amb/run-amb (amb/amb-range 5))))
    (is (= [3 4 5 6] (amb/run-amb (amb/amb-range 3 7))))))

(deftest amb-list-test
  (testing "amb-list chooses from collection"
    (is (= [:a :b :c] (amb/run-amb (amb/amb-list [:a :b :c]))))
    (is (= [] (amb/run-amb (amb/amb-list []))))))

(deftest pythagorean-triples-test
  (testing "finds correct Pythagorean triples"
    (let [triples (amb/pythagorean-triples 15)]
      (is (some #{[3 4 5]} triples))
      (is (some #{[5 12 13]} triples))
      (is (every? (fn [[a b c]] (= (+ (* a a) (* b b)) (* c c))) triples)))))

(deftest n-queens-test
  (testing "4-queens has exactly 2 solutions"
    (is (= 2 (count (amb/n-queens 4)))))
  
  (testing "8-queens has 92 solutions"
    (is (= 92 (count (amb/n-queens 8))))))

(deftest subset-sum-test
  (testing "finds subsets that sum to target"
    (let [results (amb/subset-sum [1 2 3 4 5] 9)]
      (is (some #{[1 3 5]} results))
      (is (some #{[2 3 4]} results))
      (is (some #{[4 5]} results))
      (is (every? #(= 9 (reduce + %)) results)))))

(deftest graph-coloring-test
  (testing "colors triangle graph with 3 colors"
    (let [graph {:a #{:b :c} :b #{:a :c} :c #{:a :b}}
          coloring (amb/graph-coloring graph 3)]
      (is (some? coloring))
      (is (not= (:a coloring) (:b coloring)))
      (is (not= (:b coloring) (:c coloring)))
      (is (not= (:a coloring) (:c coloring)))))
  
  (testing "cannot color triangle with 2 colors"
    (let [graph {:a #{:b :c} :b #{:a :c} :c #{:a :b}}]
      (is (nil? (amb/graph-coloring graph 2))))))

(deftest run-amb-1-test
  (testing "run-amb-1 returns first result"
    (is (= 1 (amb/run-amb-1 (amb/amb 1 2 3))))
    (is (nil? (amb/run-amb-1 (amb/amb))))))
