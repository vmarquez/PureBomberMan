package puregame

import scalaz._
import scalaz.effect.IO
import scalaz.Id._
import scalaz.StateT._
import java.util.concurrent.atomic.AtomicReference

sealed trait AtomicSTRef[A] {

  def aref: AtomicReference[A]

  def commit[B](s: State[A, B]): IO[(A, B)] = {
    val m = aref.get()
    val result = s(m)
    if (!aref.compareAndSet(m, result._1))
      commit(s)
    else
      IO { result }
  }

  def frozen = IO { aref.get }

}

object AtomicSTRef {

  def get[A] = StateT[Id, A, A](a => (a, a))

  def update[A](na: A) = StateT[Id, A, Unit](a => (na, ()))

  def apply[A](a: A) = new AtomicSTRef[A] {
    private val ref = new AtomicReference(a)
    def aref = ref
  }
}

object AtomicSTMap {

  def add[A, B](k: A, v: B) = StateT[Id, Map[A, B], Unit](map => (map + (k -> v), ()))

  def update[A, B](k: A, v: B) = StateT[Id, Map[A, B], B](map => (map.updated(k, v), v))

  def get[A, B](k: A) = StateT[Id, Map[A, B], B](map => (map, map(k)))

  def filter[A, B](f: Tuple2[A, B] => Boolean) = StateT[Id, Map[A, B], Map[A, B]](m => (m, m.filter(f)))

  def apply[A, B]() = new AtomicSTRef[Map[A, B]] {
    private val ref = new AtomicReference(Map[A, B]())
    def aref = ref
  }
}
