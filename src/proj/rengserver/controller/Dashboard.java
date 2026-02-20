package proj.rengserver.controller;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import proj.rengserver.background.PoolDBConnection;
import danny.dbconn.DBConnectionType;

import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * responding JSON dataset to charts queries
 *
 * @author Danny Wong
 */
@CrossOrigin ( origins = "*" )
@RestController
public class Dashboard
{
    static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
    static SimpleDateFormat fullDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    @RequestMapping ( value = "/dashboard/access", method = RequestMethod.GET )
    public String dashboardAccess ()  //return today dashboard access counts
    {
        String sql = "SELECT a.ip,a.number AS today,COALESCE(t.number,0) AS yest FROM accesslog a LEFT JOIN (SELECT l.ip,l.number FROM accesslog l WHERE DATE(l.date)=DATE_ADD(CURRENT_DATE,INTERVAL -1 DAY)) t ON a.ip=t.ip WHERE DATE(a.date)=CURRENT_DATE";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataNum = new JsonArray();
            JsonArray dataNum2 = new JsonArray();
            while ( rs.next() )
            {
                JsonArray label = new JsonArray();
                label.add(rs.getString("ip"));
                int number = rs.getInt("today");
                int yest = rs.getInt("yest");

                labelOrder.add(label);
                dataNum.add(number);
                dataNum2.add(yest);
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataNum);

            JsonObject passObj2 = new JsonObject();
            passObj2.add("data", dataNum2);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);
            dataArr.add(passObj2);

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

    @RequestMapping ( value = "/dashboard/message", method = RequestMethod.GET )
    public String dashboardMessage ()  //return today display message and count access
    {
        String sql = "SELECT m.*,MAX(l.date)AS lastaccess,MAX(l.date)>=m.date_issue AS showed FROM message m,accesslog l WHERE m.date_issue<=CURRENT_DATE AND l.ip='" + getIP() + "' GROUP BY m.id,l.ip ORDER BY showed,m.date_issue";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonObject result = new JsonObject();
            JsonArray messages = new JsonArray();
            while ( rs.next() )
            {
                if ( rs.getInt("showed") == 1 ) break;
                //required to show id, date_issue, message, lastaccess, showed
                JsonObject msg = new JsonObject();
                msg.addProperty("issue", rs.getString("date_issue"));
                msg.addProperty("message", rs.getString("message"));
                messages.add(msg);
            }

            result.add("message", messages);
            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
        //record access log here
        finally
        {
            try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") )
            {
                //log today access to database
                if ( pdb.getDb().updateSQL("UPDATE accesslog SET number=number+1 WHERE date='" + dateFormat.format(new Date()) + "' AND ip='" + getIP() + "' AND host='" + getHostname() + "'") == 0 )
                {
                    //insert
                    pdb.getDb().updateSQL("INSERT INTO accesslog (date,ip,host,first,number) VALUES ('" + dateFormat.format(new Date()) + "','" + getIP() + "','" + getHostname() + "','" + fullDateFormat.format(new Date()) + "',1)");
                }
            }
            catch ( Exception e )
            {
                e.printStackTrace();
            }
        }
    }

