package proj.rengserver.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import danny.dbconn.DBConnectionType;
import danny.dbconn.DBResultSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * responding details to query for single work order
 *
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class DashboardOrderCheck
{
    SimpleDateFormat datetimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");


    @RequestMapping ( value = "/order/1", method = RequestMethod.GET )
    public String order1 ( @RequestParam ( required = true ) String order )
    {
        String sql = "SELECT DATE(test_datetime)AS tdate,SUM(t.test_pass-COALESCE((select d.test_pass from testdata d where t.test_station=d.test_station and t.test_order=d.test_order and t.test_quantity=d.test_quantity and ((t.test_todo+1=d.test_todo and t.test_pass=d.test_pass+1 and t.test_fail=d.test_fail) or (t.test_todo=d.test_todo and t.test_pass=d.test_pass and t.test_fail=d.test_fail+1))and (d.test_datetime>=t.test_datetime-interval 7 day) and d.test_datetime<t.test_datetime order by d.id desc limit 1),0))AS prod FROM testdata t WHERE t.test_order='" + order + "' GROUP BY DATE(test_datetime),test_quantity,test_station ORDER BY test_datetime";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            LinkedHashMap<String, Integer> prodCount = new LinkedHashMap<>();
            while ( rs.next() )
            {
                String day = rs.getString("tdate");
                int pass = rs.getInt("prod");
                if ( pass < 0 ) pass = 0;
                if ( !prodCount.containsKey(day) ) prodCount.put(day, pass);
                else prodCount.put(day, prodCount.get(day) + pass);
            }
            JsonArray labelDate = new JsonArray();
            JsonArray dataPass = new JsonArray();
            for ( Map.Entry<String, Integer> set : prodCount.entrySet() )
            {
                labelDate.add(set.getKey());
                dataPass.add(set.getValue());
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataPass);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);

            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelDate);

            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/order/2", method = RequestMethod.GET )
    public String order2 ( @RequestParam ( required = true ) String order, @RequestParam ( required = true ) String date )
    {
        String sql = "SELECT test_station,HOUR(test_datetime)AS hour,SUM(t.test_pass-COALESCE((select d.test_pass from testdata d where t.test_station=d.test_station and t.test_order=d.test_order and t.test_quantity=d.test_quantity and ((t.test_todo+1=d.test_todo and t.test_pass=d.test_pass+1 and t.test_fail=d.test_fail) or (t.test_todo=d.test_todo and t.test_pass=d.test_pass and t.test_fail=d.test_fail+1))and (d.test_datetime>=t.test_datetime-interval 7 day) and d.test_datetime<t.test_datetime order by d.id desc limit 1),0))AS prod FROM testdata t WHERE t.test_order='" + order + "' AND DATE(test_datetime)='" + date + "' GROUP BY test_quantity,test_station,hour ORDER BY hour";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            LinkedHashMap<Integer, LinkedHashMap<String, Double>> prodCount = new LinkedHashMap<>();
            LinkedHashMap<String, JsonArray> stationCount = new LinkedHashMap<>();
            int minHr = 23;
            int maxHr = 0;
            while ( rs.next() )
            {
                int hour = rs.getInt("hour");
                if ( minHr > hour ) minHr = hour;
                if ( maxHr < hour ) maxHr = hour;
                if ( !prodCount.containsKey(hour) ) prodCount.put(hour, new LinkedHashMap<>());

                String station = rs.getString("test_station");
                if ( !stationCount.containsKey(station) ) stationCount.put(station, new JsonArray());
                double pass = rs.getInt("prod");
                if ( pass == 0 ) pass = 0.1;
                else if ( pass < 0 ) pass = 0;

                if ( !prodCount.get(hour).containsKey(station) ) prodCount.get(hour).put(station, pass);
                else prodCount.get(hour).put(station, prodCount.get(hour).get(station) + pass);
            }
            JsonArray labelDate = new JsonArray();
            for ( int i = minHr ; i <= maxHr ; i++ )
            {
                labelDate.add(i);
                for ( String station : stationCount.keySet() )
                {
                    if ( prodCount.containsKey(i) )
                    {
                        LinkedHashMap<String, Double> set = prodCount.get(i);
                        stationCount.get(station).add(set.getOrDefault(station, 0D));
                    }
                    else stationCount.get(station).add(0D);
                }
            }
            JsonArray dataArr = new JsonArray();
            for ( Map.Entry<String, JsonArray> set : stationCount.entrySet() )
            {
                JsonObject passObj = new JsonObject();
                passObj.addProperty("label", set.getKey());
                passObj.add("data", set.getValue());
                dataArr.add(passObj);
            }
            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelDate);
            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/order/3", method = RequestMethod.GET )
    public String order3 ( @RequestParam ( required = true ) String order, @RequestParam ( required = true ) String date, @RequestParam ( required = true ) String hour, @RequestParam ( required = true ) String station )
    {
        String sql = "SELECT t.test_pass-COALESCE((select d.test_pass from testdata d where t.test_station=d.test_station and t.test_order=d.test_order and t.test_quantity=d.test_quantity and ((t.test_todo+1=d.test_todo and t.test_pass=d.test_pass+1 and t.test_fail=d.test_fail) or (t.test_todo=d.test_todo and t.test_pass=d.test_pass and t.test_fail=d.test_fail+1))and (d.test_datetime>=t.test_datetime-interval 7 day) and d.test_datetime<t.test_datetime order by d.id desc limit 1),0)AS prod,COUNT(*)AS num,COALESCE(test_fail_at,'Unknown')AS test_fail_at FROM testdata t WHERE t.test_order='" + order + "' AND DATE(test_datetime)='" + date + "' AND HOUR(test_datetime)=" + hour + " AND test_station='" + station + "' GROUP BY prod,test_fail_at ORDER BY prod DESC";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            boolean checkFirst = false;
            while ( rs.next() )
            {
                JsonArray label = new JsonArray();
                //prod, num, test_fail_at
                int prod = rs.getInt("prod");
                if ( !checkFirst && 0 == prod )
                {
                    //means no pass
                    label.add("Pass");
                    labelOrder.add(label);
                    dataPass.add(0);
                    label = new JsonArray();
                }
                checkFirst = true;
                if ( 1 == prod )
                    label.add("Pass");
                else
                    label.add("Fail : " + rs.getString("test_fail_at"));
                labelOrder.add(label);
                dataPass.add(rs.getInt("num"));
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataPass);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);

            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelOrder);

            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/order/data", method = RequestMethod.GET )
    public String orderData ( @RequestParam ( required = true ) String order )
    {
        JsonObject result = new JsonObject();

        String sql1 = "SELECT MIN(test_datetime)AS start,MAX(test_datetime)AS end,if(DATE(MAX(test_datetime))!=CURRENT_DATE OR(test_quantity=MAX(test_pass)OR MAX(test_pass)<=1),'-',DATE_ADD(MAX(test_datetime),INTERVAL (CEILING(TIME_TO_SEC(TIMEDIFF(MAX(test_datetime),MIN(test_datetime)))/(MAX(test_pass)-1)))*(test_quantity-MAX(test_pass)) SECOND))AS estend,test_station,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_type='Unknown',NULL,test_type))),'Unknown')AS testtype,test_quantity,MAX(test_pass)AS pass,MAX(test_fail)AS fail,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_fail_at='none',NULL,test_fail_at))),'None')AS failat FROM testdata WHERE test_order='" + order + "' GROUP BY test_quantity,test_station ORDER BY test_datetime";
        //String sql1 = "SELECT MIN(test_datetime)AS start,MAX(test_datetime)AS end,test_station,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_type='Unknown',NULL,test_type))),'Unknown')AS testtype,test_quantity,MAX(test_pass)AS pass,MAX(test_fail)AS fail,COALESCE(GROUP_CONCAT(DISTINCT(IF(test_fail_at='none',NULL,test_fail_at))),'None')AS failat FROM testdata WHERE test_order='" + order + "' GROUP BY test_quantity,test_station ORDER BY test_datetime";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql1) )
        {
            if ( rs instanceof DBResultSet )
            {
                JsonArray tests = ( (DBResultSet) rs ).getJson();

                for ( int i = 0 ; i < tests.size() ; i++ )
                {
                    JsonObject obj = tests.get(i).getAsJsonObject();
                    if ( obj.keySet().contains("start") && obj.get("start").getAsString().endsWith(".0") )
                    {
                        obj.addProperty("start", obj.get("start").getAsString().replace(".0", ""));
                    }
                    if ( obj.keySet().contains("end") && obj.get("end").getAsString().endsWith(".0") )
                    {
                        obj.addProperty("end", obj.get("end").getAsString().replace(".0", ""));
                    }
                    if ( obj.keySet().contains("estend") && !obj.get("end").getAsString().equals("-") )
                    {
                        try
                        {
                            //check if passed same day 16:00 then shows by hours
                            String todayOff = dateFormat.format(new Date()) + " 16:00:00";
                            Date todayOffTime = datetimeFormat.parse(todayOff);
                            Date estOrderEnd = datetimeFormat.parse(obj.get("estend").getAsString());
                            if ( estOrderEnd.getTime() > todayOffTime.getTime() )
                            {
                                //show hours
                                long diff = ( estOrderEnd.getTime() - new Date().getTime() ) / 1000;
                                long hr = diff / 3600;
                                diff -= hr * 3600;
                                long min = diff / 60;
                                diff -= min * 60;
                                if ( diff > 1 ) min++;
                                if ( min >= 60 )
                                {
                                    hr++;
                                    min -= 60;
                                }
                                obj.addProperty("estend", ( hr > 0 ? hr + " hr " : "" ) + min + " min");
                            }
                        }
                        catch ( Exception ignore ) {}
                    }
                }

                result.add("test", tests);
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }


        return result.toString();
    }


    protected static String getIP ()
    {
        try
        {
            return ( (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes() ).getRequest().getRemoteAddr();
        }
        catch ( Exception e ) { return "Unknown"; }
    }
}

