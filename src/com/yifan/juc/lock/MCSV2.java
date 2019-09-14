package com.yifan.juc.lock;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author yifan
 * @since 2019/8/28 11:34
 */
public class MCSV2 {

    /**
     * MCS锁节点状态
     * 每个希望获取锁的线程都被封装为一个节点对象
     */
    private class MCSNode {

        /**
         * 后继节点
         */
        volatile MCSNode next;

        /**
         * 默认状态为等待锁
         */
        volatile boolean blocked = true;

        /**
         * 这个节点的线程
         */
        volatile Thread thread;

        public MCSNode() {
            thread = Thread.currentThread();
        }
    }

    /**
     * 指向最后一个申请锁的MCSNode
     */
    private volatile MCSNode tail;

    /**
     * 线程到节点的映射
     */
    private ThreadLocal<MCSNode> currentThreadNode = new ThreadLocal<>();

    /**
     * 原子更新器
     */
    private static final AtomicReferenceFieldUpdater<MCSV2, MCSNode> UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(MCSV2.class, MCSNode.class, "tail");

    public void lock() {
        MCSNode cNode = currentThreadNode.get();
        if (cNode == null) {
            cNode = new MCSNode();
            currentThreadNode.set(cNode);
        }
        if (!cNode.blocked) {
            // 重入
            // 如果这里没有这个判断，重入的情况：unlock释放后，currentThreadNode中已经没有这个节点了，但是tail字段不是null，那么其它线程就会死锁
            return;
        }
        // step 1: 将tail设置成当前线程（CAS直到设置成功）
        MCSNode predecessor = UPDATER.getAndSet(this, cNode);
        if (predecessor != null) {
            // step 2：将后继改成自己（这一步需要和unlock配合）
            predecessor.next = cNode;
            while (cNode.blocked) {
                // 可以一直占用CPU，也可以阻塞自己
                LockSupport.park(Thread.currentThread());
            }
        } else {
            // 之前的tail是null，表示没有其它线程获得锁，当前线程直接获得锁
            cNode.blocked = false;
        }
    }


    /**
     * MCS释放锁操作
     */
    public void unlock() {
        // 获取当前线程对应的节点
        MCSNode cNode = currentThreadNode.get();
        if (cNode == null || cNode.blocked) {
            // 之前没有执行过lock，或者执行了lock()，但是未获得锁，这时候释放锁没有任何意义
            return;
        }
        if (cNode.next == null && !UPDATER.compareAndSet(this, cNode, null)) {
            // 如果当前节点的后继节点是null，将tail设置成null
            // 如果CAS操作失败了表示突然有节点排在自己后面了(lock中的step1执行了，但是step2还未执行就会出现这种情况)
            // 等待直到lock中的step2执行完成
            while (cNode.next == null) {
            }
        }

        if (cNode.next != null) {
            // 通知后继节点可以获取锁
            cNode.next.blocked = false;
            LockSupport.unpark(cNode.next.thread);
            // 将当前节点从链表中断开，方便对当前节点进行GC
            cNode.next = null;
        }
        // 清空当前线程对应的节点信息
        currentThreadNode.remove();
    }
}
