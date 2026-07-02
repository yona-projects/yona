package utils

import scala.language.implicitConversions

object JavaCollectionCompat {
  implicit def javaIterableAsScala[A](iterable: java.lang.Iterable[A]): Iterable[A] =
    scala.jdk.javaapi.CollectionConverters.asScala(iterable)

  implicit def javaCollectionAsScala[A](collection: java.util.Collection[A]): Iterable[A] =
    scala.jdk.javaapi.CollectionConverters.asScala(collection)

  implicit def javaIteratorAsScala[A](iterator: java.util.Iterator[A]): Iterator[A] =
    scala.jdk.javaapi.CollectionConverters.asScala(iterator)

  implicit def javaMapAsScala[K, V](map: java.util.Map[K, V]): scala.collection.mutable.Map[K, V] =
    scala.jdk.javaapi.CollectionConverters.asScala(map)
}
