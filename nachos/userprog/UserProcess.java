package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;

import java.io.EOFException;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		openfile = new OpenFile[16];

		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];// 在配置文件中定义了物理内存的大小
		// 初始化页表 在但线程的时候将所有的资源私有化
		// for (int i = 0; i < numPhysPages; i++)
		// VPS PPS trans? RO used? dirty?
		// pageTable[i] = new TranslationEntry(i, i, true, false, false, false);
		// Processor.translate()实现虚实地址的转换
		// UserProcess.restoreState()将页表传递给CPU
		// UThread.restoreState()-->UserProcess.restoreState()
		// UThread.runProgram()-->UserProcess.restoreState()
		// 设置标准输入输出
		this.openfile[0] = UserKernel.console.openForReading();
		this.openfile[1] = UserKernel.console.openForWriting();

		pidlock.acquire();
		pid = Staticpid++;
		numOfRunningProcess++;
		pidlock.release();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to load
	 * the program, and then forks a thread to run it.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args)) {
			System.out.println("load 失败");
			return false;
		}
		new UThread(this).setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch. Called by
	 * <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for the
	 * null terminator, and convert it to a <tt>java.lang.String</tt>, without
	 * including the null terminator. If no null terminator is found, returns
	 * <tt>null</tt>.
	 *
	 * @param vaddr
	 *            the starting virtual address of the null-terminated string.
	 * @param maxLength
	 *            the maximum number of characters in the string, not including the
	 *            null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to read.
	 * @param data
	 *            the array where the data will be stored.
	 * @param offset
	 *            the first byte to write in the array.
	 * @param length
	 *            the number of bytes to transfer from virtual memory to the array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		// 现在的大小是页大小乘页数量
		if (vaddr < 0 || vaddr >= pageSize * numPages)
			return 0;

		if ((length + vaddr) > pageSize * numPages)
			length = pageSize * numPages - vaddr;

		int readByte = 0;
		while (readByte < length) {
			// 内存地址 / pagesize = 虚拟页数
			int pageN = Processor.pageFromAddress(vaddr + readByte);
			if (pageN < 0 || pageN >= pageTable.length)
				return 0;
			// 内存地址 % pagesize = 页便宜
			int pageO = Processor.offsetFromAddress(vaddr + readByte);
			// 该页中剩余的内容
			int byteNum = pageSize - pageO;
			// 取有与需要的最小值
			int amount = Math.min(byteNum, length - readByte);
			int phyAddr = pageTable[pageN].ppn * pageSize + pageO;// 页表映射
			// src src.start des des.start mount
			System.arraycopy(memory, phyAddr, data, offset + readByte, amount);
			readByte += amount;
		}
		return readByte;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 *
	 * @param vaddr
	 *            the first byte of virtual memory to write.
	 * @param data
	 *            the array containing the data to transfer.
	 * @param offset
	 *            the first byte to transfer from the array.
	 * @param length
	 *            the number of bytes to transfer from the array to virtual memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		// for now, just assume that virtual addresses equal physical addresses
		if (vaddr < 0 || vaddr >= pageSize * numPages)
			return 0;
		if (length + vaddr > pageSize * numPages)// 长度超过限制
			length = pageSize * numPages - vaddr;
		if (data.length - offset < length)// 提取的数量  若有的少于需要转移的则减少需要写入的数据数量
			length = data.length - offset;
		int writebyte = 0;
		while (writebyte < length) {
			int pageN = Processor.pageFromAddress(vaddr + writebyte);
			if (pageN < 0 || pageN >= pageTable.length)
				return 0;
			// 内存地址 % pagesize = 页偏移
			int pageO = Processor.offsetFromAddress(vaddr + writebyte);
			// 该页中剩余可写
			int byteNum = pageSize - pageO;
			// 取有与需要的最小值
			int amount = Math.min(byteNum, length - writebyte);
			int phyAddr = pageTable[pageN].ppn * pageSize + pageO;// 页表映射
			// src src.start des des.start mount
			System.arraycopy(data, offset + writebyte, memory, phyAddr, amount);
			writebyte += amount;
		}
		return writebyte;
	}

	/**
	 * Load the executable with the specified name into this process, and prepare to
	 * pass it the specified arguments. Opens the executable, reads its header
	 * information, and copies sections and arguments into this process's virtual
	 * memory.
	 *
	 * @param name
	 *            the name of the file containing the executable.
	 * @param args
	 *            the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into memory.
	 * If this returns successfully, the process will definitely be run (this is the
	 * last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	// zjt 分配页表
	protected boolean loadSections() {
		// 多线程，加锁
		UserKernel.lock.acquire();
		// 所需大于空闲页表的需要 其中numpages的值由load()方法确定 现在开辟空间
		if (numPages > UserKernel.freePage.size()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}
		// 初始化创建页表
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; i++) { // 从全局空闲页表中拿出一页，进行分配
			int ppn = UserKernel.freePage.remove();
			pageTable[i] = new TranslationEntry(i, ppn, true, false, false, false);
		}
		UserKernel.lock.release();

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				pageTable[vpn].readOnly = section.isReadOnly();// 传递内存是否已经被读写
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		UserKernel.lock.acquire();
		for (int i = 0; i < numPages; i++)
			UserKernel.freePage.add(pageTable[i].ppn);
		UserKernel.lock.release();
	}

	/**
	 * Initialize the processor's registers in preparation for running the program
	 * loaded into this process. Set the PC register to point at the start function,
	 * set the stack pointer register to point at the top of the stack, set the A0
	 * and A1 registers to argc and argv, respectively, and initialize all other
	 * registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		// 判断是否是root进程 TODO
		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall
	 *            the syscall number.
	 * @param a0
	 *            the first syscall argument.
	 * @param a1
	 *            the second syscall argument.
	 * @param a2
	 *            the third syscall argument.
	 * @param a3
	 *            the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	// 对外调用接口
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallCreate:
			return handleCreate(a0);// a0 文件名称在虚拟内存中的地址
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);// 文件名称的地址 写入到虚拟内存的地址 写入内容的长度
		case syscallWrite:
			return handleWrite(a0, a1, a2);// 文件名称的地址 读取虚拟内存的地址 写入内容的长度
		case syscallUnlink:
			return handleUnlink(a0);// a0 文件名称在虚拟内存中的地址
		case syscallClose:
			return handleClose(a0);// a0 文件名称在虚拟内存中的地址
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallExit:
			return handleExit(a0);
		case syscallJoin:
			return handleJoin(a0,a1);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	private int handleJoin(int pid, int statuAddress) {
		// 判断是否是子进程
		processorlock.acquire();
		int i = 0;
		while (i < chindrenprocess.size() && chindrenprocess.get(i).pid != pid) {
			i++;
		}
		if (i == chindrenprocess.size()) {
			processorlock.release();
			return -1;// 不是自己的孩子
		}
		UserProcess up = chindrenprocess.get(i);
		processorlock.release();
		joinID = pid;
		boolean status = Machine.interrupt().disable(); // 关中断?????
		kthread = KThread.currentThread();
		KThread.sleep();
		Machine.interrupt().setStatus(status);// 中断还原
		byte[] childstat = new byte[4];
		Lib.bytesFromInt(childstat, 0, up.Status);
		// 写入虚拟内存的指定位置
		writeVirtualMemory(statuAddress, childstat);
		if (up.normExit)
			return 1;
		return 0;
	}

	// 线程退出
	private int handleExit(int status) {
		coff.close();
		for (int i = 0; i < openfile.length; i++) {
			if (openfile[i] != null)
				handleClose(i);
		}
		this.Status = status;// 保存退出状态
		normExit = true;// 正常退出
		if (fatherprocess != null)// 需维护父进程的子进程表
		{
			fatherprocess.processorlock.acquire();
			// 从父进程的子进程表中删除自己
			fatherprocess.chindrenprocess.remove(this);
			if (fatherprocess.joinID == pid) { // 父进程正在等待自己执行完毕
				boolean intStatus = Machine.interrupt().disable();
				fatherprocess.kthread.ready();// 唤醒父进程
				Machine.interrupt().restore(intStatus);
			}
			fatherprocess.processorlock.release();
		}
		unloadSections();// 释放内存
		pidlock.acquire();
		numOfRunningProcess--;// 总进程数减一
		if (numOfRunningProcess == 0)// 无用户级进程，停机
			Kernel.kernel.terminate();
		pidlock.release();
		KThread.finish();// 让thread结束
		return 0;
	}

	private int handleExec(int fileaddress, int argc, int argcaddress) {
		String filename = readVirtualMemoryString(fileaddress, 256);
		// 存在非法参数
		if (!(filename != null && argc >= 1 && argcaddress >= 0 && argcaddress <= pageSize * numPages))
			return -1;
		// 获得参数
		String args[] = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] argssAdress = new byte[4];// 获取参数的地址
			readVirtualMemory(argcaddress + i * 4, argssAdress);
			args[i] = readVirtualMemoryString(Lib.bytesToInt(argssAdress, 0), 256);
		}
		// 创建子进程
		UserProcess up = UserProcess.newUserProcess();
		if (!up.execute(filename, args))
			return -1;
		// 保存子进程指向父进程的引用
		up.processorlock.acquire();
		up.fatherprocess = this;
		up.processorlock.release();
		// 在自身的子进程队列中添加新进程
		processorlock.acquire();
		this.chindrenprocess.add(up);
		processorlock.release();
		// 返回子进程pid
		return up.pid;

	}

	// 实现私有方法 根据syscall 文件操作接口
	/**
	 * 实现文件打开
	 * 
	 * @param address
	 * @return
	 */
	private int handleOpen(int address) {
		// 根据地址读虚拟内存获取文件名，并限制文件名长度
		String filename = readVirtualMemoryString(address, 256);
		if (filename == null)// 若文件名不存在，出错
			return -1;
		return open(filename);
	}

	/**
	 * 实现文件的创建
	 * 
	 * @param address
	 * @return
	 */
	private int handleCreate(int address) {
		// 根据地址读虚拟内存获取文件名，并限制文件名长度
		String filename = readVirtualMemoryString(address, 256);
		if (filename == null)// 若文件名不存在，出错
			return -1;
		return create(filename);
	}

	/**
	 * 写入内容
	 * 
	 * @param fd
	 *            文件描述符
	 * @param buffer
	 *            缓冲区的位置
	 * @param size
	 *            写入文件的大小
	 * @return
	 */
	private int handleWrite(int fd, int buffer, int size) {
		if (fd < 0 || fd >= openfile.length || openfile[fd] == null)
			return -1;
		// 读取内存
		byte buffer_temp[] = new byte[size];

		int len = readVirtualMemory(buffer, buffer_temp);
		// 数量小于0，出错
		if (len < 0)
			return -1;

		// 调用文件系统的read方法
		int writeCount = openfile[fd].write(buffer_temp, 0, len);
		// 数量小于0，出错
		if (writeCount < 0)
			return -1;
		// 返回写入的长度
		return writeCount;
	}

	/**
	 * 读取文件
	 * 
	 * @param fd
	 *            文件标志符
	 * @param buffer
	 *            缓冲的位置
	 * @param size
	 *            读取的长度
	 * @return 返回写入虚拟内存中的真实大小
	 */
	private int handleRead(int fd, int buffer, int size) { // 判断文件是否真的存在
		if (fd < 0 || fd >= openfile.length || openfile[fd] == null)
			return -1;
		// 创建临时缓存
		byte buffer_temp[] = new byte[size];
		// 调用文件系统的read方法
		int readCount = openfile[fd].read(buffer_temp, 0, size);
		// 数量小于0，出错
		if (readCount < 0)
			return -1;
		// 写入虚拟内存 位置 内容 起始位置 长度
		int writeCount = writeVirtualMemory(buffer, buffer_temp, 0, readCount);
		// 数量小于0，出错
		if (writeCount < 0)
			return -1;
		// 返回数量
		return writeCount;//返回的是写入的数据长度
	}

	/**
	 * 关闭文件
	 * 
	 * @param fd
	 *            文件描述符
	 * @return
	 */
	private int handleClose(int fd) {
		// 检查文件的合法性
		if (fd < 0 || fd >= openfile.length || openfile[fd] == null)
			return -1;
		// 获取文件名称
		String filename = openfile[fd].getName();
		// 调用内核关闭 没有返回
		openfile[fd].close();
		openfile[fd] = null;

		// 维护文件列表
		lock.acquire();
		for (int i = 0; i < fileCounts.size(); i++) {
			FileCount temp = fileCounts.get(i);
			if (temp.filename.equals(filename)) {
				temp.count--;// 打开数量减一
				if (temp.count == 0)// 打开数量为零，从文件打开表中删除
				{
					fileCounts.remove(i);
					// 若该文件已经被调用unlink，而自己是最后一个关闭它的进程，故应该删除
					if (unlinkFiles.contains(filename))
						if (!ThreadedKernel.fileSystem.remove(filename)) {
							lock.release();
							// 文件已将被删除 所以文件关闭失败
							return -1;
						}
				}
				break;
			}
		}
		lock.release();
		return 0;

	}

	/**
	 * 
	 * @param address
	 *            虚拟内存的地址
	 * @return
	 */
	private int handleUnlink(int address) {
		String fn = readVirtualMemoryString(address, 256);
		if (fn == null)
			return -1; // 文件名称不存在
		return unlink(fn);
	}

	/**
	 * 
	 * @param filename
	 * @return
	 */
	private int unlink(String filename) {
		// 寻找文件的标志符 在打开文件中关闭该文件 但该文件不一定处于打开状态
		lock.acquire();
		for (int i = 0; i < fileCounts.size(); i++) {
			if (fileCounts.get(i).filename.equals(filename)) {
				if (!unlinkFiles.contains(filename))
					unlinkFiles.add(filename);
				// if(fileCounts.get(i).count==0)//如果文件直接被删除
				lock.release();
				break;
			}
		}
		lock.release();
		if (ThreadedKernel.fileSystem.remove(filename))
			return 0;
		return -1;// 没有找到该文件出错
	}

	/**
	 * 1. 减产文件是否已经在删除队列中 2. 寻找一个标志符来标记文件 若打开的文件过多，无法被记录的时候 失败 3. 调用内核申请打开文件。
	 * 若文件不存在，失败 4. 将文件进行关联 ， 记录该文件被打开的次数
	 * 
	 * @param filename
	 * @return 文件的位置 在数组中的下标
	 */
	private int open(String filename) {
		lock.acquire();// 申请锁，检查该文件是否在等待被删除的队列中 若是文件没有权限打开
		boolean isUnlink = unlinkFiles.contains(filename);
		lock.release();
		if (isUnlink)
			return -1;
		// 寻找一个空的文件描述符存储位置
		int fileDescriptor = findEmptyOpenFile();
		// 找不到，返回-1
		if (fileDescriptor == -1)
			return -1;
		// 调用文件系统的open方法，若文件没有，不主动进行创建
		openfile[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, false);
		// 打开文件失败，返回-1
		if (openfile[fileDescriptor] == null)
			return -1;
		else// 关联文件，记录该文件已经被打开了
		{
			boolean found = false;
			lock.acquire();// 申请锁，查看该文件是否已被打开
			for (int i = 0; i < fileCounts.size(); i++) {
				FileCount temp = fileCounts.get(i);
				if (temp.filename.equals(filename)) { // 已被打开，打开数+1
					temp.count++;
					found = true;
					break;
				}
			}
			if (!found)// 未被打开，添加
				fileCounts.add(new FileCount(filename));
			lock.release();
			// 返回文件描述符
			return fileDescriptor;
		}
	}

	/**
	 * 在打开的文件中寻找一个空的位置
	 * 
	 * @return
	 */
	private int findEmptyOpenFile() {
		for (int i = 0; i < openfile.length; i++) {
			if (openfile[i] == null)
				return i;
		}
		return -1;
	}

	/**
	 * 创建一个新的文件 检查创建的文件是否在删除列表中 为文件寻找一个文件标志符 创建文件 按照打开文件的步骤，记录文件被打开的次数(该操作为互异性操作)
	 * 
	 * @param name
	 * @return
	 */
	private int create(String filename) {
		lock.acquire();// 申请锁，检查该文件是否在等待被删除的队列中 若是文件没有权限打开
		boolean isUnlink = unlinkFiles.contains(filename);
		lock.release();
		if (isUnlink)
			return -1;
		// 寻找一个空的文件描述符存储位置
		int fileDescriptor = findEmptyOpenFile();
		// 找不到，返回-1
		if (fileDescriptor == -1)
			return -1;
		// 调用文件系统的open方法 若文件不存在允许创建
		openfile[fileDescriptor] = ThreadedKernel.fileSystem.open(filename, true);
		// 打开文件失败，返回-1
		if (openfile[fileDescriptor] == null)
			return -1;
		else// 关联文件，记录该文件已经被打开了
		{
			boolean found = false;
			lock.acquire();// 申请锁，查看该文件是否已被打开
			for (int i = 0; i < fileCounts.size(); i++) {
				FileCount temp = fileCounts.get(i);
				if (temp.filename.equals(filename)) { // 已被打开，打开数+1
					temp.count++;
					found = true;
					break;
				}
			}
			if (!found)// 未被打开，添加
				fileCounts.add(new FileCount(filename));
			lock.release();
			// 返回文件描述符
			return fileDescriptor;
		}
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;
	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	private OpenFile openfile[];// 记录已经打开的文件

	private static LinkedList<FileCount> fileCounts = new LinkedList<FileCount>();
	// unliked 文件 ，不能马上删除的
	private static LinkedList<String> unlinkFiles = new LinkedList<String>();
	// 实现文件的互斥访问
	public static Lock lock = new Lock();

	UserProcess fatherprocess;// 父进程
	LinkedList<UserProcess> chindrenprocess = new LinkedList<UserProcess>();
	// 保证this是自己
	Lock processorlock = new Lock();
	private int Status = -1;
	private int pid;
	public int joinID = -1;
	private KThread kthread;
	private boolean normExit;
	private static int numOfRunningProcess;
	private static int Staticpid;
	public static Lock pidlock = new Lock();

	/**
	 * 补充类 文件管理类 实现的功能 记录文件名称 和 被打开的次数
	 */
	private class FileCount {
		// 在openfile被创建的时候，定义了数组的长度，从而控制了线程最多能打开几个文件
		public FileCount(String filename) {
			this.filename = filename;
			count = 1;
		}

		public String filename;
		public int count;
	}
}
