package cs455.scaling.util;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import cs455.scaling.task.ReadAndRespond;
import cs455.scaling.task.Task;

public class ThreadPoolManager extends Thread {
    private static final LinkedBlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>();
    private static Batch currentBatch;
    public static HashMap<SocketChannel, Long> clientMessagesSent = new HashMap<>();
    //should never have more than 1000 threads running at once
    //sem is needed to control problem of having any number able to add
    //but needing to block all of them if creating a new batch
    public static Semaphore batchSem = new Semaphore(1000);
    private static BlockingQueue<Worker> threads = new LinkedBlockingQueue<>();
    private long batchTime;
    private long batchSize;

    public ThreadPoolManager(long batchSize, long batchTime) {
        this.batchSize = batchSize;
        this.batchTime = batchTime;
    }

    public void startWorkers(int threadPoolSize) {
        for (int i = 0; i < threadPoolSize; i++) {
            Worker worker = new Worker();
            worker.start();
        }
    }

    public static LinkedBlockingQueue<Task> getTaskQueue() {
        return taskQueue;
    }

    public static void setCurrentBatch(Batch batch) {
        currentBatch = batch;
    }

    public static Batch getCurrentBatch () {
        return currentBatch;
    }

    public static class Worker extends Thread {
        private static int instances = 0;
        private Task task;

        public Worker() {
            task = null;
            instances++;
        }

        //allocate work according to thread pool in an infinite loop
        @Override
        public void run() {
            while (true) {
                try {
                    task = ThreadPoolManager.addToPool(this);
                    if (task != null)  {
                        task.execute();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /*
    the pool manager should check for different jobs that need to be done.
    it needs to keep track of the batch timer and process the current batch when that is up

    it also needs to allocate threads to accept connections, read data in, and process batches.

     */

    public static Task addToPool(Worker thread) {
        synchronized (thread) {
            try {
                threads.add(thread);
                thread.wait();
                synchronized (taskQueue) {
                    if (!taskQueue.isEmpty()) {
                        //grab a task out of the front of the queue and resolve it in the thread that is calling this method
                        return taskQueue.remove();
                    }
                    return null;
                }
            } catch (InterruptedException e) {
                return null;
            }
        }
    }

    //run method that will keep a thread spinning on checking batch times and
    //creating new batches
    //only 1 instance of ThreadPoolManager should ever exist at one.
    public void run() {
        Long batchStart = System.currentTimeMillis();
        ReadAndRespond sendTask;
        long statTime = System.currentTimeMillis();
        //initialize batch and time

        while (true) {
            Long time = System.currentTimeMillis();
            boolean batchTimeReached = time - batchStart > batchTime;
            boolean batchSizeReached = (batchSize <= currentBatch.getCount());
            if (!threads.isEmpty()) {
                Worker next = threads.remove();
                synchronized (next) {
                    next.notify();
                }
            }

            if (batchSizeReached || batchTimeReached) {
                try {
                    //aquire all permits so you wait for all adding to
                    //finish then prevent adding until you release them
                    batchSem.acquire(1000);
                    batchStart = System.currentTimeMillis();
                    sendTask = new ReadAndRespond(currentBatch);
                    currentBatch = new Batch();
                    taskQueue.add(sendTask);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    batchSem.release(1000);
                }
            }

            //compute statistics and print every 20 seconds
            if (time - statTime > 20000) {
                ArrayList<Long> sentCounts = new ArrayList<>();
                System.out.println("Stat time");
                for (SocketChannel client : clientMessagesSent.keySet()) {
                    synchronized (clientMessagesSent) {
                        Long sent = clientMessagesSent.get(client);
                        sentCounts.add(sent);
                    }
                }
                synchronized (clientMessagesSent) {
                    clientMessagesSent = new HashMap<>();
                }

                double throughput = 0;
                for (Long number : sentCounts) {
                    throughput += number;
                }

                double standardDeviation = 0;

                throughput /= 20;
                double numClients = sentCounts.size();
                double mean = throughput / numClients;

                for (Long number : sentCounts) {
                    standardDeviation += Math.pow((double) (number / 20) - mean, 2);
                }
                standardDeviation = Math.sqrt(standardDeviation / numClients);

                System.out.println("Server Throughput: " + throughput);
                System.out.println("Active Clients: " + numClients);
                System.out.println(" Mean Per-Client Throughput: " + mean);
                System.out.println(" STD of Mean Throughput: " + standardDeviation);

                statTime = System.currentTimeMillis();
            }
        }
    }
}