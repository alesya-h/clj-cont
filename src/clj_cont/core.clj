(ns clj-cont.core
  "Delimited continuations using JDK internal VM continuations.
   
   Requires JVM option: --add-opens java.base/jdk.internal.vm=ALL-UNNAMED"
  (:import [java.lang.invoke MethodHandles MethodType]))

;; -----------------------------------------------------------------------------
;; Internal JDK Continuation access via MethodHandles
;; -----------------------------------------------------------------------------

(def ^:private scope-class
  (Class/forName "jdk.internal.vm.ContinuationScope"))

(def ^:private continuation-class
  (Class/forName "jdk.internal.vm.Continuation"))

(def ^:private lookup
  (MethodHandles/lookup))

(def ^:private scope-constructor
  (-> (MethodHandles/privateLookupIn scope-class lookup)
      (.findConstructor scope-class (MethodType/methodType Void/TYPE (into-array Class [String])))))

(def ^:private continuation-constructor
  (-> (MethodHandles/privateLookupIn continuation-class lookup)
      (.findConstructor continuation-class
                        (MethodType/methodType Void/TYPE (into-array Class [scope-class Runnable])))))

(def ^:private continuation-run
  (-> (MethodHandles/privateLookupIn continuation-class lookup)
      (.findVirtual continuation-class "run" (MethodType/methodType Void/TYPE (into-array Class [])))))

(def ^:private continuation-yield
  (-> (MethodHandles/privateLookupIn continuation-class lookup)
      (.findStatic continuation-class "yield"
                   (MethodType/methodType Boolean/TYPE (into-array Class [scope-class])))))

(def ^:private continuation-is-done
  (-> (MethodHandles/privateLookupIn continuation-class lookup)
      (.findVirtual continuation-class "isDone" (MethodType/methodType Boolean/TYPE (into-array Class [])))))

;; -----------------------------------------------------------------------------
;; Dynamic var for yielded state (replaces Java's ScopedValue)
;; -----------------------------------------------------------------------------

(def ^:dynamic *yielded-value*
  "Dynamic var holding the value passed to yield. Bound during continuation execution."
  nil)

;; -----------------------------------------------------------------------------
;; Public API
;; -----------------------------------------------------------------------------

(defn make-scope
  "Creates a new continuation scope with the given name."
  [name]
  (.invokeWithArguments scope-constructor (object-array [name])))

(defn make-continuation
  "Creates a new continuation with the given scope and body function.
   
   The body function receives the scope as its argument and can call
   (yield! scope) or (yield! scope value) to suspend execution."
  [scope f]
  (.invokeWithArguments continuation-constructor
                        (object-array [scope (reify Runnable (run [_] (f scope)))])))

(defn resume!
  "Resumes or starts the continuation. Returns nil."
  [continuation]
  (.invokeWithArguments continuation-run (object-array [continuation]))
  nil)

(defn yield!
  "Suspends the current continuation. Must be called from within a continuation body.
   Optionally passes a value that can be retrieved by the caller via `next!`."
  ([scope]
   (.invokeWithArguments continuation-yield (object-array [scope]))
   nil)
  ([scope value]
   (binding [*yielded-value* value]
     (.invokeWithArguments continuation-yield (object-array [scope])))
   nil))

(defn done?
  "Returns true if the continuation has completed execution."
  [continuation]
  (.invokeWithArguments continuation-is-done (object-array [continuation])))

(defn next!
  "Resumes the continuation and returns the yielded value (or nil if none)."
  [continuation]
  (resume! continuation)
  *yielded-value*)

;; -----------------------------------------------------------------------------
;; Convenience macros
;; -----------------------------------------------------------------------------

(defmacro cont
  "Creates and returns a continuation with the given scope name and body.
   
   Example:
     (cont \"my-scope\" [scope]
       (dotimes [i 10]
         (yield! scope i)))"
  [scope-name [scope-binding] & body]
  `(let [scope# (make-scope ~scope-name)]
     (make-continuation scope# (fn [~scope-binding] ~@body))))

(defmacro generator
  "Creates a generator-style continuation. Returns a map with:
   - :next!  - function to get next value (returns nil when done)
   - :done?  - function to check if generator is exhausted
   - :continuation - the underlying continuation
   - :scope - the continuation scope
   
   Example:
     (def g (generator \"nums\" [scope]
              (doseq [x (range 10)]
                (yield! scope x))))
     ((:next! g)) ;=> 0
     ((:next! g)) ;=> 1"
  [scope-name [scope-binding] & body]
  `(let [scope# (make-scope ~scope-name)
         cont# (make-continuation scope# (fn [~scope-binding] ~@body))]
     {:next! (fn []
               (when-not (done? cont#)
                 (next! cont#)))
      :done? (fn [] (done? cont#))
      :continuation cont#
      :scope scope#}))
