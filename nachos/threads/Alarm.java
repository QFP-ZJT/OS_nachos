package nachos.threads;

import nachos.machine.*;
/** zjt P1 T3**/
import java.util.TreeSet;


/** zjt P1 T3**/

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 *
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		/** zjt P1 T3 **/
		waiting = new TreeSet<WaitingThread>();
		/** zjt P1 T3 **/

		// tick唤醒timerInterrupt     为timer设定应该执行的方法
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {
		KThread.currentThread().yield();
		/** zjt P1 T3 **/
		long time = Machine.timer().getTime();

		if (waiting.isEmpty())
			return;
		// 判断最早应该被唤醒的线程是否应该唤醒
		if (((WaitingThread) waiting.first()).time > time)
			return;

		Lib.debug(dbgInt, "Invoking Alarm.timerInterrupt at time = " + time);

		while (!waiting.isEmpty() && ((WaitingThread) waiting.first()).time <= time) {
			WaitingThread next = (WaitingThread) waiting.first();

			// 将应该唤醒的线程放入就绪队列，并在waiting队列中删掉该线程
			next.thread.ready();
			waiting.remove(next);

			Lib.assertTrue(next.time <= time);

			Lib.debug(dbgInt, "  " + next.thread.getName());
		}

		Lib.debug(dbgInt, "  (end of Alarm.timerInterrupt)");
		/** zjt P1 T3 **/
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 *
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 *
	 * @param x
	 *            the minimum number of clock ticks to wait.
	 *
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		// 计算应该唤醒的时钟周期
		long wakeTime = Machine.timer().getTime() + x;

		// for now, cheat just to get something working (busy waiting is bad)
		// while (wakeTime > Machine.timer().getTime())
		// KThread.yield();

		// zjt for now not cheat anymore
		/** zjt P1 T3 **/
		boolean intStatus = Machine.interrupt().disable();
		WaitingThread toAlarm = new WaitingThread(wakeTime, KThread.currentThread());
		Lib.debug(dbgInt, "Wait thread " + KThread.currentThread().getName() + " until " + wakeTime);
		waiting.add(toAlarm);
		KThread.sleep();

		Machine.interrupt().restore(intStatus);
		/** zjt P1 T3 **/
	}

	/** zjt P1 T3 **/
	private static final char dbgInt = 'i';
	// 使用TreeSet方便进行排序
	private TreeSet<WaitingThread> waiting;

	private class WaitingThread implements Comparable {

		WaitingThread(long time, KThread thread) {
			this.time = time;
			this.thread = thread;
		}

		public int compareTo(Object o) {
			WaitingThread toOccur = (WaitingThread) o;

			// can't return 0 for unequal objects, so check all fields
			if (time < toOccur.time)
				return -1;
			else if (time > toOccur.time)
				return 1;
			else
				return thread.compareTo(toOccur.thread);
		}

		long time;
		KThread thread;

	}

	private static class AlarmTest implements Runnable {
		AlarmTest(long x) {
			this.time = x;
		}

		public void run() {

			System.out.print(KThread.currentThread().getName() + " alarm\n");
			ThreadedKernel.alarm.waitUntil(time);
			System.out.print(KThread.currentThread().getName() + " woken up at \n");

		}

		private long time;
	}

	public static void selfTest() {

		System.out.print("Enter Alarm.selfTest \n");

		Runnable r = new Runnable() {
			public void run() {
				KThread t[] = new KThread[10];

				for (int i = 0; i < 10; i++) {
					t[i] = new KThread(new AlarmTest(160 + i * 20));
					t[i].setName("Thread" + i).fork();
				}
				for (int i = 0; i < 10000; i++) {
					KThread.yield();
				}
			}
		};

		KThread t = new KThread(r);
		t.setName("Alarm SelfTest");
		t.fork();
		KThread.yield();
		t.join();
		System.out.print("Leave Alarm.selfTest\n");
	}

	/** zjt P1 T3 **/
}