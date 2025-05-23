package snackomaten

import scala.jdk.CollectionConverters.*

/** Utilities and data structures for working with concurrency. */
object Concurrent:
  /** Run `action` concurrently in another virtual thread. Requires at least Java 21. */
  def Run(action: => Unit): Thread = Thread.startVirtualThread(() => action)

  class MutableNumber(init: Int = 0):
    val underlying = java.util.concurrent.atomic.AtomicInteger(init)
    def get(): Int = underlying.get()
    def set(newValue: Int): Unit = underlying.set(newValue)
    def inc(): Int = underlying.incrementAndGet()
  end MutableNumber

  class MutableCounter():
    // the underlying AtomicInteger is private to guarantee increments by 1 only
    private val underlying = java.util.concurrent.atomic.AtomicInteger(0)
    def get(): Int = underlying.get()
    def inc(): Int = underlying.incrementAndGet()
  end MutableCounter

  class MutableFlag(init: Boolean = false):
    val underlying = java.util.concurrent.atomic.AtomicBoolean(init)
    def isTrue: Boolean = underlying.get()
    def isFalse: Boolean = !underlying.get()
    def setTrue(): Unit = underlying.set(true)
    def setFalse(): Unit = underlying.set(false)

    def toggle(): Unit = 
      var value = false
      while {
        value = underlying.get()
        !underlying.compareAndSet(value, !value)
      } do ()
  end MutableFlag

  class MutableLatch():
    val underlying = java.util.concurrent.CountDownLatch(1) 
    def waitUntilTrue(): Unit = underlying.await() 
    def isFalse: Boolean = underlying.getCount() > 0
    def setTrue(): Unit = underlying.countDown()
  end MutableLatch
  
  /** A sequence ordered by first-in-first-out: if e1 is put before e2 then e1 is out before e2. */
  class MutableFifoSeq[E]:
    val underlying: java.util.concurrent.BlockingQueue[E] = 
      java.util.concurrent.LinkedBlockingQueue[E]()
    def size: Int = underlying.size()
    def put(elem: E): Unit = underlying.put(elem)
    def nonEmpty: Boolean = underlying.size > 0
    def deleteIfPresent(elem: E): Boolean = underlying.remove(elem)
    def deleteAll(): Unit = underlying.clear()
    def outOrAwaitAvailable(): E = underlying.take() 
    def outOption(): Option[E] = Option(underlying.poll())

    def outAll(): Seq[E] = 
      val buf = java.util.ArrayList[E]() 
      underlying.drainTo(buf) 
      buf.asScala.toSeq 
    def iterator: Iterator[E] = underlying.asScala.iterator
    def foreach(f: E => Unit): Unit = underlying.forEach(e => f(e))
  end MutableFifoSeq


  /** A weakly ordered sequence where out gives elements of type E in priority order, 
   * but iterator and forEach has tail in no particular order. 
   * 
   * By requiring E to be a subtype of Comparable we avoid runtime ClassCastException, 
   * which otherwise can happen when using PriorityBlockingQueue (se JDK docs).
  */
  class MutablePrioritySeq[E <: Comparable[E]] extends MutableFifoSeq[E]:
    override val underlying: java.util.concurrent.BlockingQueue[E] = 
      java.util.concurrent.PriorityBlockingQueue[E]()
  end MutablePrioritySeq

  class MutableSortedSet[E <: Comparable[E]]:
    val underlying = java.util.concurrent.ConcurrentSkipListSet[E]()
    def size: Int = underlying.size()
    def add(elem: E): Unit = underlying.add(elem)
    def iterator: Iterator[E] = underlying.asScala.iterator
    def subSeq(fromElement: E, untilElement: E): Seq[E] = underlying.subSet(fromElement, untilElement).iterator().asScala.toSeq
    def headSeq(untilElement: E): Seq[E] = underlying.headSet(untilElement).iterator().asScala.toSeq
    def tailSeq(fromElement: E): Seq[E] = underlying.tailSet(fromElement).iterator().asScala.toSeq
    def higher(e: E): E = underlying.higher(e)
    def headOption: Option[E] = Option(underlying.first())
    def lastOption: Option[E] = Option(underlying.last())
    def take(n: Int): Seq[E] = iterator.take(n).toSeq

    def takeAfter(e: E, n: Int): Seq[E] = 
      if n < 1 then Seq() else
        var next = higher(e)
        var i = 1
        var xs = collection.mutable.ListBuffer.empty[E]
        while next != null && i <= n do 
          xs += next
          next = higher(next)
          i += 1
        end while
        xs.toSeq
  end MutableSortedSet

  class MutableKeyValueStore[K, V]():
    val underlying = java.util.concurrent.ConcurrentHashMap[K, V]()
    def put(key: K, value: V): Unit = underlying.put(key, value)
    def get(key: K): Option[V] = Option(underlying.getOrDefault(key, null.asInstanceOf[V]))
    def isDefinedAt(key: K): Boolean = underlying.containsKey(key)
    def toSeq: Seq[(K, V)] = underlying.asScala.toSeq
    def iterator: Iterator[(K, V)] = underlying.asScala.iterator
    def foreach(f: (K, V) => Unit): Unit = underlying.forEach((k, v) => f(k, v))
  end MutableKeyValueStore

end Concurrent