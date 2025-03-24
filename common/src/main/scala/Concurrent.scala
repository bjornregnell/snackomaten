package snackomaten

/** Utilities for working with concurrency. */
object Concurrent:
  /** Run `action` concurrently` in another virtual thread. Requires at least Java 21. */
  def Run(action: => Unit): Thread = Thread.startVirtualThread(() => action)

  /** Thread-safe data structures with atomic operations */
  object ThreadSafe:
    import scala.jdk.CollectionConverters.*

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

    class AwaitTrueSignal():
      val underlying = java.util.concurrent.CountDownLatch(1)
      def waitUntilTrue(): Unit = underlying.await() 
      def isFalse: Boolean = underlying.getCount() > 0
      def setTrue(): Unit = underlying.countDown()
    end AwaitTrueSignal

    class MutableList[E]:
      val underlying = java.util.concurrent.LinkedBlockingQueue[E]()
      def size: Int = underlying.size()
      def append(elem: E): Unit = underlying.add(elem)
      def nonEmpty: Boolean = underlying.size > 0
      def deleteIfPresent(elem: E): Boolean = underlying.remove(elem)
      def deleteAll(): Unit = underlying.clear()
      def outHeadOrWaitUntilAvailable(): E = underlying.take() 
      def outHeadIfPresent(): Option[E] = Option(underlying.poll())
      def outAllToSeq(): Seq[E] = 
        val buf = java.util.ArrayList[E]() 
        underlying.drainTo(buf) 
        buf.asScala.toSeq 
      def toSeq: Seq[E] = underlying.asScala.toSeq 
      def iterator: Iterator[E] = underlying.asScala.iterator
      def foreach(f: E => Unit): Unit = underlying.forEach(e => f(e))
    end MutableList
      
    class MutableKeyValueStore[K, V]():
      val underlying = java.util.concurrent.ConcurrentHashMap[K, V]()
      def put(key: K, value: V): Unit = underlying.put(key, value)
      def get(key: K): Option[V] = Option(underlying.getOrDefault(key, null.asInstanceOf[V]))
      def isDefinedAt(key: K): Boolean = underlying.containsKey(key)
      def toSeq: Seq[(K, V)] = underlying.asScala.toSeq
      def iterator: Iterator[(K, V)] = underlying.asScala.iterator
      def foreach(f: (K, V) => Unit): Unit = underlying.forEach((k, v) => f(k, v))
    end MutableKeyValueStore

  end ThreadSafe
  
end Concurrent