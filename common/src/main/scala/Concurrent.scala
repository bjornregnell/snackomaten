package snackomaten

object Concurrent:

  object ThreadSafe:

    class MutableFlag(init: Boolean = false):
      val underlying = java.util.concurrent.atomic.AtomicBoolean(init)
      def isTrue: Boolean = underlying.get()
      def isFalse: Boolean = !underlying.get()
      def setTrue(): Unit = underlying.set(true)
      def setFalse(): Unit = underlying.set(false)

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
      def put(key: K, value: V): Unit = underlying.put(key, value)
      def get(key: K): Option[V] = Option(underlying.getOrDefault(key, null.asInstanceOf[V]))
      def isDefinedAt(key: K): Boolean = underlying.containsKey(key)

  end ThreadSafe

  def Run(action: => Unit): Thread = Thread.startVirtualThread(() => action)

  def RepeatUntil(ready: ThreadSafe.MutableFlag)(action: => Unit): Thread = 
    Run(while ready.isFalse do action)

  def RepeatForever(action: => Unit): Thread = 
    Run(while true do action)
  
end Concurrent