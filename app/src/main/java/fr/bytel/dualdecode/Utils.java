package fr.bytel.dualdecode;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.TimeoutException;

public class Utils {
    private static final String LOG_TAG = Utils.class.getSimpleName();
    private Utils() {}

    private static class FutureRunnable implements Runnable {
        final Runnable task;
        final long timeout = SystemClock.uptimeMillis()+8000L;
        long startTime;
        private boolean isDone;

        private FutureRunnable(Runnable task) {
            this.task = task;
        }

        @Override
        public void run() {
            startTime = System.nanoTime();
            task.run();
            isDone = true;
        }

        boolean isTimeout() throws TimeoutException {
            if (SystemClock.uptimeMillis() > timeout){
                throw new TimeoutException("timeout while executing "+task+" on main thread");
            }
            return false;
        }
    }

    public static void runInLooperThread(Handler handler, Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        if (handler.getLooper() == Looper.myLooper()) {
            runnable.run();
        } else {
            handler.post(runnable);
        }
    }

    public static void runInLooperThreadAndWait(@NonNull Handler ahandler, @NonNull Runnable runnable) {
        if (runnable == null) throw new NullPointerException();
        if (ahandler.getLooper() == Looper.myLooper()) {
            runnable.run();
        } else {
            final FutureRunnable temp = new FutureRunnable(runnable);
            ahandler.post(temp);
            try {
                boolean interrupted = false;
                final String WAIT_MAIN_THREAD = "WAIT_MAIN_THREAD"+Math.random(); //one instance per call
                long waitTime = 5;
                while (!temp.isDone && !temp.isTimeout() && !interrupted) {
                    synchronized (WAIT_MAIN_THREAD) {
                        try {
                            WAIT_MAIN_THREAD.wait(waitTime);
                        } catch (InterruptedException e) {
                            interrupted = true;
                        }
                        waitTime += 5;
                    }
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "runInLooperThreadAndWait: Exception on thread execution",e);
            }
        }
    }

}