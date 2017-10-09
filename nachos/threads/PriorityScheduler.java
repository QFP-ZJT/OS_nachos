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
	 *            <tt>true</tt> if this queue should transfer priority from
	 *            waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		/**从线程的ThreadState中得到线程的优先级**/
		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());
		/**得到有效优先级**/
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
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		// TODO 当什么时候执行？？？？？？？？？？？
//		if a thread acquires a lock that no other threads are waiting for, it
//	     should call this method.
		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
			/**zjt P1 T5**/
			ThreadState state = getThreadState(thread);
			// TODO 看不懂
            if (this.holder != null && this.transferPriority) {
                this.holder.myResource.remove(this);
            }
             
            this.holder = state;
             
            state.acquire(this);
            /**zjt P1 T5**/
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			/**zjt P1 T5**/
			//没有等待队列，返回空
			 if (waitQueue.isEmpty())
	                return null;
			 // 若还是看不懂
			 if (this.holder != null && this.transferPriority)  
	                this.holder.myResource.remove(this);
			 
			 KThread firstThread = pickNextThread();
	            if (firstThread != null) {
	                // 将优先级最高的队列移出等待队列
	                waitQueue.remove(firstThread);
	                // 
	                getThreadState(firstThread).acquire(this);
	            }
	            
	            return firstThread;
			 
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return,
		 * without modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 * 返回有效优先级最高的线程
		 */
		protected KThread pickNextThread() {
			/**zjt P1 T5**/
			 KThread nextThread = null;
			 for (Iterator<KThread> ts = waitQueue.iterator(); ts.hasNext();) {  
	                KThread thread = ts.next(); 
	                int priority = getThreadState(thread).getEffectivePriority();
	                
	                if (nextThread == null || priority > getThreadState(nextThread).getEffectivePriority()) { 
	                    nextThread = thread;
	                }
	            }  
	         return nextThread;
	        /**zjt P1 T5**/
		}

		
		public int getEffectivePriority() {

            // System.out.print("[Inside getEffectivePriority] transferPriority: " + transferPriority + "\n"); // debug

            // if do not transfer priority, return minimum priority
            if (transferPriority == false) {
            // System.out.print("Inside 'getEffectivePriority:' false branch\n" ); // debug
                return priorityMinimum;
            }

            if (dirty) {
                effectivePriority = priorityMinimum; 
                for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {  
                    KThread thread = it.next(); 
                    int priority = getThreadState(thread).getEffectivePriority();
                    if ( priority > effectivePriority) { 
                        effectivePriority = priority;
                    }
                }
                dirty = false;
            }

            return effectivePriority;
        }

		/**
		 * 队列置dirty
		 */
        public void setDirty() {
            if (transferPriority == false) {
                return;
            }

            dirty = true;

            if (holder != null) {
                holder.setDirty();
            }
        }
		
		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			for (Iterator<KThread> it = waitQueue.iterator(); it.hasNext();) {  
                KThread currentThread = it.next(); 
                int  priority = getThreadState(currentThread).getPriority();

                System.out.print("Thread: " + currentThread 
                                    + "\t  Priority: " + priority + "\n");
            }
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting
		 * threads to the owning thread.
		 * 即是否使用优先级调度队列
		 */
		public boolean transferPriority;
		/** zjt for P1 T5 **/
		/**资源等待队列**/
		private LinkedList<KThread> waitQueue = new LinkedList<KThread>();
		/**持有资源的线程的ThreadState**/
		private ThreadState holder = null;
		/** 优先级被更改时 **/
		private boolean dirty;
		/**waitQueue中的最高优先级 当!dirty时**/
		private int effectivePriority; 
	}

	/**
	 * The scheduling state of a thread. This should include the thread's
	 * priority, its effective priority, any objects it owns, and the queue it's
	 * waiting for, if any.
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

			setPriority(priorityDefault);//默认优先级
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
		 * @return 若dirty返回myresource线程队列中的优先级最高的线程。
		 */
		public int getEffectivePriority() {
			/** zjt for P1 T5 **/
	        int maxEffective = this.priority;
	        
	        if (dirty) {
	            for (Iterator<ThreadQueue> it = myResource.iterator(); it.hasNext();) {  
	                PriorityQueue pg = (PriorityQueue)(it.next()); 
	                int effective = pg.getEffectivePriority();
	                if (maxEffective < effective) {
	                    maxEffective = effective;
	                }
	            }
	        }
	            
		    return maxEffective;
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
			setDirty();
			/** zjt for P1 T5 **/
		}

		// 更改优先级时，置dirty
		private void setDirty() {
			if(dirty)
				return;
			dirty = true;
			
			PriorityQueue pg = (PriorityQueue)waitingOn;
	        if (pg != null) {
	        		//将阻碍自己的线程队列置为dirty
	            pg.setDirty();
	        }
			
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is
		 * the associated thread) is invoked on the specified priority queue.
		 * The associated thread is therefore waiting for access to the resource
		 * guarded by <tt>waitQueue</tt>. This method is only called if the
		 * associated thread cannot immediately obtain access.
		 *
		 * @param waitQueue
		 *            the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 * 将线程重新放到等待队列
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			/** zjt for P1 T5 **/
			/**copied from RR**/
			Lib.assertTrue(Machine.interrupt().disabled());
			waitQueue.waitQueue.add(thread);
			waitQueue.setDirty();

	        // set waitingOn
	        waitingOn = waitQueue;

	        // if the waitQueue was previously in myResource, remove it 
	        // and set its holder to null
	        // When will this IF statement be executed?
	        if (myResource.indexOf(waitQueue) != -1) {
	            myResource.remove(waitQueue);
	            waitQueue.holder = null;
	        }
	        // TODO  搞不明白
	        /** zjt for P1 T5 **/
		}

		/**
		 * Called when the associated thread has acquired access to whatever is
		 * guarded by <tt>waitQueue</tt>. This can occur either as a result of
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
	        
	        // add waitQueue to myResource list
	        myResource.add(waitQueue);
	        
	        // clean waitingOn if waitQueue is just waiting on
	        if (waitQueue == waitingOn) {
	            waitingOn = null;
	        }

	        // 有效优先级可能发生变化
	        setDirty();
	        // TODO  搞不明白
	        /** zjt for P1 T5 **/
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		/** zjt for P1 T5 **/
		/**有效优先级 */
		private int  effectivePriority;
		/**当优先级变化时 为true**/
		private boolean dirty = false;
		/**阻碍自己获得资源的线程**/
		protected ThreadQueue waitingOn; 
		/**该线程自己拥有的资源**/
		protected LinkedList<ThreadQueue> myResource = new LinkedList<ThreadQueue>();
		/** zjt for P1 T5 **/
	}
	/**
	 * for 测试
	 * @author zjtao
	 *
	 */
	private static class PingTest implements Runnable {
		PingTest(int which, Lock lock) {
			this.which = which;
			this.lock = lock;
		}

		public void run() {
			for (int i = 0; i < 5; i++) {
//				获得所之后输出
 
				System.out.println("*** thread " + which + " looped " + i + " times");
				KThread.yield();
			}
		}
		private Lock lock;
		private int which;
	}
	
	/**
	 * P1 T5 测试
	 */
	public static void selfTest_5() { 
		Lock lock=new Lock(); 
		lock.acquire();//主线程拿到锁 
		KThread th1=new KThread(new PingTest(51,lock)); 
		th1.setName("defu<1>号").fork();//fork后该线程会加入到就绪队列，并不立即执行 
		KThread th2=new KThread(new PingTest(52,lock)); 
		th2.setName("defu<2>号").fork();//fork后该线程会加入到就绪队列，并不立即执行 
		boolean preStatus = Machine.interrupt().disable(); 
		ThreadedKernel.scheduler.setPriority(1);
		ThreadedKernel.scheduler.setPriority(th1, 3); 
		ThreadedKernel.scheduler.setPriority(th2, 7); 
		System.out.println("main1" + ThreadedKernel.scheduler.getEffectivePriority()); 
		KThread.yield(); 
		System.out.println("main2" + ThreadedKernel.scheduler.getEffectivePriority()); 
		lock.release(); 
		
		Machine.interrupt().restore(preStatus); 
		th1.join();
	}
	
	
}
