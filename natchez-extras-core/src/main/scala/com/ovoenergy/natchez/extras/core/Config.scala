package com.ovoenergy.natchez.extras.core

sealed trait Config {
  protected[extras] def fullyQualifiedSpanName(name: String): String
}

object Config {
  case object UseExistingNames extends Config {
    override protected[extras] def fullyQualifiedSpanName(name: String): String = name
  }

  final case class ResourceOnly(resource: String) extends Config {
    override protected[extras] def fullyQualifiedSpanName(name: String): String = s"$resource:$name"
  }

  final case class ServiceAndResource(service: String, resource: String) extends Config {
    override protected[extras] def fullyQualifiedSpanName(name: String): String = s"$service:$resource:$name"
  }
}