    @RequestMapping ( value = "/dashboard/data", method = RequestMethod.GET )
    public String dashboardData ()  //return data in text or number format
    {
        JsonObject dataResult = new JsonObject();
        String sql = "SELECT COUNT(*)AS completed FROM today_order WHERE quantity=pass";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("order", rs.getInt("completed"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sql = "SELECT COUNT(*)AS totalpass FROM today_test_result WHERE result='Pass'";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("unit", rs.getInt("totalpass"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sql = "SELECT COUNT(*)AS completed FROM testdata_thisweekcombineorder WHERE quantity<=pass";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("thisorder", rs.getInt("completed"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sql = "SELECT SUM(pass)AS totalpass FROM testdata_thisweekcombineorder";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("thisunit", rs.getInt("totalpass"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sql = "SELECT COUNT(*)AS completed FROM testdata_lastweekcombineorder WHERE quantity<=pass";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("lastorder", rs.getInt("completed"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }

        sql = "SELECT SUM(pass)AS totalpass FROM testdata_lastweekcombineorder";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            while ( rs.next() )
            {
                dataResult.addProperty("lastunit", rs.getInt("totalpass"));
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
        return dataResult.toString();
    }

    @RequestMapping ( value = "/dashboard/stationtestresult", method = RequestMethod.GET )
    public String stationTestResult ()  //return pass and fail count at today operating stations
    {
        //String sql = "SELECT t1.station,SUM(t1.pass)AS today_pass,SUM(t1.fail)AS today_fail,SUM(t3.pass)AS yest_pass,SUM(t3.fail)AS yest_fail,IF(((SUM(t1.pass)-COALESCE(t3.pass,0))<0),0,(SUM(t1.pass)-COALESCE(t3.pass,0)))AS pass,IF(((SUM(t1.fail)-COALESCE(t3.fail,0))<0),0,(SUM(t1.fail)-COALESCE(t3.fail,0)))AS fail FROM testdata_todayworkorder t1 LEFT JOIN testdata_yestworkorder t3 ON(t1.workorder=t3.workorder AND t1.quantity=t3.quantity AND t1.station=t3.station) GROUP BY t1.station";
        String sql = "SELECT r.station,COUNT(*)AS pass, COALESCE((SELECT COUNT(*) FROM today_test_result WHERE result='Fail' AND station=r.station GROUP BY station),0)AS fail FROM today_test_result r WHERE r.result='Pass' GROUP BY station";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelStation = new JsonArray();
            JsonArray dataPass = new JsonArray();
            JsonArray dataFail = new JsonArray();
            while ( rs.next() )
            {
                String station = rs.getString("station");
                int pass = rs.getInt("pass");
                int fail = rs.getInt("fail");
                labelStation.add(station);
                dataPass.add(pass);
                dataFail.add(fail);
            }
            JsonObject passObj = new JsonObject();
            passObj.addProperty("label", "Pass");
            passObj.add("data", dataPass);

            JsonObject failObj = new JsonObject();
            failObj.addProperty("label", "Fail");
            failObj.add("data", dataFail);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);
            dataArr.add(failObj);

            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelStation);

            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/dashboard/hourproductivity", method = RequestMethod.GET )
    public String hourProductivity ()  //return today pass count show by hours
    {
        //String sql = "SELECT test_hour AS hour,SUM(prod)AS pass FROM testdata_todayproductbyhour GROUP BY test_hour";
        String sql = "SELECT HOUR(datetime)AS hour,COUNT(*)AS prod FROM today_test_result WHERE result='Pass' GROUP BY hour";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelHour = new JsonArray();
            JsonArray dataPass = new JsonArray();
            int prevHour = -1;
            while ( rs.next() )
            {
                int hour = rs.getInt("hour");
                if ( prevHour == -1 ) prevHour = hour;
                else if ( hour != prevHour + 1 )
                {
                    //insert the missing hours
                    for ( int i = prevHour + 1 ; i < hour ; i++ )
                    {
                        labelHour.add(i + "");
                        dataPass.add(0);
                    }
                }
                prevHour = hour;
                int pass = rs.getInt("prod");
                labelHour.add(hour + "");
                if ( pass < 0 ) dataPass.add(0);
                else dataPass.add(pass);
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataPass);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);

            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelHour);

            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/dashboard/dayproductivity", method = RequestMethod.GET )
    public String dayProductivity ()  //return today pass count show by hours
    {
        //String sql = "SELECT test_hour AS hour,SUM(prod)AS pass FROM testdata_todayproductbyhour GROUP BY test_hour";
        String sql = "SELECT HOUR(datetime)AS hour,COUNT(*)AS prod FROM today_test_result WHERE result='Pass' GROUP BY hour";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelHour = new JsonArray();
            JsonArray dataPass = new JsonArray();
            int prevHour = -1;
            int pass = 0;
            while ( rs.next() )
            {
                int hour = rs.getInt("hour");
                if ( prevHour == -1 )
                {
                    labelHour.add(hour + "");
                    dataPass.add(0);
                    prevHour = hour;
                }
                hour++;
                if ( hour != prevHour + 1 )
                {
                    //insert the missing hours
                    for ( int i = prevHour + 1 ; i < hour ; i++ )
                    {
                        labelHour.add(i + "");
                        dataPass.add(0);
                    }
                }
                pass += rs.getInt("prod");
                labelHour.add(hour + "");
                if ( pass < 0 ) dataPass.add(0);
                else dataPass.add(pass);
                prevHour = hour;
            }
            JsonObject passObj = new JsonObject();
            passObj.add("data", dataPass);

            JsonArray dataArr = new JsonArray();
            dataArr.add(passObj);

            JsonObject result = new JsonObject();
            result.add("datasets", dataArr);
            result.add("labels", labelHour);

            return result.toString();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return "{}";
        }
    }

    @RequestMapping ( value = "/dashboard/dayorderprogress", method = RequestMethod.GET )
    public String dayOrderProgress ()  //return today order progress
    {
        String sql = "SELECT `order`,pass,quantity,IF(pass/quantity>1,1,COALESCE(CAST(pass/quantity AS DECIMAL(3,2)),0))*100 AS percentage FROM today_order_combine t ORDER BY percentage DESC,`order`";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            while ( rs.next() )
            {
                String order = rs.getString("order");
                order += " (" + rs.getInt("pass") + "/" + rs.getInt("quantity") + ")";
                int percentage = rs.getInt("percentage");
                labelOrder.add(order);
                dataPass.add(percentage);
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

    @RequestMapping ( value = "/dashboard/dayorderduration", method = RequestMethod.GET )
    public String dayOrderDuration ()
    {
        String sql = "SELECT `order`,station,MIN(datetime)AS start_time,MAX(datetime)AS end_time,TIMEDIFF(MAX(datetime),MIN(datetime))AS duration,TIME_TO_SEC(TIMEDIFF(MAX(datetime),MIN(datetime)))AS seconds FROM today_record GROUP BY `order`,station ORDER BY start_time,`order`";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            while ( rs.next() )
            {
                JsonArray label = new JsonArray();
                label.add(rs.getString("order") + " @ " + rs.getString("station"));
                int percentage = rs.getInt("seconds");

                labelOrder.add(label);
                dataPass.add(percentage);
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

    @RequestMapping ( value = "/dashboard/dayorderunitduration", method = RequestMethod.GET )
    public String dayOrderUnitDuration ()
    {
        String sql = "SELECT `order`,station,MIN(datetime)AS start_time,MAX(datetime)AS end_time,TIMEDIFF(MAX(datetime),MIN(datetime))AS duration,TIME_TO_SEC(TIMEDIFF(MAX(datetime),MIN(datetime)))/MAX(pass)AS avgseconds FROM today_record GROUP BY `order`,station ORDER BY start_time,`order`";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            while ( rs.next() )
            {
                JsonArray label = new JsonArray();
                label.add(rs.getString("order") + " @ " + rs.getString("station"));
                if ( null != rs.getString("avgseconds") )
                {
                    int avgseconds = rs.getInt("avgseconds");
                    if ( avgseconds > 0 )
                    {
                        labelOrder.add(label);
                        dataPass.add(avgseconds);
                    }
                }
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

    @RequestMapping ( value = "/dashboard/thisweekorderprogress", method = RequestMethod.GET )
    public String thisWeekOrderProgress ()  //return last week order progress
    {
        String sql = "SELECT test_order,pass,quantity,IF(pass/quantity>1,1,COALESCE(CAST(pass/quantity AS DECIMAL(3,2)),0))*100 AS percentage FROM testdata_thisweekcombineorder t ORDER BY percentage DESC,test_order";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            while ( rs.next() )
            {
                String order = rs.getString("test_order");
                order += " (" + rs.getInt("pass") + "/" + rs.getInt("quantity") + ")";
                int percentage = rs.getInt("percentage");
                labelOrder.add(order);
                dataPass.add(percentage);
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

    @RequestMapping ( value = "/dashboard/lastweekorderprogress", method = RequestMethod.GET )
    public String lastWeekOrderProgress ()  //return last week order progress
    {
        String sql = "SELECT test_order,pass,quantity,IF(pass/quantity>1,1,COALESCE(CAST(pass/quantity AS DECIMAL(3,2)),0))*100 AS percentage FROM testdata_lastweekcombineorder t ORDER BY percentage DESC,test_order";
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, getIP(), "Dashboard") ; ResultSet rs = pdb.getDb().querySQL(sql) )
        {
            JsonArray labelOrder = new JsonArray();
            JsonArray dataPass = new JsonArray();
            while ( rs.next() )
            {
                String order = rs.getString("test_order");
                order += " (" + rs.getInt("pass") + "/" + rs.getInt("quantity") + ")";
                int percentage = rs.getInt("percentage");
                labelOrder.add(order);
                dataPass.add(percentage);
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

    //query station

    //show total test , pass , fail
    //SELECT result,COUNT(result)AS num FROM today_test_result WHERE station='CFL1E' GROUP BY result

    //show pass and fail by hour
    //SELECT HOUR(datetime)AS hour,result,COUNT(result)AS num FROM today_test_result WHERE station='CFL1E' GROUP BY hour,result ORDER BY hour

    protected static String getIP ()
    {
        try
        {
            return ( (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes() ).getRequest().getRemoteAddr();
        }
        catch ( Exception e ) { return "Unknown"; }
    }

    protected static String getHostname ()
    {
        try
        {
            return ( (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes() ).getRequest().getRemoteHost();
        }
        catch ( Exception e ) { return "Unknown"; }
    }
}
