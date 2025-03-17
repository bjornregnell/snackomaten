package snackomaten

object Concurrent:
  class Flag(init: Boolean = false):
    val underlying = java.util.concurrent.atomic.AtomicBoolean(init)
    def isTrue: Boolean = underlying.get()
    def isFalse: Boolean = !underlying.get()
    def setTrue(): Unit = underlying.set(true)
    def setFalse(): Unit = underlying.set(false)

  def run(action: => Unit): Thread =
    Thread.startVirtualThread(() => action)

  def repeatUntil(ready: Flag)(action: => Unit): Thread = 
    run(while ready.isFalse do action)