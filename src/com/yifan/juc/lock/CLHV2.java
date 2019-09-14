package com.yifan.juc.lock;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.LockSupport;

/**
 * @author yifan
 * @since 2019/8/28 16:04
 */
public class CLHV2 {

    /**
     * CLH锁节点状态
     * 每个希望获取锁的线程都被封装为一个节点对象
     */
    private static class CLHNode {

        /**
         * 默认状态为true - 即处于等待状态或者加锁成功(换言之，即此节点处于有效的一种状态)
         */
        volatile boolean active = true;

        /**
         * 用来lock()的次数
         */
        int lockCount = 0;

        /**
         * 需要被唤醒的线程
         */
        volatile Thread notify;

    }

    /**
     * 隐式链表最末等待节点
     */
    private volatile CLHNode tail = null;

    /**
     * 线程对应CLH节点映射
     */
    private ThreadLocal<CLHNode> currentThreadNode = new ThreadLocal<>();

    /**
     * 原子更新器
     */
    private static final AtomicReferenceFieldUpdater<CLHV2, CLHNode> UPDATER = AtomicReferenceFieldUpdater
            .newUpdater(CLHV2.class, CLHNode.class, "tail");

    /**
     * CLH加锁
     */
    public void lock() {
        CLHNode node = currentThreadNode.get();
        if (node == null) {
            node = new CLHNode();
            currentThreadNode.set(node);
        }
        if (node.lockCount > 0) {
            // 重入的情况，直接获得锁
            // 锁次数加1
            node.lockCount += 1;
            return;
        }
        // 将自己设置成队尾，并获取自己的前驱
        CLHNode predecessor = UPDATER.getAndSet(this, node);
        if (predecessor != null) {
            // 等待前驱节点状态变更
            while (predecessor.active) {
                predecessor.notify = Thread.currentThread();
                LockSupport.park();
            }
        }
        // 锁次数加1
        node.lockCount += 1;
    }

    /**
     * CLH释放锁
     */
    public void unlock() {
        CLHNode node = currentThreadNode.get();
        // 只有持有锁的线程才能够释放
        if (node == null || !node.active) {
            return;
        }
        // 锁次数减1
        node.lockCount -= 1;
        if (node.lockCount > 0) {
            return;
        }
        // 从映射关系中移除当前线程对应的节点
        currentThreadNode.remove();
        // 尝试将tail从currentThread变更为null，因此当tail不为currentThread时表示还有线程在等待加锁
        if (!UPDATER.compareAndSet(this, node, null)) {
            // 不仅只有当前线程，还有后续节点线程的情况 - 将当前线程的锁状态置为false，因此其后继节点的lock自旋操作可以退出
            while (node.notify == null) {
                // 可能之前的线程设置了tail，但是还没有设置前驱的notify，等一下它
                System.out.println("Wait notify thread not null....");
            }
            // 唤醒等待的线程
            node.active = false;
            LockSupport.unpark(node.notify);
        }
    }
}