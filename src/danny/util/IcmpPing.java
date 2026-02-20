package danny.util;
/**
 * Created by Danny on 17/10/2016.
 */

import org.icmp4j.IcmpPingResponse;
import org.icmp4j.IcmpPingUtil;

public class IcmpPing
{
    public synchronized static boolean ping ( String ip, int timeout ) throws Exception
    {
        final IcmpPingResponse response = IcmpPingUtil.executePingRequest( ip, 1, timeout );
        return response.getSuccessFlag();
    }
}
