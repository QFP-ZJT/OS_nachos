package nachos.threads;

import java.util.*; // +zjt+
import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() {
		/** zjt P1 T4 **/
		lock = new Lock();
		listener = new Condition2(lock);
		speaker = new Condition2(lock);
		waitingReturn = new Condition2(lock);
		/** zjt P1 T4 **/
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param word
	 *            the integer to transfer.
	 */
	public void speak(int word) {
		/** zjt P1 T4 **/
		// 获取操作权限
		lock.acquire();
		// 查看是否有人发送数据
		while (sendingSpeaker != 0) {
			waitingSpeaker++;
			// 加入等待队列 sleep()会释放掉锁
			speaker.sleep();
			// 等待被唤醒
			waitingSpeaker--;
			// 移出等待
		}
		sendingSpeaker++;
		data = word;
		System.out.println(KThread.currentThread().getName() + " speaks" + word);
		if (receivingListener != 0) {
			waitingReturn.wake();
		} else {
			waitingReturn.sleep();
		}
		sendingSpeaker--;
		if (waitingSpeaker != 0) {
			speaker.wake();
		}
		lock.release();
		return;
		/** zjt P1 T4 **/
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return the integer transferred.
	 */
	public int listen() {
		/** zjt P1 T4 **/
		lock.acquire();
		while (receivingListener != 0) {
			waitingListener++;
			listener.sleep();
			waitingListener--;
		}
		receivingListener++;
		if (sendingSpeaker != 0) {
			// 若有人正在写入 则唤醒等待数据接收的Condition2
			waitingReturn.wake();
		} else {
			// 若无人正在写入 则睡眠等待数据接收的Condition2
			waitingReturn.sleep();
		}
		if (waitingListener != 0) {
			listener.wake();
		}
		int word = data;
		System.out.println(KThread.currentThread().getName() + " listens a word" + word);
		receivingListener--;
		lock.release();
		return word;
	}

	/** zjt P1 T4 **/
	private static class Speaker implements Runnable {
		Speaker(Communicator comm, int word) {
			this.comm = comm;
			this.word = word;
		}

		public void run() {
			// System.out.print(KThread.currentThread().getName()
			// + " will speak " + this.word + "\n");
			comm.speak(this.word);

		}

		private int word = 0;
		private Communicator comm;
	}

	private static class Listener implements Runnable {
		Listener(Communicator comm) {
			this.comm = comm;
		}

		public void run() {
			// System.out.print(KThread.currentThread().getName()
			// + " will listen \n");

			int word = comm.listen();

			// System.out.print(KThread.currentThread().getName() + " Listen a
			// word: " + word + " \n");
		}

		private Communicator comm;
	}

	public static void selfTest() {

		System.out.print("Enter Communicator.selfTest\n");

		System.out.print("\nVAR1: 先说后听 \n");
		// 先说后听
		Communicator comm = new Communicator();
		KThread threadSpeaker = new KThread(new Speaker(comm, 100));
		threadSpeaker.setName("VAR1 Thread speaker").fork();

		KThread.yield();

		KThread threadListener = new KThread(new Listener(comm));
		threadListener.setName("VAR1 Thread listner").fork();

		KThread.yield();

		threadListener.join();
		threadSpeaker.join();

		System.out.print("\nVAR2: 先听后说\n");
		Communicator comm1 = new Communicator();
		// 先听后说
		KThread threadListener1 = new KThread(new Listener(comm1));
		threadListener1.setName("VAR2 Thread listner").fork();

		KThread.yield();

		KThread threadSpeaker1 = new KThread(new Speaker(comm1, 2));
		threadSpeaker1.setName("VAR2 Thread speaker").fork();

		KThread.yield();

		threadListener1.join();
		threadSpeaker1.join();

		System.out.print("\nVAR3: 先多人听，后说\n");

		Communicator comm2 = new Communicator();
		// 先多人听，后说
		KThread t[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			t[i] = new KThread(new Listener(comm2));
			t[i].setName("VAR3 Listener Thread" + i).fork();
		}

		KThread.yield();

		KThread speakerThread2 = new KThread(new Speaker(comm2, 200));
		speakerThread2.setName("VAR3 Thread speaker").fork();

		KThread.yield();
		t[0].join();
		speakerThread2.join();

		System.out.print("\nVAR4: 先说后多人听\n");

		Communicator comm3 = new Communicator();
		// 先说后多人听
		KThread speakerThread3 = new KThread(new Speaker(comm3, 300));
		speakerThread3.setName("VAR4 Thread speaker").fork();

		KThread.yield();

		KThread t3[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			t3[i] = new KThread(new Listener(comm3));
			t3[i].setName("VAR4 Listener Thread" + i).fork();
		}

		KThread.yield();
		t3[0].join();
		speakerThread3.join();

		System.out.print(
				"\nVAR5: 先多人听 后多人说\n");
		Communicator comm31 = new Communicator();
		// 先多人听 后多人说
		KThread t31[] = new KThread[10];
		for (int i = 0; i < 5; i++) {
			t31[i] = new KThread(new Listener(comm31));
			t31[i].setName("VAR5_前5 Listener Thread" + i).fork();
		}

		KThread.yield();

		KThread speakerThread31 = new KThread(new Speaker(comm31, 300));
		speakerThread31.setName("VAR5 Thread speaker").fork();

		KThread.yield();

		for (int i = 6; i < 10; i++) {
			t31[i] = new KThread(new Listener(comm31));
			t31[i].setName("VAR5_后5 Listener Thread" + i).fork();
		}

		KThread.yield();
		t3[0].join();
		speakerThread3.join();

		System.out.print("\nVAR6: 先多人说 后多人听\n");

		Communicator comm4 = new Communicator();
		// 先多人说 后多人听
		KThread t4[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			t4[i] = new KThread(new Speaker(comm4, (i + 1) * 100));
			t4[i].setName("VAR6 Speaker Thread" + i).fork();
		}

		KThread.yield();

		KThread listenerCond4 = new KThread(new Listener(comm4));
		listenerCond4.setName("VAR6 Thread listener").fork();

		KThread.yield();
		t4[0].join();
		listenerCond4.join();

		System.out.print("\nVAR7: Test for more speaker, one listener, speaker waits for listener\n");

		Communicator comm5 = new Communicator();

		KThread listenerCond5 = new KThread(new Listener(comm5));
		listenerCond5.setName("VAR7 Thread listener").fork();

		KThread.yield();

		KThread t5[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			t5[i] = new KThread(new Speaker(comm5, (i + 1) * 100));
			t5[i].setName("VAR7 Speaker Thread" + i).fork();
		}

		KThread.yield();
		t5[0].join();
		listenerCond5.join();

		System.out.print(
				"\nVAR8: Test for more speaker, one listener, speaker waits for listener,  and then create more speakers\n");

		Communicator comm51 = new Communicator();

		KThread t51[] = new KThread[10];
		for (int i = 0; i < 5; i++) {
			t51[i] = new KThread(new Speaker(comm51, (i + 1) * 100));
			t51[i].setName("VAR8_前5 Speaker Thread" + i).fork();
		}

		KThread.yield();

		KThread listenerCond51 = new KThread(new Listener(comm51));
		listenerCond51.setName("VAR8 Thread listener").fork();

		KThread.yield();
		for (int i = 5; i < 10; i++) {
			t51[i] = new KThread(new Speaker(comm51, (i + 1) * 100));
			t51[i].setName("VAR8_后5 Speaker Thread" + i).fork();
		}
		KThread.yield();

		t51[0].join();
		listenerCond51.join();

		System.out.print("\nVAR9:  Test for more speakers, more listeners, listeners waits for speaker \n");
		Communicator comm9 = new Communicator();

		KThread ts9[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			ts9[i] = new KThread(new Speaker(comm9, (i + 1) * 100));
			ts9[i].setName("VAR9 Speaker Thread" + i).fork();
		}

		KThread.yield();

		KThread tl9[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			tl9[i] = new KThread(new Listener(comm9));
			tl9[i].setName("VAR9 Listener Thread" + i).fork();
		}

		KThread.yield();

		for (int i = 0; i < 10; i++) {
			ts9[i].join();
			tl9[i].join();
		}

		System.out.print("\nVAR10:  Test for more speakers, more listeners, speaker waits for listeners \n");
		Communicator comm10 = new Communicator();

		KThread tl10[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			tl10[i] = new KThread(new Listener(comm10));
			tl10[i].setName("VAR10 Listener Thread" + i).fork();
		}

		KThread.yield();

		KThread ts10[] = new KThread[10];
		for (int i = 0; i < 10; i++) {
			ts10[i] = new KThread(new Speaker(comm10, (i + 1) * 100));
			ts10[i].setName("VAR10 Speaker Thread" + i).fork();
		}

		KThread.yield();

		for (int i = 0; i < 10; i++) {
			ts10[i].join();
			tl10[i].join();
		}

		System.out.print("\nVAR11:  Test for more speakers, more listeners, speaker waits for listeners \n");
		Communicator comm11 = new Communicator();

		int num = 80;
		ArrayList<KThread> t11 = new ArrayList<KThread>();

		for (int i = 0; i < num; i++) {
			KThread tmp = new KThread(new Speaker(comm11, (i + 1) * 100));
			tmp.setName("VAR11 Speaker Thread" + i);

			t11.add(tmp);
		}

		for (int i = 0; i < num; i++) {
			KThread tmp = new KThread(new Listener(comm11));
			tmp.setName("VAR11 Listener Thread" + i);

			t11.add(tmp);
		}

		Collections.shuffle(t11);

		for (int i = 0; i < num * 2; i++) {
			t11.get(i).fork();
		}

		KThread.yield();

		for (int i = 0; i < num * 2; i++) {
			t11.get(i).join();
		}

		System.out.print("\nTest for one speaker, more listener, speaker waits for listener \n");
		System.out.print("\nTest for more speaker, one listener, speaker waits for listener \n");

		System.out.print("Leave Communicator.selfTest\n");

	}

	/** zjt P1 T4 **/

	/** zjt P1 T4 **/
	private int waitingSpeaker = 0;
	private int sendingSpeaker = 0;
	private int waitingListener = 0;
	private int receivingListener = 0;
	private int data;
	private Lock lock;
	private Condition2 speaker;
	private Condition2 listener;
	private Condition2 waitingReturn;
	/** zjt P1 T4 **/

}
