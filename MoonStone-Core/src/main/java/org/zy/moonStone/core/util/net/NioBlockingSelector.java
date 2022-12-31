package org.zy.moonStone.core.util.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zy.moonStone.core.util.ExceptionUtils;
import org.zy.moonStone.core.util.collections.SynchronizedQueue;
import org.zy.moonStone.core.util.collections.SynchronizedStack;

/**
 * @dateTime 2022年1月24日;
 * @author zy(azurite-Y);
 * @description
 */
public class NioBlockingSelector {
	private static final Logger logger = LoggerFactory.getLogger(NioBlockingSelector.class);

    private final SynchronizedStack<KeyReference> keyReferenceStack = new SynchronizedStack<>();

    protected Selector sharedSelector;

    protected BlockPoller poller;

    public void open(String name, Selector selector) {
        sharedSelector = selector;
        poller = new BlockPoller();
        poller.selector = sharedSelector;
        poller.setDaemon(true);
        poller.setName(name + "-BlockPoller");
        poller.start();
    }

    public void close() {
        if (poller != null) {
            poller.disable();
            poller.interrupt();
            poller = null;
        }
    }

    /**
     * 使用字节缓冲区对要写的数据执行阻塞写操作。如果选择器参数为空，那么它将执行一个繁忙的写操作，可能会占用大量的CPU周期。
     *
     * @param buf ByteBuffer - ByteBuffer——包含数据的缓冲区，只要(buf.hasRemaining()==true)
     * @param nioChannel - 写入数据的套接字
     * @param writeTimeout - 此写操作的超时时间(以毫秒为单位)，-1表示没有超时
     * @return 写入的字节数
     * 
     * @throws EOFException - 如果write返回-1
     * @throws SocketTimeoutException - 如果写超时
     * @throws IOException - 如果底层套接字逻辑中发生了IO异常
     */
    public int write(ByteBuffer buf, NioChannel nioChannel, long writeTimeout) throws IOException {
        SelectionKey key = nioChannel.getIOChannel().keyFor(nioChannel.getSocketWrapper().getPoller().getSelector());
        if (key == null) {
            throw new IOException("SelectionKey 未注册");
        }
        KeyReference reference = keyReferenceStack.pop();
        if (reference == null) {
            reference = new KeyReference();
        }
        NioSocketWrapper att = (NioSocketWrapper) key.attachment();
        int written = 0;
        boolean timedout = false;
        int keycount = 1; // 假定可以写
        long time = System.currentTimeMillis(); // 超时起始时间
        try {
            while (!timedout && buf.hasRemaining()) {
                if (keycount > 0) { // 只有在我们注册了写的时候才写
                    int cnt = nioChannel.write(buf); // 写入数据
                    if (cnt == -1) {
                        throw new EOFException();
                    }
                    written += cnt;
                    if (cnt > 0) {
                        time = System.currentTimeMillis(); // 超时重置时间
                        continue; // 已成功写入，在没有选择器的情况下重试
                    }
                }
                try {
                    if (att.getWriteLatch() == null || att.getWriteLatch().getCount() == 0) {
                        att.startWriteLatch(1);
                    }
                    poller.add(att, SelectionKey.OP_WRITE, reference);
                    att.awaitWriteLatch(AbstractEndpoint.toTimeout(writeTimeout), TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignore) {
                    // Ignore
                }
                if (att.getWriteLatch() != null && att.getWriteLatch().getCount() > 0) {
                    // 被中断
                    keycount = 0;
                } else {
                    // 闩锁倒计时已经开始
                    keycount = 1;
                    att.resetWriteLatch();
                }

                if (writeTimeout > 0 && (keycount == 0)) {
                    timedout = (System.currentTimeMillis() - time) >= writeTimeout;
                }
            }
            if (timedout) {
                throw new SocketTimeoutException();
            }
        } finally {
            poller.remove(att, SelectionKey.OP_WRITE);
            if (timedout && reference.key != null) {
                poller.cancelKey(reference.key);
            }
            reference.key = null;
            keyReferenceStack.push(reference);
        }
        return written;
    }

    /**
     * 如果选择器参数为null，那么它将执行一个繁忙的读取，这可能会占用大量的CPU周期。
     *
     * @param buf - 包含数据的缓冲区，我们将一直读取，直到读取至少一个字节或超时
     * @param socket - 写入数据的套接字
     * @param readTimeout - 这个读操作的超时时间(以毫秒为单位)，-1表示没有超时
     * @return 读取的字节数
     * 
     * @throws EOFException - 如果read返回-1
     * @throws SocketTimeoutException - 如果读取超时
     * @throws IOException - 如果底层套接字逻辑中发生了IO异常
     */
    public int read(ByteBuffer buf, NioChannel socket, long readTimeout) throws IOException {
        SelectionKey key = socket.getIOChannel().keyFor(socket.getSocketWrapper().getPoller().getSelector());
        if (key == null) {
            throw new IOException("SelectionKey 未注册");
        }
        KeyReference reference = keyReferenceStack.pop();
        if (reference == null) {
            reference = new KeyReference();
        }
        NioSocketWrapper att = (NioSocketWrapper) key.attachment();
        int read = 0;
        boolean timedout = false;
        int keycount = 1; // 假设可读
        long time = System.currentTimeMillis(); // 超时起始时间
        try {
            while (!timedout) {
                if (keycount > 0) { // 只有注册了才读
                    read = socket.read(buf);
                    if (read != 0) {
                        break;
                    }
                }
                try {
                    if (att.getReadLatch()==null || att.getReadLatch().getCount()==0) {
                        att.startReadLatch(1);
                    }
                    poller.add(att,SelectionKey.OP_READ, reference);
                    att.awaitReadLatch(AbstractEndpoint.toTimeout(readTimeout), TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignore) {
                    // Ignore
                }
                if ( att.getReadLatch()!=null && att.getReadLatch().getCount()> 0) {
                    // 被中断
                    keycount = 0;
                }else {
                    // 闩锁倒计时已发生
                    keycount = 1;
                    att.resetReadLatch();
                }
                if (readTimeout >= 0 && (keycount == 0)) {
                    timedout = (System.currentTimeMillis() - time) >= readTimeout;
                }
            }
            if (timedout) {
                throw new SocketTimeoutException();
            }
        } finally {
            poller.remove(att,SelectionKey.OP_READ);
            if (timedout && reference.key != null) {
                poller.cancelKey(reference.key);
            }
            reference.key = null;
            keyReferenceStack.push(reference);
        }
        return read;
    }

    /**
     * 处理已添加到 {@link #events } 集合中的 {@link Runnable } 实现
     */
    protected static class BlockPoller extends Thread {
        protected volatile boolean run = true;
        protected Selector selector = null;
        protected final SynchronizedQueue<Runnable> events = new SynchronizedQueue<>();
        
        public void disable() {
            run = false;
            selector.wakeup();
        }
        protected final AtomicInteger wakeupCounter = new AtomicInteger(0);

        public void cancelKey(final SelectionKey key) {
            Runnable r = new RunnableCancel(key);
            events.offer(r);
            wakeup();
        }

        public void wakeup() {
            if (wakeupCounter.addAndGet(1)==0) selector.wakeup();
        }

        public void cancel(SelectionKey sk, NioSocketWrapper key, int ops){
            if (sk != null) {
                sk.cancel();
                sk.attach(null);
                if (SelectionKey.OP_WRITE == (ops & SelectionKey.OP_WRITE)) {
                    countDown(key.getWriteLatch());
                }
                if (SelectionKey.OP_READ == (ops & SelectionKey.OP_READ)) {
                    countDown(key.getReadLatch());
                }
            }
        }

        public void add(final NioSocketWrapper key, final int ops, final KeyReference ref) {
            if (key == null) {
                return;
            }
            NioChannel nch = key.getSocketChannel();
            final SocketChannel ch = nch.getIOChannel();
            if (ch == null) {
                return;
            }
            Runnable r = new RunnableAdd(ch, key, ops, ref);
            events.offer(r);
            wakeup();
        }

        public void remove(final NioSocketWrapper key, final int ops) {
            if (key == null) {
                return;
            }
            NioChannel nch = key.getSocketChannel();
            final SocketChannel ch = nch.getIOChannel();
            if (ch == null) {
                return;
            }
            Runnable r = new RunnableRemove(ch, key, ops);
            events.offer(r);
            wakeup();
        }

        public boolean events() {
            Runnable r = null;
            /**
             * 只在启动此方法时轮询并运行可运行事件。以后添加到队列中的事件将被延迟到此方法的下一次执行。
             * 
             * 因为从事件队列中运行事件可能导致工作线程向队列添加更多的事件(例如，当被获得无效的SelectionKey的前一个RunnableAdd事件唤醒时，工作线程可能添加另一个RunnableAdd事件)。
             * 试图消耗一个不断增加的队列中的所有事件，直到队列为空，将使循环难以终止，这将消耗大量时间，并极大地影响轮询器循环的性能。
             */
            int size = events.size();
            for (int i = 0; i < size && (r = events.poll()) != null; i++) {
                r.run();
            }
            return (size > 0);
        }

        @Override
        public void run() {
            while (run) {
                try {
                    events();
                    int keyCount = 0;
                    try {
                        int i = wakeupCounter.get();
                        if (i > 0) {
                            keyCount = selector.selectNow();
                        } else {
                            wakeupCounter.set(-1);
                            keyCount = selector.select(1000);
                        }
                        wakeupCounter.set(0);
                        if (!run) {
                            break;
                        }
                    } catch (NullPointerException x) {
                        if (selector == null) {
                            throw x;
                        }
                        if (logger.isDebugEnabled()) {
                            logger.debug("在windows JDK 1.5上可能遇到sun bug 5076772", x);
                        }
                        continue;
                    } catch (CancelledKeyException x) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("在windows JDK 1.5上可能遇到sun bug 5076772", x);
                        }
                        continue;
                    } catch (Throwable x) {
                        ExceptionUtils.handleThrowable(x);
                        logger.error("Selector 异常", x);
                        continue;
                    }

                    Iterator<SelectionKey> iterator = keyCount > 0 ? selector.selectedKeys().iterator() : null;

                    // 遍历就绪 SelectionKey 集合并分派任何活动事件
                    while (run && iterator != null && iterator.hasNext()) {
                        SelectionKey sk = iterator.next();
                        NioSocketWrapper socketWrapper = (NioSocketWrapper) sk.attachment();
                        try {
                            iterator.remove();
                            // 除去 SelectionKey 已有的就绪操作集
                            sk.interestOps(sk.interestOps() & (~sk.readyOps()));
                            if (sk.isReadable()) {
                                countDown(socketWrapper.getReadLatch());
                            }
                            if (sk.isWritable()) {
                                countDown(socketWrapper.getWriteLatch());
                            }
                        } catch (CancelledKeyException ckx) {
                            sk.cancel();
                            countDown(socketWrapper.getReadLatch());
                            countDown(socketWrapper.getWriteLatch());
                        }
                    }
                } catch (Throwable t) {
                    logger.error("Nio 阻塞选择器处理异常", t);
                }
            }
            events.clear();

            // 如果使用共享选择器，NioSelectorPool也会尝试关闭选择器。尽量避免出现closeselectoreexception，因为涉及到多个线程，所以总是有可能出现异常。
            if (selector.isOpen()) {
                try {
                    // 取消所有剩余 SelectionKey
                    selector.selectNow();
                } catch (Exception ignore) {
                    if (logger.isDebugEnabled())
                        logger.debug("", ignore);
                }
            }
            try {
                selector.close();
            } catch (Exception ignore) {
                if (logger.isDebugEnabled())
                    logger.debug("", ignore);
            }
        }

        public void countDown(CountDownLatch latch) {
            if (latch == null) {
                return;
            }
            latch.countDown();
        }

        /**
         * 通过指定的 {@link SocketChannel } 注册指定的 {@link SelectionKey } ops值
         */
        private class RunnableAdd implements Runnable {
            private final SocketChannel ch;
            private final NioSocketWrapper key;
            private final int ops;
            private final KeyReference ref;

            public RunnableAdd(SocketChannel ch, NioSocketWrapper key, int ops, KeyReference ref) {
                this.ch = ch;
                this.key = key;
                this.ops = ops;
                this.ref = ref;
            }

            @Override
            public void run() {
                SelectionKey sk = ch.keyFor(selector);
                try {
                    if (sk == null) {
                        sk = ch.register(selector, ops, key);
                        ref.key = sk;
                    } else if (!sk.isValid()) {
                        cancel(sk, key, ops);
                    } else {
                        sk.interestOps(sk.interestOps() | ops);
                    }
                } catch (CancelledKeyException cx) {
                    cancel(sk, key, ops);
                } catch (ClosedChannelException cx) {
                    cancel(null, key, ops);
                }
            }
        }

        /**
         * 通过指定的 {@link SocketChannel } 删除指定的 {@link SelectionKey } 
         */
        private class RunnableRemove implements Runnable {
            private final SocketChannel ch;
            private final NioSocketWrapper key;
            private final int ops;

            public RunnableRemove(SocketChannel ch, NioSocketWrapper key, int ops) {
                this.ch = ch;
                this.key = key;
                this.ops = ops;
            }

            @Override
            public void run() {
                SelectionKey sk = ch.keyFor(selector);
                try {
                    if (sk == null) {
                        if (SelectionKey.OP_WRITE == (ops & SelectionKey.OP_WRITE)) {
                            countDown(key.getWriteLatch());
                        }
                        if (SelectionKey.OP_READ == (ops & SelectionKey.OP_READ)) {
                            countDown(key.getReadLatch());
                        }
                    } else {
                        if (sk.isValid()) {
                            sk.interestOps(sk.interestOps() & (~ops));
                            if (SelectionKey.OP_WRITE == (ops & SelectionKey.OP_WRITE)) {
                                countDown(key.getWriteLatch());
                            }
                            if (SelectionKey.OP_READ == (ops & SelectionKey.OP_READ)) {
                                countDown(key.getReadLatch());
                            }
                            if (sk.interestOps() == 0) {
                                sk.cancel();
                                sk.attach(null);
                            }
                        } else {
                            sk.cancel();
                            sk.attach(null);
                        }
                    }
                } catch (CancelledKeyException cx) {
                    if (sk != null) {
                        sk.cancel();
                        sk.attach(null);
                    }
                }
            }
        }

        /**
         * 处理 {@link SelectionKey } 的取消
         */
        public static class RunnableCancel implements Runnable {
            private final SelectionKey key;

            public RunnableCancel(SelectionKey key) {
                this.key = key;
            }

            @Override
            public void run() {
                key.cancel();
            }
        }
    }

    /**
     * 传递 {@link SelectionKey } 
     */
    public static class KeyReference {
        SelectionKey key = null;

        @Override
        protected void finalize() {
            if (key != null && key.isValid()) {
                logger.warn("nioBlockingSelector.possibleLeak");
                try {
                    key.cancel();
                } catch (Exception ignore) {
                }
            }
        }
    }
}
