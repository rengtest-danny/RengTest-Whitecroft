package danny.util;

import java.util.*;

/**
 * Integrating some Thread methods with TimerTask
 *
 * @author Danny Wong
 */
public abstract class TimerThread extends TimerTask
{
    private Thread thread;
    private State thState = State.NEW;
    private int priority = Thread.NORM_PRIORITY;
    private final String threadName;

    /**
     * Creates a new TimerThread
     */
    protected TimerThread () { threadName = null; }

    protected TimerThread ( int priority )
    {
        this.priority = priority;
        threadName = null;
    }

    protected TimerThread ( String threadName ) { this.threadName = threadName; }

    protected TimerThread ( String threadName, int priority )
    {
        this.threadName = threadName;
        this.priority = priority;
    }

    public State getState () { return thState; }

    public final int getPriority () { return priority; }

    public final void setPriority ( int priority ) { this.priority = priority; }

    private final void start ()
    {
        if ( null == thread ) thread = Thread.currentThread();
        thread.setPriority( priority );
        if ( !State.CANCELLED.equals( thState ) && !State.TERMINATED.equals( thState ) )
        {
            thState = State.EXECUTING;
        }
    }

    private final void end ()
    {
        if ( null == thread ) thread = Thread.currentThread();
        if ( State.CANCELLED.equals( thState ) )
        {
            thState = State.TERMINATED;
            super.cancel();
        }
        else
        {
            thState = State.SCHEDULED;
        }
    }

    public final void join () throws InterruptedException
    {
        cancel( true );
        while ( !State.TERMINATED.equals( thState ) ) { Thread.sleep( 50 ); }
    }

    public final boolean waitEnd ( boolean interrupt, long maxWaitingTime )
    {
        return this.waitEnd( interrupt, maxWaitingTime, "waiting..." );
    }

    public final boolean waitEnd ( boolean interrupt, long maxWaitingTime, String waitMessage )
    {
        if ( null == thread ) return true;
        long now = System.currentTimeMillis();
        cancel( interrupt );
        while ( State.TERMINATED.equals( thState ) )
        {
            if ( System.currentTimeMillis() - now >= maxWaitingTime ) return false;
            try { Thread.sleep( 50 ); }
            catch ( Exception e ) {}
        }
        return true;
    }

    /**
     * this sleep method will be skipped if the timer thread is not executing
     *
     * @return true if sleep operation have been processed, false if interrupted or timertask is cancelled
     */
    protected final boolean sleep ( long millis )
    {
        if ( null == thread ) thread = Thread.currentThread();
        if ( State.EXECUTING.equals( thState ) )
        {
            try
            {
                thread.sleep( millis );
                return true;
            }
            catch ( InterruptedException ie )
            {
            }
        }
        return false;
    }

    /**
     * cancel this timer thread
     */
    public final boolean cancel ()
    {
        boolean result = super.cancel();
        if ( State.NEW.equals( thState ) ) thState = State.TERMINATED;
        if ( State.SCHEDULED.equals( thState ) ) thState = State.TERMINATED;
        if ( State.SCHEDULED.equals( thState ) ) return true;
        thState = State.CANCELLED;
        return result;
    }

    /**
     * cancel this timer thread with interrupting the timertask
     */
    public final boolean cancel ( boolean interrupt )
    {
        boolean result = this.cancel();
        if ( null != thread && interrupt ) thread.interrupt();
        return result;
    }

    public final void interrupt ()
    {
        if ( null != thread ) thread.interrupt();
    }

    public String getName ()
    {
        if ( null == threadName ) return "TimerThread";
        return threadName;
    }

    public String toString ()
    {
        return getName();
    }

    /**
     * the action to be performed by this timer thread,
     */
    public final void run ()
    {
        start();
        try { runTask(); }
        catch ( Exception e ) {}
        end();
    }

    public abstract void runTask ();

    public enum State
    {
        /**
         * not yet scheduled and executed
         */
        NEW,
        /**
         * scheduled, but not executing
         */
        SCHEDULED,
        /**
         * scheduled and executing
         */
        EXECUTING,
        /**
         * cancelled and executing the last time
         */
        CANCELLED,
        /**
         * cancelled and finish executing the last time
         */
        TERMINATED;
    }
}
