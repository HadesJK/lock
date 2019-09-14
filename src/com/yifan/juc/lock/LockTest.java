package com.yifan.juc.lock;

import java.util.Random;

import com.yifan.juc.lock.CLH;
import com.yifan.juc.lock.CLHV2;
import com.yifan.juc.lock.MCS;
import com.yifan.juc.lock.MCSV2;

/**
 * @author yifan
 * @since 2019/8/28 17:31
 */
public class LockTest {

    /**
     * 用例
     *
     * @param args
     */
    public static void main(String[] args) throws InterruptedException {
        Random random = new Random(System.currentTimeMillis());
        int testLoop = 20;
        System.out.println("================>测试CLH");
        for (int i = 0; i < 20; i ++) {
            int loop = random.nextInt(200);
            if (loop < 100) {
                loop += 100;
            }
            test(new Lock(new CLH()), loop);
        }

        System.out.println("================>测试CLHV2");
        for (int i = 0; i < testLoop; i ++) {
            int loop = random.nextInt(20000);
            if (loop < 10000) {
                loop += 10000;
            }
            test(new Lock(new CLHV2()), loop);
        }

//        System.out.println("测试MCS");
//        for (int i = 0; i < testLoop; i ++) {
//            int loop = random.nextInt(200);
//            if (loop < 100) {
//                loop += 100;
//            }
//            test(new Lock(new MCS()), loop);
//        }

//        System.out.println("测试MCSV2");
//        for (int i = 0; i < testLoop; i ++) {
//            int loop = random.nextInt(20000);
//            if (loop < 10000) {
//                loop += 10000;
//            }
//            test(new Lock(new MCSV2()), loop);
//        }
    }

    private static void test(Lock lock, int loop) throws InterruptedException {
        System.out.print("loop ====>" + loop + "\t");
        int reentrantCont = 5;
        // 测试正确性
        Count count = new Count();
        for (int i = 1; i <= loop; i++) {
            new Thread(() -> {
                try {
                    lock.lock();
                    count.value++;
                    // 测试可重入性
                    reentrant(lock, reentrantCont, count);
                } finally {
                    lock.unlock();
                }
            }).start();
        }
        int retries = 30;
        while (retries > 0) {
            retries--;
            if (count.value == loop * (reentrantCont + 1)) {
                break;
            }
            Thread.sleep(1000);
        }
        if (count.value != loop * (reentrantCont + 1)) {
            System.err.println("Count value=" + count.value + ", loop=" + loop + ", Lock=" + lock.lk.getClass());
        }
        // 测试可重入性
        reentrant(lock, 10, count);
        try {
            lock.lock();
        } finally {
            lock.unlock();
        }
        System.out.println("======>" + lock.lk.getClass() + "测试正确");
    }

    private static void reentrant(Lock lock, int reentrantCount, Count count) {
        lock.lock();
        try {
            count.value ++;
            if (reentrantCount > 1) {
                reentrant(lock, reentrantCount - 1, count);
            }
        } finally {
            lock.unlock();
        }
    }

    private static class Count {
        int value;
    }

    private static class Lock {
        private Object lk;

        public Lock(Object lk) {
            this.lk = lk;
        }

        void lock() {
            if (lk instanceof CLH) {
                ((CLH)lk).lock();
            } else if (lk instanceof CLHV2) {
                ((CLHV2)lk).lock();
            } else if (lk instanceof MCS) {
                ((MCS)lk).lock();
            } else if (lk instanceof MCSV2) {
                ((MCSV2)lk).lock();
            } else {
                throw new UnsupportedOperationException();
            }
        }

        void unlock() {
            if (lk instanceof CLH) {
                ((CLH)lk).unlock();
            } else if (lk instanceof CLHV2) {
                ((CLHV2)lk).unlock();
            } else if (lk instanceof MCS) {
                ((MCS)lk).unlock();
            } else if (lk instanceof MCSV2) {
                ((MCSV2)lk).unlock();
            } else {
                throw new UnsupportedOperationException();
            }
        }
    }
}
