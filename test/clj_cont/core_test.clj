(ns clj-cont.core-test
  (:require [clojure.test :refer :all]
            [clj-cont.core :as c]))

(deftest basic-continuation-test
  (testing "Continuation can yield and resume"
    (let [results (atom [])
          scope (c/make-scope "test")
          cont (c/make-continuation scope
                 (fn [s]
                   (swap! results conj :start)
                   (c/yield! s)
                   (swap! results conj :middle)
                   (c/yield! s)
                   (swap! results conj :end)))]
      (is (= false (c/done? cont)))
      (c/resume! cont)
      (is (= [:start] @results))
      (is (= false (c/done? cont)))
      (c/resume! cont)
      (is (= [:start :middle] @results))
      (is (= false (c/done? cont)))
      (c/resume! cont)
      (is (= [:start :middle :end] @results))
      (is (= true (c/done? cont))))))

(deftest yield-values-test
  (testing "Continuation can yield values"
    (let [scope (c/make-scope "values")
          cont (c/make-continuation scope
                 (fn [s]
                   (c/yield! s :a)
                   (c/yield! s :b)
                   (c/yield! s :c)))]
      (is (= :a (c/next! cont)))
      (is (= :b (c/next! cont)))
      (is (= :c (c/next! cont))))))

(deftest cont-macro-test
  (testing "cont macro creates continuation"
    (let [results (atom [])
          cont (c/cont "macro-test" [s]
                 (doseq [x [1 2 3]]
                   (swap! results conj x)
                   (c/yield! s)))]
      (c/resume! cont)
      (is (= [1] @results))
      (c/resume! cont)
      (is (= [1 2] @results))
      (c/resume! cont)
      (is (= [1 2 3] @results)))))

(deftest generator-macro-test
  (testing "generator macro creates generator"
    (let [gen (c/generator "gen" [s]
                (doseq [x [:x :y :z]]
                  (c/yield! s x)))]
      (is (= :x ((:next! gen))))
      (is (= :y ((:next! gen))))
      (is (= :z ((:next! gen))))
      (is (= false ((:done? gen))))
      ((:next! gen)) ; finish the body
      (is (= true ((:done? gen)))))))

(deftest multiple-scopes-test
  (testing "Multiple continuations with different scopes are independent"
    (let [scope-a (c/make-scope "A")
          scope-b (c/make-scope "B")
          cont-a (c/make-continuation scope-a
                   (fn [s]
                     (c/yield! s :a1)
                     (c/yield! s :a2)))
          cont-b (c/make-continuation scope-b
                   (fn [s]
                     (c/yield! s :b1)
                     (c/yield! s :b2)))]
      (is (= :a1 (c/next! cont-a)))
      (is (= :b1 (c/next! cont-b)))
      (is (= :a2 (c/next! cont-a)))
      (is (= :b2 (c/next! cont-b))))))
