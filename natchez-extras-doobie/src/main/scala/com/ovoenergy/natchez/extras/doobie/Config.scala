package com.ovoenergy.natchez.extras.doobie

sealed trait Config {
  protected[doobie] def fullyQualifiedSpanName(name: String): String
}

object Config {
  case object UseExistingNames extends Config {
    override protected[doobie] def fullyQualifiedSpanName(name: String): String = name
  }

  final case class ResourceOnly(resource: String) extends Config {
    override protected[doobie] def fullyQualifiedSpanName(name: String): String = s"$resource:$name"
  }

  final case class ServiceAndResource(service: String, resource: String) extends Config {
    override protected[doobie] def fullyQualifiedSpanName(name: String): String = s"$service:$resource:$name"
  }

  protected[doobie] val DefaultResourceName = "db.execute"
  val DefaultConfig = ResourceOnly(DefaultResourceName)
}
