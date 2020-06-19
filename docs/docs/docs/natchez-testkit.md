---
layout: docs
title: "Natchez Testkit"
---

# Natchez Testkit

Natchez teskit is a small module that provides a `TestEntryPoint`  backed by a `Ref` so you can write unit tests
that check your application is sending the right information to Natchez.

## Installation

```scala
val effectUtilsVersion = "@VERSION@"
resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  "com.ovoenergy.effect" %% "natchez-testkit" % effectUtilsVersion % Test
)
```

## Usage

An example of how to use it can be found in the test for `natchez-fs2`:

[https://github.com/ovotech/effect-utils/blob/master/natchez-fs2/src/test/scala/com/ovoenergy/effect/natchez/AllocatedSpanTest.scala](
https://github.com/ovotech/effect-utils/blob/master/natchez-fs2/src/test/scala/com/ovoenergy/effect/natchez/AllocatedSpanTest.scala
)
