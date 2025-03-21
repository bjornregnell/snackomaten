package snackomaten

object Concurrent:

  object ThreadSafe:
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
          !underlying.weakCompareAndSet(value, !value)
        } do ()


    class MutableNumber(init: Int = 0):
      val underlying = java.util.concurrent.atomic.AtomicInteger(init)
      def get(): Int = underlying.get()
      def set(newValue: Int): Unit = underlying.set(newValue)
      def inc(): Int = underlying.incrementAndGet()

    class MutableCounter():
      private val underlying = java.util.concurrent.atomic.AtomicInteger(0)
      def get(): Int = underlying.get()
      def inc(): Int = underlying.incrementAndGet()

    class MutableKeyValueStore[K, V]():
      val underlying = java.util.concurrent.ConcurrentHashMap[K, V]()
      import scala.jdk.CollectionConverters.ConcurrentMapHasAsScala
      def toSeq: Seq[(K, V)] = underlying.asScala.toSeq
      def put(key: K, value: V): Unit = underlying.put(key, value)
      def get(key: K): Option[V] = Option(underlying.getOrDefault(key, null.asInstanceOf[V]))
      def isDefinedAt(key: K): Boolean = underlying.containsKey(key)

    class MutableFirstInFirstOutQueue[E]:
      val underlying = java.util.concurrent.PriorityBlockingQueue[E]()
      def add(elem: E): Unit = underlying.add(elem)
      def removeOneOrWaitUntilAvailable(): E = underlying.take() 
      def removeOneOpt(): Option[E] = Option(underlying.poll())
      def removeAll(): Unit = underlying.clear()

      /** Remove all elements and return them in a Seq[E] */
      def removeAllToSeq(): Seq[E] = 
        import scala.jdk.CollectionConverters.* 
        val buf = java.util.ArrayList[E]() 
        underlying.drainTo(buf) 
        buf.asScala.toSeq 

      def isEmpty: Boolean = underlying.size == 0
    end MutableFirstInFirstOutQueue

  end ThreadSafe

  def Run(action: => Unit): Thread = Thread.startVirtualThread(() => action)

  def RepeatUntil(ready: ThreadSafe.MutableFlag)(action: => Unit): Thread = 
    Run(while ready.isFalse do action)

  def RepeatForever(action: => Unit): Thread = 
    Run(while true do action)
  
end Concurrent