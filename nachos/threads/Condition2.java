package nachos.threads;

import java.util.Currency;

import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see nachos.threads.Condition
 */
public class Condition2 {
	/**
	 * Allocate a new condition variable.
	 *
	 * @param conditionLock
	 *            the lock associated with this condition variable. The current
	 *            thread must hold this lock whenever it uses <tt>sleep()</tt>,
	 *            <tt>wake()</tt>, or <tt>wakeAll()</tt>.
	 */
	public Condition2(Lock conditionLock) {
		this.conditionLock = conditionLock;
		/** zjt P1 T2 */
		waitQueue = ThreadedKernel.scheduler.newThreadQueue(false);
		/** zjt P1 T2 */
	}

	/**
	 * Atomically release the associated lock and go to sleep on this condition
	 * variable until another thread wakes it using <tt>wake()</tt>. The current
	 * thread must hold the associated lock. The thread will automatically
	 * reacquire the lock before <tt>sleep()</tt> returns.
	 */
	public void sleep() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());

		conditionLock.release();

		/** zjt P1 T2 **/
		boolean inStatus = Machine.interrupt().disable();
		waitQueue.waitForAccess(KThread.currentThread());
//		System.out.println(KThread.currentThread().getName()+"sleep");
		KThread.sleep();
		Machine.interrupt().restore(inStatus);
		/** zjt P1 T2 **/
		conditionLock.acquire();
	}

	/**
	 * Wake up at most one thread sleeping on this condition variable. The
	 * current thread must hold the associated lock.
	 */
	public void wake() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		/** zjt P1 T2 **/
		boolean inStatus = Machine.interrupt().disable();
		KThread thread = waitQueue.nextThread();
		if (thread != null) {
			thread.ready();
		}
		Machine.interrupt().restore(inStatus);
		/** zjt P1 T2 **/
	}

	/**
	 * Wake up all threads sleeping on this condition variable. The current
	 * thread must hold the associated lock.
	 */
	public void wakeAll() {
		Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		/** zjt P1 T2 **/
		boolean inStatus = Machine.interrupt().disable();
		while (true) {
			KThread thread = waitQueue.nextThread();
			if (thread == null)
				break;
			thread.ready();
//			System.out.println("唤醒"+thread.getName());
		}
		Machine.interrupt().restore(inStatus);
		/** zjt P1 T2 **/
	}

	/** zjt P1 T2 **/
	private static class Condition2Test implements Runnable {

		Condition2Test(Lock lock, Condition2 condition) {

			this.condition = condition;

			this.lock = lock;

		}

		public void run() {

			lock.acquire();

			System.out.print(KThread.currentThread().getName() + " acquired lock\n");

			condition.sleep();

			System.out.print(KThread.currentThread().getName() + " acquired lock again\n");

			lock.release();

			System.out.print(KThread.currentThread().getName() + " released lock \n");

		}

		private Lock lock;

		private Condition2 condition;
	}

	/** zjt P1 T2 **/

	/** zjt P1 T2 **/
	public static void selfTest() {

		System.out.print("Enter Condition2.selfTest\n");
		Lock lock = new Lock();

		Condition2 condition = new Condition2(lock);

		KThread t[] = new KThread[10];

		for (int i = 0; i < 10; i++) {

			t[i] = new KThread(new Condition2Test(lock, condition));

			t[i].setName("Thread" + i).fork();

		}

		KThread.yield();

		lock.acquire();

		System.out.print("condition.wake();\n");

		condition.wake();

		System.out.print("condition.wakeAll();\n");

		condition.wakeAll();

		lock.release();

		for (int i = 0; i < 10; i++) {
			t[i].join();
		}
		
		System.out.print("Leave Condition2.selfTest\n");
	}

	/** zjt P1 T2 **/

	private Lock conditionLock;
	/** zjt P1 T2 **/
	private ThreadQueue waitQueue;
	/** zjt P1 T2 **/
}
