package nachos.threads;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;

	/* zjtao fot P1 T6 */
	static int childrenO;
	static int adultO;
	static int childrenM;
	static int adultM;
	static boolean boatO;
	// 记录船的状态
	static int boatstate;
	final static int EMPTY = 0, HALF = 1, FULL = 2;
	// 互斥锁
	static Lock lock;
	// 条件变量 大人无需返回 孩子不能在有大人在O岛的情况下去M 这两种情况属于无用功
	static Condition2 childrenonO, childrenonM, adultonO;

	static Semaphore isFinished;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		begin(3, 3, b);

	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		System.out.println("\n ***Testing Boats with "+children+" children , "+adults +"adults***");
		bg = b;
		// 初始化变量
		childrenO = children;
		adultO = adults;
		childrenM = 0;
		adultM = 0;
		boatstate = EMPTY;
		boatO = true;
		lock = new Lock();
		childrenonO = new Condition2(lock);
		childrenonM = new Condition2(lock);
		adultonO = new Condition2(lock);
		isFinished = new Semaphore(0);// TODO 好好看看原理

		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		// 样例
		// Runnable r = new Runnable() {
		// public void run() {
		// SampleItinerary();
		// }
		// };
		// KThread t = new KThread(r);
		// t.setName("Sample Boat Thread");
		// t.fork();
		// 创建大人孩子线程
		Runnable r = new Runnable() {
			public void run() {
				AdultItinerary();
			}
		};
		for (int i = 1; i <= adults; i++) {
			new KThread(r).setName("Adult " + i).fork();
		}
		r = new Runnable() {
			public void run() {
				ChildItinerary();
			}
		};
		for (int i = 1; i <= children; i++) {
			new KThread(r).setName("Child " + i).fork();
		}
		isFinished.P();
		System.out.println("渡船结束");
	}

	static void AdultItinerary() {
		bg.initializeAdult(); // Required for autograder interface. Must be the first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.

		/*
		 * This is where you should put your solutions. Make calls to the BoatGrader to
		 * show that it is synchronized. For example: bg.AdultRowToMolokai(); indicates
		 * that an adult has rowed the boat across to Molokai
		 * 大人渡河的条件: 
		 * 				船在O
		 * 				船EMPTY
		 * 				childrenM > 0
		 * 			不满足渡河条件:sleep()等待机会
		 * 大人渡河的影响: 
		 * 				船在M
		 * 				大人在两个岛上的人数变化
		 * 				船EMPTY -> FULL(调用渡船语句) -> EMPTY
		 * 				判断是否结束
		 * 					NO :唤醒所有孩子，以使得在M的孩子能够回去
		 * 					YES:释放信号量
		 * 				break;
		 */
		lock.acquire();
		while(true) {
//			System.out.println("大人被唤醒"+childrenM+boatstate);
			if(boatO && boatstate == EMPTY && childrenM > 0) {
				/*渡船*/
				adultO--;
				boatstate = FULL;
				bg.AdultRowToMolokai();
				boatstate = EMPTY;
				boatO = false;
				adultM++;
				if(adultO==0 && childrenO ==0) {//运送完成
					isFinished.V();
				}
				else {//运送结束
//					childrenonM.wake();//唤醒一个孩子回去
					childrenonM.wakeAll();//所有孩子都唤醒  但是只有在M岛的孩子应该行到有用的代码
				}
				break;
			}
			else {
				adultonO.sleep();
			}
		}
		lock.release();
	}

	static void ChildItinerary() {
		bg.initializeChild(); // Required for autograder interface. Must be the first thing called.
		// DO NOT PUT ANYTHING ABOVE THIS LINE.
		
		/**
		 * 孩子在O渡河的条件: 
		 * 				船EMPTY
		 * 					船在O 
		 * 					childrenO >=2 || adultO == 0
		 * 						childrenO>=1
		 * 							唤醒孩子还有机会走
		 * 						else
		 * 							自己走
		 * 				船HALF
		 * 					船在O
		 * 					跟随过河
		 * 			不满足渡河条件:sleep()等待机会
		 * 孩子O->M渡河的影响: 
		 * 				船在M
		 * 				孩子在两个岛上的人数变化
		 * 				船EMPTY -> FULL(调用渡船语句) -> EMPTY
		 * 				判断是否结束
		 * 					NO :唤醒所有孩子，以使得在M的孩子能够回去
		 * 					YES:释放信号量
		 * 				sleep();
		 * 孩子在M渡河的条件：
		 * 				船在M
		 * 				船为EMPTY;
		 * 					不需要判断是否需要回去(待定)
		 * 				是否需要回去
		 * 孩子M渡河到O的影响:
		 * 				船位置变化
		 * 				人数变化
		 * 				船的状态变化
		 * 				唤醒O的大人和孩子
		 */
		lock.acquire();
		while (true) {
			if (boatO) {
				if (boatstate == EMPTY) {
					if (childrenO >= 2 || adultO == 0) {
						childrenO--;
						boatstate = HALF;
						
						if (childrenO > 0) // 再找一个人渡河
							childrenonO.wakeAll();
						else {
							boatstate = EMPTY;// 若没有人搭船，直接改为空
							boatO = false;
							childrenonM.wakeAll();
						}
//						bg.ChildRowToMolokai();System.out.println("O岛上剩余孩子人数:"+childrenO);
						childrenM++;
						if (childrenO == 0 && adultO == 0)
							isFinished.V();	
						childrenonM.sleep();
					}else
					childrenonO.sleep();
				} else if (boatstate == HALF) {
					boatstate = FULL;
					childrenO--;
					bg.ChildRideToMolokai();//顺风船
					childrenM++;
					boatstate = EMPTY;
					boatO = false;	
					if (childrenO == 0 && adultO == 0)
						isFinished.V();
					else
						childrenonM.wakeAll();
					childrenonM.sleep();
				}
				else
					childrenonO.sleep();
			} else {// 船在M
				if(boatstate==EMPTY) {
					childrenM--;
					boatstate = FULL;
					bg.ChildRowToOahu();
					boatO = true;
					boatstate = EMPTY;
					childrenO++;
					adultonO.wakeAll();	
					childrenonO.wakeAll();
					childrenonO.sleep();
				}else
				childrenonM.sleep();
			}
		}	
//		System.out.println("error");
	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		System.out.println("\n ***Everyone piles on the boat and goes to Molokai***");
		bg.AdultRowToMolokai();
		bg.ChildRideToMolokai();
		bg.AdultRideToMolokai();
		bg.ChildRideToMolokai();
	}

}
