# positano

> "Positano bites deep. It is a dream place that isn't quite real when
> you are there and becomes beckoningly real after you have gone." --
> John Steinbeck

Provenance system for Clojure code. This is a documentation-first
project, so there very little code right now.

## Provenance

Provenance (from the French provenir, "to come from"), is the
chronology of the ownership, custody or location of a historical
object. Within computer science, *provenance* means the lineage of
data, as per data provenance, with research in the last decade
extending the conceptual model of causality and relation to include
processes that act on data and agents that are responsible for those
processes.

## Motivation

### Immutability and complexity

One of the often-touted advantages of Clojure's immutability is that
it makes it easier to reason about the code because, well, state is
not mutated. Frequently, in practice, Clojure become very adventurous
with chaining multiple data transformations. This, combined with the
lack of types often leads to long chains of transformations and/or
deep call stacks that transform the data into shapes that are not
immediately apparent and require a painstaking exploratory approach to
elucidate.

Debuggers can be used to step through the code, and to watch the data
transformation as it happens and to construct a mental narrative of
the different steps and data shapes that arise, but would it not be
preferable to have a tool that constructs the narrative for you and
then allows you to explore it visually and by running queries against
the execution narrative? This type of exploration would have the
added advantage of being able to record multiple runs and then to
query them all at once.

### Data shape exploration and troubleshooting

Information about the parameters passed to functions can be
generalised to show the shape of data generally expected by the
function. This can then be used to infer a Prismatic schema or a
Typed Clojure type which can be incorporated into the code. This is
similar to F# type providers which [infer types from example
data](http://fsharp.github.io/FSharp.Data/library/JsonProvider.html).

There is a certain class of errors that occurs in dynamically typed
languages where a function is passed data of the correct shape in most
cases, but under certain circumstances it is passed data of the wrong
shape which then causes a bug. In most cases the bug is only revealed
several levels deeper into the call stack because the data conflicts
with the assumptions of a different function. Gathering data about
what the passed parameters look like can reveal such bugs in a
statistical way: if parameters conform to the same shape in 99% of the
calls to a function, the programmer should look into the remaining 1%
in case it reveals a bug.

### Coverage exploration

Running a collection of unit tests against a codebase along with a
provenance tool can reveal which parts of the codebase are not
exercised, and therefore it can be used as a test-coverage metric.

## Architecture

* Total code tracing
* Datomic for execution data collection
* Utility code for querying/filtering/exploring/visualising execution
  narrative.
* Utility code for extracting Prismatic schemas and Typed Clojure
  types from execution information.

## Next steps/roadmap

* Add caller information to function call events and link it via datomic refs
* Function to trace/untrace whole codebase (consider including clojure.core)
* Plug in-memory datomic to tracing
* Explore tracing individual S-expressions
* Utilities for deriving schemas and type annotations
* Run clojure.core tests, derive type annotations and compare to
  existing (hand-crafted) annotations.

## License

Copyright © 2015 Efstathios Sideris

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
