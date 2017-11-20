package nachos.threads;

/**
 * 我的名字叫彩票    我会有运气遇到你吗？
 */
import nachos.machine.*;
import nachos.threads.PriorityScheduler.PriorityQueue;

import java.util.Random;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not acceptable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends PriorityScheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */
	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 *
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer tickets from waiting
	 *            threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		// how to select next thread;

		return new LotteryQueue(transferPriority);
	}

	protected class LotteryQueue extends PriorityQueue {
		LotteryQueue(boolean transferPriority) {
			super(transferPriority);
			// TODO Auto-generated constructor stub
		}

		public int geteffpri() {
			int re = 0;
			if (waitQueue != null)
				for (KThread a : waitQueue) {
					re += getThreadState(a).getPriority();
				}
			return re;
		}

		@Override
		protected KThread pickNextThread() {
			// WaitQueue 非空
			/** zjt P2 T4 **/
			int sum = 0;
			for (KThread a : waitQueue)
				sum += getThreadState(a).getEffectivePriority(); // 获得总彩票的数量
			Random random = new Random();

			int lucky = random.nextInt(sum);

			int index = 0;
			for (KThread a : waitQueue) {
				index += getThreadState(a).getEffectivePriority();
				if (index > lucky) {
					return a;
				}
			}
			return null;
			/** zjt P2 T4 **/
		}
	}

	protected class Lotterythreadstate extends ThreadState {

		public Lotterythreadstate(KThread thread) {
			super(thread);
			// TODO Auto-generated constructor stub
		}

		@Override
		public int getEffectivePriority() {
			return this.getPriority() + thread.joinQueue.geteffpri();
		}
	}

	/**
	 * 功能 可以测试出 join()方法的加入 是否会有贡献优先级的事情发生 报告自己的优先级 等待另一个线程的结束 再次报告 自己结束
	 *
	 */
	protected static class PrioritySchedulerTest implements Runnable {
		PrioritySchedulerTest(int which, KThread thread) {
			this.which = which;
			joinThread = thread;
		}

		public void run() {
			boolean status = Machine.interrupt().disable();
			System.out
					.println("*** thread " + which + " priority is " + ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " join " + joinThread.getName());
			joinThread.join();// join目标线程，测试优先级能否传递
			System.out.println("*** thread " + which + " finish ");
		}

		private int which;
		private KThread joinThread;
	}

	/**
	 * 
	 * 直接打印自己的优先级 然后结束
	 *
	 */
	protected static class PrioritySchedulerTest2 implements Runnable {
		PrioritySchedulerTest2(int which) {
			this.which = which;
		}

		public void run() {
			boolean status = Machine.interrupt().disable();
			System.out
					.println("*** thread " + which + " priority is " + ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " finish ");
		}

		private int which;
	}

	/**
	 * 打印优先级 获得锁 yield() 再次打印优先级 释放锁
	 *
	 */
	protected static class PrioritySchedulerTest3 implements Runnable {
		PrioritySchedulerTest3(int which, Lock lock) {
			this.which = which;
			this.lock = lock;
		}

		public void run() {
			boolean status = Machine.interrupt().disable();
			System.out
					.println("*** thread " + which + " priority is " + ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " acquire lock");

			lock.acquire();
			KThread.yield();

			status = Machine.interrupt().disable();
			System.out
					.println("*** thread " + which + " priority is " + ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);

			lock.release();
			System.out.println("*** thread " + which + " finish ");
		}

		private int which;
		private Lock lock;
	}

	/**
	 * 
	 * 拿到锁 然后结束释放
	 *
	 */
	protected static class PrioritySchedulerTest4 implements Runnable {
		PrioritySchedulerTest4(int which, Lock lock) {
			this.which = which;
			this.lock = lock;
		}

		public void run() {
			boolean status = Machine.interrupt().disable();
			ThreadedKernel.scheduler.setPriority(5);
			System.out
					.println("*** thread " + which + " priority is " + ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " acquire lock");

			lock.acquire();

			System.out.println("*** thread " + which + " finish ");
		}

		private int which;
		private Lock lock;
	}

	// 测试方法1，测试join中能否完成优先级传递
	public static void selfTest() { // 创建一个线程，优先级默认为1
		KThread t = new KThread(new PrioritySchedulerTest2(1)).setName("thread 1");
		t.fork();
		// 创建一个线程，优先级设为3，join上一个线程，测试优先级能否传递
		KThread t2 = new KThread(new PrioritySchedulerTest(2, t)).setName("thread 2");
		((PriorityScheduler) ThreadedKernel.scheduler).getThreadState(t2).setPriority(3);
		t2.fork();
		// 创建一个线程，优先级设为5，join上一个线程，测试优先级能否传递
		KThread t3 = new KThread(new PrioritySchedulerTest(3, t2)).setName("thread 3");
		((PriorityScheduler) ThreadedKernel.scheduler).getThreadState(t3).setPriority(5);
		t3.fork();

		boolean status = Machine.interrupt().disable();
		KThread.readyQueue.print();
//		((PriorityScheduler) ThreadedKernel.scheduler).getThreadState(KThread.currentThread()).setPriority(7);
		Machine.interrupt().setStatus(status);

		KThread.yield();
		t.join();

	}

	// 测试方法2，测试能否通过Lock完成优先级传递
	public static void selfTest2() { // 创建一个线程，优先级默认为1
		System.out.println("进入锁   查看是否可以贡献优先级");
		Lock lock = new Lock();
		KThread t = new KThread(new PrioritySchedulerTest3(1, lock)).setName("thread 1");
		t.fork();
		// 创建一个线程，优先级设为5，申请上一个线程占有的锁，测试优先级能否传递
		KThread t2 = new KThread(new PrioritySchedulerTest4(2, lock)).setName("thread 2");
		t2.fork();
		t.join();
	}

}
