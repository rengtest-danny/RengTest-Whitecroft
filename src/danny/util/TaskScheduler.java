package danny.util;

import java.awt.*;
import java.awt.event.*;
import java.text.*;
import java.util.*;

import java.io.*;
import java.net.*;

/**
 * Write a description of TaskScheduler here.
 *
 * @author Danny Wong
 */
public class TaskScheduler implements Runnable
{
    /**
     * public use task scheduler
     */
    public volatile static TaskScheduler commonScheduler;
    private static final Object staticlock = new Object();

    public static TaskScheduler commonScheduler ()
    {
        return commonScheduler( false );
    }

    public static TaskScheduler commonScheduler ( boolean withTrace )
    {
        if ( null == commonScheduler )
        {
            synchronized ( staticlock )
            {
                if ( null == commonScheduler )
                {
                    commonScheduler = new TaskScheduler( "Common Scheduler" );
                    if ( withTrace ) commonScheduler.enableTrace();
                    else commonScheduler.disableTrace();
                }
            }
        }
        return commonScheduler;
    }

    public static void enableNetworkRefreshTrigger () throws IOException
    {
        final ServerSocket server = new ServerSocket( 8855 );
        new Thread( "Scheduler Refresh Trigger" )
        {
            public void run ()
            {
                setPriority( Thread.MIN_PRIORITY );
                while ( true )
                {
                    Socket conn = null;
                    PrintStream ps = null;
                    try
                    {
                        conn = server.accept();
                        ps = new PrintStream( conn.getOutputStream(), true );
                        refreshAllSchedule( ps );
                        ps.flush();
                    }
                    catch ( Exception e )
                    {

                    }
                    finally
                    {
                        if ( null != ps )
                        {
                            try { ps.close(); }
                            catch ( Exception e ) {}
                        }
                        if ( null != conn )
                        {
                            try { conn.close(); }
                            catch ( Exception e ) {}
                        }
                    }
                }
            }
        }.start();
    }

    /**
     * used to compare thread name for refreshing schedules
     */
    private static final String THREAD_NAME = "TaskScheduler";
    private static final int IDLE_CHECKING = 3000;  // 3 seconds
    private static final int IDLE_MAXIMUM = 300000; //5 minutes


    private final HashMap<TimerTask, TaskScheduleJob> jobs = new HashMap<TimerTask, TaskScheduleJob>();

    private final Thread timerTaskProcessingThread;
    private final String title;
    private boolean cancelled = false;
    private boolean disableTrace = true;
    private long prevSysTime = System.currentTimeMillis();
    private String interruptMessage = "";

    public TaskScheduler ()
    {
        this( null, Thread.NORM_PRIORITY );
    }

    public TaskScheduler ( final String _title )
    {
        this( _title, Thread.NORM_PRIORITY );
    }

    public TaskScheduler ( final String _title, int priority )
    {
        if ( null == _title ) this.title = "";
        else this.title = _title;
        //verify priority
        if ( priority < Thread.MIN_PRIORITY ) priority = Thread.MIN_PRIORITY;
        else if ( priority > Thread.MAX_PRIORITY ) priority = Thread.MAX_PRIORITY;

        if ( "".equals( this.title ) ) timerTaskProcessingThread = new Thread( this, THREAD_NAME );
        else timerTaskProcessingThread = new Thread( this, THREAD_NAME + " : " + this.title );
        timerTaskProcessingThread.setPriority( priority );
        timerTaskProcessingThread.start();
    }

    private class TaskScheduleJob
    {
        final long delay;
        final long period;
        Thread thread;
        long nextExecutionTime;

        public TaskScheduleJob ( Thread thread, long nextExecutionTime, long delay, long period )
        {
            this.thread = thread;
            this.nextExecutionTime = nextExecutionTime;
            this.delay = delay;
            this.period = period;
        }

        public String toString ()
        {
            return "";
        }
    }

    /**
     * disable trace for the task scheduler , for fast executing task or stable developed task
     */
    public void disableTrace ()
    {
        disableTrace = true;
    }

    /**
     * enable trace for the task scheduler
     */
    public void enableTrace ()
    {
        disableTrace = false;
    }

    public void cancel ()
    {
        removeAllTask();
        this.cancelled = true;
        interruptMessage = "scheduler cancelled";
        timerTaskProcessingThread.interrupt();
    }

    public void removeTask ( TimerTask task )
    {
        if ( jobs.keySet().contains( task ) )
        {
            synchronized ( jobs )
            {
                jobs.remove( task );
            }
            task.cancel();
            this.interrupt();
        }
    }

    public void removeAllTask ()
    {
        Collection<TimerTask> tasks;
        synchronized ( jobs )
        {
            tasks = jobs.keySet();
            jobs.clear();
        }
        for ( TimerTask task : tasks ) { task.cancel(); }
        this.interrupt();
    }

    public int size ()
    {
        return jobs.size();
    }

