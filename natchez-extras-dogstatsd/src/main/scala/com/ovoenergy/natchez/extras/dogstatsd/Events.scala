package com.ovoenergy.natchez.extras.dogstatsd

import com.ovoenergy.natchez.extras.dogstatsd.Events.Event

/**
 * Events are a Datadog extension to StatsD You can also send them over HTTP but since metrics are UDP we use
 * the UDP approach here too
 */
trait Events[F[_]] {
  def event(event: Event): F[Unit]
}

object Events {

  sealed abstract class Priority(val value: String)

  object Priority {
    case object Normal extends Priority("normal")
    case object Low extends Priority("low")
  }

  sealed abstract class AlertType(val value: String)

  object AlertType {
    case object Error extends AlertType("error")
    case object Warning extends AlertType("warning")
    case object Info extends AlertType("info")
    case object Success extends AlertType("success")
  }

  case class Event(
    title: String,
    body: String,
    alertType: AlertType,
    tags: Map[String, String],
    priority: Priority
  ) {
    def withTags(tags: Map[String, String]): Event =
      copy(tags = tags)
    def withTag(name: String, value: String): Event =
      copy(tags = tags.updated(name, value))
    def prependTitle(name: String): Event =
      copy(title = name + title)
  }

  object Event {
    def forThrowable(t: Throwable): Event =
      Event("Exception", t.getMessage, AlertType.Error, Map.empty, Priority.Normal)
  }
}
