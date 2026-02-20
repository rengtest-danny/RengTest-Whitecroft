package danny.util;


/**
 * Created by Danny Wong on 21/8/2014
 */
public class VM
{
    private static final long INIT_CPU_TSC = System.nanoTime();

    static
    {
        //do nothing just init INIT_CPU_TSC value
        long second = INIT_CPU_TSC / 1000000000L;
        long minute = second / 60;
        second = second % 60;
        long hour = minute / 60;
        minute = minute % 60;
        long day = hour / 24;
        hour = hour % 24;
    }

    public static long getInitTimeStamp ()
    {
        return INIT_CPU_TSC;
    }

    public static long upMillis ()
    {
        return ( System.nanoTime() - INIT_CPU_TSC ) / 1000000;
    }

    public static String toString ( long upMillis )
    {
        long ms = upMillis % 1000;
        upMillis /= 1000;
        long s = upMillis % 60;
        upMillis /= 60;
        long m = upMillis % 60;
        upMillis /= 60;
        return "UpTime : " + upMillis + "h " + m + "m " + s + "s " + ms + "ms";
    }
}