    public boolean containsTask ( TimerTask task )
    {
        return jobs.keySet().contains( task );
    }

    /**
     * call this method after setting the system time , refresh the scheduled time for all tasks
     */
    public static void refreshAllSchedule ()
    {
        refreshAllSchedule( null );
    }

    public static void refreshAllSchedule ( PrintStream ps )
    {
        //refreshSchedule for all TaskScheduler
        ThreadGroup mainGroup = Thread.currentThread().getThreadGroup();
        ThreadGroup parent = mainGroup;
        while ( !"main".equals( parent.getName() ) ) { parent = parent.getParent(); }
        checkGroup( mainGroup, ps );
    }

    private static void checkGroup ( ThreadGroup group, PrintStream ps )
    {
        int nt = group.activeCount();
        Thread[] threads = new Thread[nt * 2 + 10]; //nt is not accurate
        nt = group.enumerate( threads, false );

        // List every thread in the group.
        String traceRecord = "";
        for ( int i = 0 ; i < nt ; i++ )
        {
            Thread t = threads[i];
            if ( t.getName().startsWith( THREAD_NAME ) )
            {
                t.interrupt();
                if ( null != ps )
                {
                    try
                    {
                        ps.println( " > " + t.getName() + " refreshed" );
                    }
                    catch ( Exception e ) {}
                }
            }
        }

        // Recursively list all subgroups
        int ng = group.activeGroupCount();
        ThreadGroup[] groups = new ThreadGroup[ng * 2 + 10];
        ng = group.enumerate( groups, false );
        for ( int i = 0 ; i < ng ; i++ ) { checkGroup( groups[i], ps ); }
    }

    public void refreshSchedule ()
    {
        synchronized ( jobs )
        {
            interruptMessage = "refreshing schedules";

            for ( TaskScheduleJob job : jobs.values() )
            {
                if ( -1 == job.period )
                {
                    if ( System.currentTimeMillis() < job.nextExecutionTime - job.delay )
                    {
                        job.nextExecutionTime = System.currentTimeMillis() + job.delay;
                    }
                }
                else
                {
                    if ( System.currentTimeMillis() < job.nextExecutionTime - job.period )
                    {
                        while ( System.currentTimeMillis() < job.nextExecutionTime - job.period )
                        {
                            job.nextExecutionTime = job.nextExecutionTime - job.period;
                            Thread.yield();
                        }
                    }
                    else
                    {
                        while ( System.currentTimeMillis() > job.nextExecutionTime )
                        {
                            job.nextExecutionTime = job.nextExecutionTime + job.period;
                            Thread.yield();
                        }
                    }
                }
            }
        }
        if ( !Thread.currentThread().equals( timerTaskProcessingThread ) ) timerTaskProcessingThread.interrupt();
        else
        {
            interruptMessage = "";
        }
    }

    private void sch ( TimerTask task, long delay, long period )
    {
        removeTask( task );
        TaskScheduleJob job = new TaskScheduleJob( null, System.currentTimeMillis() + delay, delay, period );
        synchronized ( jobs )
        {
            jobs.put( task, job );
        }
        interruptMessage = "add new task";
        timerTaskProcessingThread.interrupt();
    }

    public void schedule ( TimerTask task, Date time ) { this.sch( task, time.getTime() - System.currentTimeMillis(), -1 ); }

    public void schedule ( TimerTask task, Date firstTime, long period )
    {
        //adjust if first time already passed
        Calendar cal = Calendar.getInstance();
        cal.setTime( firstTime );
        cal.set( Calendar.MILLISECOND, 0 );
        while ( cal.getTime().getTime() <= System.currentTimeMillis() ) cal.add( Calendar.MILLISECOND, (int) period );
        firstTime = cal.getTime();

        this.sch( task, firstTime.getTime() - System.currentTimeMillis(), period );
    }

    public void schedule ( TimerTask task, long delay ) { this.sch( task, delay, -1 ); }

    public void schedule ( TimerTask task, long delay, long period ) { this.sch( task, delay, period ); }

    public void scheduleDayEnd ( TimerTask task )
    {
        Calendar cal = Calendar.getInstance();
        cal.set( Calendar.HOUR_OF_DAY, 0 );
        cal.set( Calendar.MINUTE, 0 );
        cal.set( Calendar.SECOND, 0 );
        cal.set( Calendar.MILLISECOND, 0 );
        while ( cal.getTime().getTime() <= System.currentTimeMillis() ) cal.add( Calendar.DAY_OF_MONTH, 1 );
        schedule( task, cal.getTime(), 86400000 );
    }

    public void scheduleDailyJob ( TimerTask task, int hour, int minute )
    {
        Calendar cal = Calendar.getInstance();
        cal.set( Calendar.HOUR_OF_DAY, hour );
        cal.set( Calendar.MINUTE, minute );
        cal.set( Calendar.SECOND, 0 );
        cal.set( Calendar.MILLISECOND, 0 );
        while ( cal.getTime().getTime() <= System.currentTimeMillis() ) cal.add( Calendar.DAY_OF_MONTH, 1 );
        schedule( task, cal.getTime(), 86400000 );
    }

