package nachos.threads;

import nachos.machine.*;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority
	 *            <tt>true</tt> if this queue should transfer priority from waiting
	 *            threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		// PL
//		System.out.println("调用了优先级调用生成队列");
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		/** 从线程的ThreadState中得到线程的优先级 **/
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		/** 得到有效优先级 **/
		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	// 增加当前线程的优先级
	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread
	 *            the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		// 对没有建立ThreadState的线程建立线程
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority. 每一个队列都可以理解为一个资源队列
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		/**
		 * 将一个线程加入该队列是应该执行的操作 检查优先级是否高于holder，若高于进行优先级的传递
		 */
		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			// 获得当前线程的state
			ThreadState ts = getThreadState(thread);
			// 若当前有线程占用资源（holder执行占有资源或者正在执行的线程） 可以详细了解一下
			if (holder != null) {
				int priority = ts.getEffectivePriority();// 得到thread的有效优先级
				int holderPriority = holder.getEffectivePriority();
				if (holderPriority < priority) {// 若资源所有者的优先级更低，开始传递优先级
					holder.setPriority(priority);// TODO 这个优先级能够被恢复嘛？
				}
			}

			// 将线程加入到
			ts.waitForAccess(this);// 为waitQueue队列，添加该线程
		}

		// TODO 当什么时候执行？？？？？？？？？？？
		// if a thread acquires a lock that no other threads are waiting for, it
		// should call this method. 即当前运行的线程获得了其它线程都不需要的锁
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			// getThreadState(thread).acquire(this);
			/** zjt P1 T5 **/
			// 重新设置该队列锁的持有者
			holder = getThreadState(thread);
			for (KThread t : waitQueue) {
				// 更新队列中中的lockholder    转告等待队列中的线程的持有这是谁   
				getThreadState(t).acquire(this);
			}
			/** zjt P1 T5 **/
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			/** zjt P1 T5 **/
			// 没有等待队列，返回空
			if (waitQueue.isEmpty())
				return null;

			KThread firstThread = pickNextThread();
			if (firstThread != null) {
				// 将优先级最高的队列移出等待队列
				waitQueue.remove(firstThread);
				// 更新lockholder
				getThreadState(firstThread).acquire(this);
			}

			return firstThread;

		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return. 返回有效优先级最高的线程
		 */
		protected KThread pickNextThread() {
			/** zjt P1 T5 **/
			KThread nextThread = null;
			for (Iterator<KThread> ts = waitQueue.iterator(); ts.hasNext();) {
				KThread thread = ts.next();
				int priority = getThreadState(thread).getEffectivePriority();

				if (nextThread == null || priority > getThreadState(nextThread).getEffectivePriority()) {
					nextThread = thread;
				}
			}
			return nextThread;
			/** zjt P1 T5 **/
		}

		// public int getEffectivePriority() {
		//
		// // System.out.print("[Inside getEffectivePriority] transferPriority: " +
		// transferPriority + "\n"); // debug
		//
		// // if do not transfer priority, return minimum priority
		// if (transferPriority == false) {
		// // System.out.print("Inside 'getEffectivePriority:' false branch\n" ); //
		// debug
		// return priorityMinimum;
		// }
		//
		// if (dirty) {
		// effectivePriority = priorityMinimum;
		// for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {
		// KThread thread = it.next();
		// int priority = getThreadState(thread).getEffectivePriority();
		// if ( priority > effectivePriority) {
		// effectivePriority = priority;
		// }
		// }
		// dirty = false;
		// }
		// // 返回整个队列中中的最大优先级
		// return effectivePriority;
		// }

		/**
		 * 队列置dirty
		 */
		// public void setDirty() {
		// if (transferPriority == false) {
		// return;
		// }
		//
		// dirty = true;
		//
		// if (holder != null) {
		// holder.setDirty();
		// }
		// }

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {
				KThread currentThread = it.next();
				int priority = getThreadState(currentThread).getPriority();

				System.out.print("Thread: " + currentThread + "\t  Priority: " + priority + "\n");
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread. 即是否使用优先级调度队列
		 */
		public boolean transferPriority;
		/** zjt for P1 T5 **/
		/** 资源等待队列 **/
		private LinkedList<KThread> waitQueue = new LinkedList<KThread>();
		/** 持有资源的线程的ThreadState **/
		private ThreadState holder = null;
		/** 优先级被更改时 **/
		// private boolean dirty;
		/** waitQueue中的最高优先级 当!dirty时 **/
		private int effectivePriority;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread
		 *            the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;

			setPriority(priorityDefault);// 默认优先级
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return 
		 */
		public int getEffectivePriority() {
			/** zjt for P1 T5 **/
			return priority;
//			int maxEffective = this.priority;
//
//			if (dirty) {
//				for (Iterator<ThreadQueue> it = myResource.iterator(); it.hasNext();) {
//					PriorityQueue pg = (PriorityQueue) (it.next());
//					int effective = pg.getEffectivePriority();
//					if (maxEffective < effective) {
//						maxEffective = effective;
//					}
//				}
//			}
//
//			return maxEffective;
			/** zjt for P1 T5 **/
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority
		 *            the new priority.
		 */
		public void setPriority(int priority) {
			if (this.priority == priority)
				return;

			this.priority = priority;

			// implement me
			/** zjt for P1 T5 **/
			// 若是优先级队列(暂时默认是优先级队列)，且优先级增加，则需要进行优先级传递
			if (this.priority < priority) {
				this.priority = priority;
				resetPriority();
			} else
				this.priority = priority;

			/** zjt for P1 T5 **/
		}

//		// 更改优先级时，置dirty
//		private void setDirty() {
//			if (dirty)
//				return;
//			dirty = true;
//
//			PriorityQueue pg = (PriorityQueue) waitingOn;
//			if (pg != null) {
//				// 将阻碍自己的线程队列置为dirty
//				pg.setDirty();
//			}
//
//		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess 将线程添加到队列中
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			/** zjt for P1 T5 **/
			/** copied from RR **/
			Lib.assertTrue(Machine.interrupt().disabled());

			waitQueue.waitQueue.add(thread);

			// set waitingOn
//			waitingOn = waitQueue;

			// if the waitQueue was previously in myResource, remove it
			// and set its holder to null
			// When will this IF statement be executed?
			// if (myResource.indexOf(waitQueue) != -1) {
			// myResource.remove(waitQueue);
			// waitQueue.holder = null;
			// }
			// TODO 搞不明白
			/** zjt for P1 T5 **/
		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			/** zjt for P1 T5 **/
			Lib.assertTrue(Machine.interrupt().disabled());

//			更新锁的持有者
			lockHolder = waitQueue.holder;
			/** zjt for P1 T5 **/
		}

		// 新增添的方法，用于传递优先级
		protected void resetPriority() {
			int priority = this.getEffectivePriority();// 获取自身优先级
			// 我等的人是老大，我的优先级给他
			if (ResourceIwanted != null && ResourceIwanted.getEffectivePriority() < priority) {
				ResourceIwanted.setPriority(priority);
			}
			// 持有锁是老大,且不是正在执行的，我的优先级给他
			if (lockHolder != null /*&& lockHolder != KThread.currentThread()*/
					&& lockHolder.getEffectivePriority() < priority) {
				lockHolder.setPriority(priority);
			}
		}

		/** The thread with which this object is associated. */
		protected KThread thread;//他爸爸
		/** The priority of the associated thread. */
		protected int priority;
		/** zjt for P1 T5 **/
		/** 有效优先级 */
//		private int effectivePriority;
		/** 当优先级变化时 为true **/
		// private boolean dirty = false;
		/** 自己想要获得资源的线程 即自己正在等待的线程 **/
		protected ThreadState ResourceIwanted;
		/** 自己占有资源的线程队列 **/
//		protected LinkedList<ThreadQueue> ResourceIhaved = new LinkedList<ThreadQueue>();
		//当前持有锁的线程
		protected ThreadState lockHolder;
		/** zjt for P1 T5 **/
	}
	// /**
	// * for 测试
	// * @author zjtao
	// *
	// */
	// private static class PingTest implements Runnable {
	// PingTest(int which, Lock lock) {
	// this.which = which;
	// this.lock = lock;
	// }
	//
	// public void run() {
	// for (int i = 0; i < 5; i++) {
	//// 获得所之后输出
	//// lock.acquire();
	// System.out.println("*** thread " + which + " looped " + i + " times");
	// KThread.yield();
	// }
	// }
	// private Lock lock;
	// private int which;
	// }
	//
	// /**
	// * P1 T5 测试
	// */
	// public static void selfTest_5() {
	// Lock lock=new Lock();
	// lock.acquire();//主线程拿到锁
	//// lock.acquire();
	// KThread th1=new KThread(new PingTest(51,lock));
	// th1.setName("defu<1>号").fork();//fork后该线程会加入到就绪队列，并不立即执行
	// KThread th2=new KThread(new PingTest(52,lock));
	// th2.setName("defu<2>号").fork();//fork后该线程会加入到就绪队列，并不立即执行
	// boolean preStatus = Machine.interrupt().disable();
	// ThreadedKernel.scheduler.setPriority(7);
	// ThreadedKernel.scheduler.setPriority(th1, 3);
	// ThreadedKernel.scheduler.setPriority(th2, 5);
	// System.out.println("main1:优先级" +
	// ThreadedKernel.scheduler.getEffectivePriority());
	// KThread.yield();
	// System.out.println("main2优先级:" +
	// ThreadedKernel.scheduler.getEffectivePriority());
	// lock.release();
	//
	// Machine.interrupt().restore(preStatus);
	// th1.join();
	// }
	
	
	
	/**
	 *  功能    可以测试出 join()方法的加入  是否会有贡献优先级的事情发生
	 *  		 报告自己的优先级
	 *  		 等待另一个线程的结束
	 *  		 再次报告 自己结束
	 *
	 */
	protected static class PrioritySchedulerTest implements Runnable 
	{
		PrioritySchedulerTest(int which,KThread thread) 
		{
			this.which = which;
			joinThread = thread;
		}
		public void run() 
		{
			boolean status = Machine.interrupt().disable();
			System.out.println("*** thread " + which + " priority is "+ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " join "+joinThread.getName());
			joinThread.join();//join目标线程，测试优先级能否传递
			System.out.println("*** thread " + which + " finish ");
		}
		private int which;
		private KThread joinThread;
	}
	
	/**
	 * 
	 * 直接打印自己的优先级    然后结束
	 *
	 */
	protected static class PrioritySchedulerTest2 implements Runnable 
	{
		PrioritySchedulerTest2(int which) 
		{
			this.which = which;
		}
		public void run() 
		{
			boolean status = Machine.interrupt().disable();
			System.out.println("*** thread " + which + " priority is "+ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " finish ");
		}
		private int which;
	}
	
	/**
	 * 打印优先级 获得锁   yield()   再次打印优先级  释放锁    
	 *
	 */
	protected static class PrioritySchedulerTest3 implements Runnable 
	{
		PrioritySchedulerTest3(int which,Lock lock) 
		{
			this.which = which;
			this.lock = lock;
		}
		public void run() 
		{
			boolean status = Machine.interrupt().disable();
			System.out.println("*** thread " + which + " priority is "+ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " acquire lock");
			
			lock.acquire();
			KThread.yield();
			
			status = Machine.interrupt().disable();
			System.out.println("*** thread " + which + " priority is "+ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			
			lock.release();
			System.out.println("*** thread " + which + " finish ");
		}
		private int which;
		private Lock lock;
	}
	
	/**
	 * 
	 * 拿到锁     然后结束释放
	 *
	 */
	protected static class PrioritySchedulerTest4 implements Runnable 
	{
		PrioritySchedulerTest4(int which,Lock lock) 
		{
			this.which = which;
			this.lock = lock;
		}
		public void run() 
		{
			boolean status = Machine.interrupt().disable();
			ThreadedKernel.scheduler.setPriority(5);
			System.out.println("*** thread " + which + " priority is "+ThreadedKernel.scheduler.getEffectivePriority());
			Machine.interrupt().setStatus(status);
			System.out.println("*** thread " + which + " acquire lock");
			
			lock.acquire();
		
			System.out.println("*** thread " + which + " finish ");
		}
		private int which;
		private Lock lock;
	}
	
	//测试方法1，测试join中能否完成优先级传递
	public static void selfTest()
	{   //创建一个线程，优先级默认为1
		KThread t = new KThread(new PrioritySchedulerTest2(1)).setName("thread 1");
		t.fork();
		//创建一个线程，优先级设为3，join上一个线程，测试优先级能否传递
		KThread t2 = new KThread(new PrioritySchedulerTest(2,t)).setName("thread 2");
		((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(t2).setPriority(3);
		t2.fork();
		//创建一个线程，优先级设为5，join上一个线程，测试优先级能否传递
		KThread t3 = new KThread(new PrioritySchedulerTest(3,t2)).setName("thread 3");
		((PriorityScheduler)ThreadedKernel.scheduler).getThreadState(t3).setPriority(5);
		t3.fork();
		
		t.join();
	}
	
	//测试方法2，测试能否通过Lock完成优先级传递
	public static void selfTest2()
	{   //创建一个线程，优先级默认为1
		System.out.println("进入锁   查看是否可以贡献优先级");
		Lock lock = new Lock();
		KThread t = new KThread(new PrioritySchedulerTest3(1,lock)).setName("thread 1");
		t.fork();
		//创建一个线程，优先级设为5，申请上一个线程占有的锁，测试优先级能否传递
		KThread t2 = new KThread(new PrioritySchedulerTest4(2,lock)).setName("thread 2");
		t2.fork();
		t.join();

	}
	
	
	
	
	
	
	

}
