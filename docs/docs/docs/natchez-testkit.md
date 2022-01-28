---
layout: docs
title: "Natchez Testkit"
---

# Natchez Testkit

Natchez teskit is a small module that provides a `TestEntryPoint`  backed by a `Ref` so you can write unit tests
that check your application is sending the right information to Natchez.

## Installation

```scala
val natchezExtrasVersion = "@VERSION@"

libraryDependencies ++= Seq(
  "com.ovoenergy" %% "natchez-extras-testkit" % natchezExtrasVersion % Test
)
```

## Usage

An example of how to use it can be found in the test for `TestEntryPoint`:

[https://github.com/ovotech/natchez-extras/blob/640bc00c3f652596e1e8ff8e88f17735d751cc8e/natchez-extras-testkit/src/test/scala/com/ovoenergy/natchez/extras/testkit/TestEntryPointTest.scala](
https://github.com/ovotech/natchez-extras/blob/640bc00c3f652596e1e8ff8e88f17735d751cc8e/natchez-extras-testkit/src/test/scala/com/ovoenergy/natchez/extras/testkit/TestEntryPointTest.scala
)