    public void interrupt ()
    {
        timerTaskProcessingThread.interrupt();
    }

    public void run ()
    {
        long sleepTime;
        long maxSleepTime;

        loopCheck:
        while ( !cancelled )
        {
            if ( "".equals( this.title ) ) timerTaskProcessingThread.setName( THREAD_NAME + " - " + jobs.size() );
            else timerTaskProcessingThread.setName( THREAD_NAME + " : " + this.title + " - " + jobs.size() );
            try
            {
                if ( jobs.size() == 0 )
                {
                    maxSleepTime = IDLE_MAXIMUM;
                    System.gc();
                    //sleep until interrupt
                    Thread.sleep( maxSleepTime );
                }
                else
                {
                    maxSleepTime = IDLE_CHECKING;
                    synchronized ( jobs )
                    {
                        for ( TimerTask task : jobs.keySet() )
                        {
                            TaskScheduleJob job = jobs.get( task );
                            String taskName = "";

                            if ( !disableTrace )
                            {
                                if ( task instanceof TimerThread )
                                    taskName = ( (TimerThread) task ).getName();
                                else
                                    taskName = task.getClass().getSimpleName();
                            }
                            if ( "".equals( taskName ) ) taskName = "Task : " + task.toString();

                            if ( null != job.thread && Thread.State.TERMINATED.equals( job.thread.getState() ) )
                            {
                                //thread ended
                                job.thread.join();
                                job.thread = null;
                            }

                            //get system time
                            long sysTime = System.currentTimeMillis();
                            if ( prevSysTime > sysTime )
                            {
                                prevSysTime = sysTime;
                                refreshSchedule();
                                continue loopCheck;
                            }

                            if ( null == job.thread )
                            {
                                //no thread , check start required
                                if ( -1 == job.nextExecutionTime )
                                {
                                    //no need - non repeating task started
                                }
                                else
                                {
                                    //thread not started - check time reached
                                    if ( job.nextExecutionTime <= sysTime )
                                    {
                                        //time reached - start thread and update next execute time
                                        if ( task instanceof TimerThread )
                                            job.thread = new Thread( task, ( (TimerThread) task ).getName() );
                                        else
                                            job.thread = new Thread( task, "TimerTask" );
                                        job.thread.start();

                                        //update next execute time
                                        if ( job.period > 0 )
                                        {
                                            job.nextExecutionTime = job.nextExecutionTime + job.period;
                                            while ( job.nextExecutionTime < sysTime )
                                            { job.nextExecutionTime = job.nextExecutionTime + job.period; }

                                            //thread just started and execute time updated - calc sleep time
                                            sleepTime = job.nextExecutionTime - sysTime;

                                            if ( maxSleepTime > sleepTime ) maxSleepTime = sleepTime;
                                            //if ( maxSleepTime > IDLE_CHECKING ) maxSleepTime = IDLE_CHECKING;
                                        }
                                        else
                                        {
                                            //this task not a repeating task - set no next
                                            job.nextExecutionTime = -1L;
                                        }
                                    }
                                    else
                                    {
                                        //thread not started and execute time not reached - calc sleep time
                                        if ( job.nextExecutionTime != -1 )
                                        {
                                            sleepTime = job.nextExecutionTime - sysTime;
                                            if ( maxSleepTime > sleepTime ) maxSleepTime = sleepTime;
                                        }
                                    }
                                }
                            }
                            else // have thread and thread not ended
                            {

                                //thread started - check time reached for repeating task
                                if ( -1 != job.nextExecutionTime ) //&& job.nextExecutionTime <= sysTime )
                                {
                                    //update next execute time
                                    if ( -1 != job.period )
                                    {
                                        long shouldRunAt = job.nextExecutionTime;
                                        while ( shouldRunAt <= sysTime ) { shouldRunAt += job.period; }

                                        //thread just started and execute time updated - calc sleep time
                                        sleepTime = shouldRunAt - sysTime;

                                        if ( maxSleepTime > sleepTime ) maxSleepTime = sleepTime;
//                                        if ( maxSleepTime > IDLE_CHECKING ) maxSleepTime = IDLE_CHECKING;
                                    }
                                }
                            }
                            prevSysTime = sysTime;
                        }
                    }
                    //sleep until interrupt or next execute
                    if ( maxSleepTime >= 0 )
                    {
                        Thread.sleep( maxSleepTime );
                    }
                }
            }
            catch ( InterruptedException ie )
            {
                if ( !"".equals( interruptMessage ) )
                {
                    interruptMessage = "";
                }
                System.gc();
            }
            catch ( Exception e )
            {
                System.gc();
            }
        }
    }
}
