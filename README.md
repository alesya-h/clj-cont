# clj-cont

Delimited continuations for Clojure using JDK 21+ internal VM continuations.

This library provides:
- **`clj-cont.core`** - Low-level continuation primitives (yield, resume, generators)
- **`clj-cont.amb`** - Nondeterministic programming with backtracking (McCarthy's `amb`)

## Requirements

- **JDK 21+** (uses `jdk.internal.vm.Continuation`)
- **Clojure 1.11+**

## Setup

Add to your `project.clj`:

```clojure
:dependencies [[clj-cont "0.1.0-SNAPSHOT"]]
:jvm-opts ["--add-opens" "java.base/jdk.internal.vm=ALL-UNNAMED"]
```

The `--add-opens` flag is required to access the internal JVM continuation classes.

## clj-cont.core - Continuations

### Basic Usage

```clojure
(require '[clj-cont.core :as c])

;; Create a continuation that yields multiple times
(let [scope (c/make-scope "my-scope")
      cont (c/make-continuation scope
             (fn [s]
               (println "Step 1")
               (c/yield! s)
               (println "Step 2")
               (c/yield! s)
               (println "Step 3")))]
  
  (c/resume! cont)  ; prints "Step 1"
  (c/resume! cont)  ; prints "Step 2"
  (c/resume! cont)  ; prints "Step 3"
  (c/done? cont))   ; => true
```

### Yielding Values

Continuations can yield values back to the caller:

```clojure
(let [scope (c/make-scope "values")
      cont (c/make-continuation scope
             (fn [s]
               (c/yield! s :first)
               (c/yield! s :second)
               (c/yield! s :third)))]
  
  (c/next! cont)   ; => :first
  (c/next! cont)   ; => :second
  (c/next! cont))  ; => :third
```

### Using the `cont` Macro

The `cont` macro provides a more convenient syntax:

```clojure
(let [counter (c/cont "counter" [scope]
                (dotimes [i 5]
                  (println "Count:" i)
                  (c/yield! scope)))]
  (while (not (c/done? counter))
    (c/resume! counter)))
```

### Generators

Create Python-style generators:

```clojure
(let [gen (c/generator "fib" [s]
            (loop [a 0 b 1]
              (c/yield! s a)
              (recur b (+ a b))))]
  
  ((:next! gen))   ; => 0
  ((:next! gen))   ; => 1
  ((:next! gen))   ; => 1
  ((:next! gen))   ; => 2
  ((:next! gen)))  ; => 3
```

### API Reference

| Function | Description |
|----------|-------------|
| `(make-scope name)` | Create a new continuation scope |
| `(make-continuation scope f)` | Create continuation; `f` receives scope |
| `(resume! cont)` | Resume/start the continuation |
| `(yield! scope)` | Suspend execution |
| `(yield! scope value)` | Suspend and yield a value |
| `(done? cont)` | Check if continuation has completed |
| `(next! cont)` | Resume and return yielded value |
| `(cont name [scope] & body)` | Macro to create continuation |
| `(generator name [scope] & body)` | Macro to create generator map |

## clj-cont.amb - Nondeterministic Programming

The `amb` operator lets you write code that explores multiple possibilities and backtracks on failure.

### Basic Usage

```clojure
(require '[clj-cont.amb :as amb])

;; Find all pairs where x + y = 7
(amb/run-amb
  (let [x (amb/amb 1 2 3)
        y (amb/amb 4 5 6)]
    (amb/require! (= (+ x y) 7))
    [x y]))
;; => [[1 6] [2 5] [3 4]]

;; Find even numbers
(amb/run-amb
  (let [x (amb/amb 1 2 3 4 5)]
    (amb/require! (even? x))
    x))
;; => [2 4]
```

### How It Works

1. `amb` nondeterministically "chooses" one of its arguments
2. `require!` checks a condition; if false, it backtracks
3. `run-amb` collects all successful computation paths

The implementation uses continuations to track choice points and systematically explores all paths through the computation.

### Classic Examples

#### Pythagorean Triples

```clojure
(amb/pythagorean-triples 20)
;; => [[3 4 5] [5 12 13] [6 8 10] [8 15 17] [9 12 15] [12 16 20]]

;; Or write it yourself:
(amb/run-amb
  (let [a (amb/amb-range 1 21)
        b (amb/amb-range a 21)
        c (amb/amb-range b 21)]
    (amb/require! (= (+ (* a a) (* b b)) (* c c)))
    [a b c]))
```

#### N-Queens

```clojure
;; Find all solutions to 4-queens
(amb/n-queens 4)
;; => [[[0 1] [1 3] [2 0] [3 2]] 
;;     [[0 2] [1 0] [2 3] [3 1]]]

;; 8-queens has 92 solutions
(count (amb/n-queens 8))
;; => 92
```

#### Subset Sum

```clojure
;; Find subsets of [1 2 3 4 5] that sum to 9
(amb/subset-sum [1 2 3 4 5] 9)
;; => [[1 3 5] [2 3 4] [4 5]]
```

#### Graph Coloring

```clojure
;; Color a triangle with 3 colors
(amb/graph-coloring {:a #{:b :c} 
                     :b #{:a :c} 
                     :c #{:a :b}} 
                    3)
;; => {:a 0, :b 1, :c 2}

;; Cannot color with only 2 colors
(amb/graph-coloring {:a #{:b :c} :b #{:a :c} :c #{:a :b}} 2)
;; => nil
```

#### SEND + MORE = MONEY

```clojure
(amb/send-more-money)
;; => {:s 9, :e 5, :n 6, :d 7, :m 1, :o 0, :r 8, :y 2,
;;     :send 9567, :more 1085, :money 10652}
```

### API Reference

| Function | Description |
|----------|-------------|
| `(amb & choices)` | Choose one of the alternatives |
| `(amb* choices-vec)` | Function version of amb |
| `(require! pred)` | Backtrack if predicate is false |
| `(fail!)` | Explicitly trigger backtracking |
| `(run-amb & body)` | Return all solutions as vector |
| `(run-amb-1 & body)` | Return first solution or nil |
| `(run-amb-n n & body)` | Return up to n solutions |
| `(amb-range end)` | Choose from 0 to end-1 |
| `(amb-range start end)` | Choose from start to end-1 |
| `(amb-list coll)` | Choose element from collection |
| `(one-of coll)` | Alias for amb-list |
| `(amb-boolean)` | Choose true or false |

### Built-in Solvers

| Function | Description |
|----------|-------------|
| `(pythagorean-triples n)` | Find triples with components <= n |
| `(n-queens n)` | Solve N-queens problem |
| `(subset-sum nums target)` | Find subsets summing to target |
| `(graph-coloring graph k)` | Color graph with k colors |
| `(send-more-money)` | Solve cryptarithmetic puzzle |

## How It Works

### JVM Continuations

This library uses the internal `jdk.internal.vm.Continuation` class available in JDK 21+. These are delimited, one-shot continuations that can:

- **Yield**: Suspend execution and return control to the caller
- **Resume**: Continue execution from where it was suspended

Unlike Scheme's `call/cc`, these continuations:
- Are delimited (scoped to a specific region)
- Cannot be invoked multiple times (one-shot)
- Are very efficient (stack copying, not heap allocation)

### Why `--add-opens`?

The continuation classes are internal to the JDK and not part of the public API. The `--add-opens` flag allows our code to access these internal classes via reflection/MethodHandles.

### Comparison to ScopedValue

The original Java implementation used `ScopedValue` (preview feature) for passing yielded values. This Clojure port uses dynamic variables (`^:dynamic`) instead, which:
- Eliminates the need for `--enable-preview`
- Is more idiomatic in Clojure
- Provides similar scoped binding semantics

### Acknowledgements

This Clojure library is based on the code from the following Java gist: https://gist.github.com/thomasdarimont/bd22bbce165334dc7fa5ccf28c589414

### AI disclosure

OpenCode and Anthropic Claude Opus 4.5 were used during work on this library.

## License

EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0
