package proj.rengserver.background;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import danny.dbconn.DBConnectionType;
import proj.rengserver.controller.RecordReceiver;

import java.io.File;
import java.io.FileFilter;
import java.io.RandomAccessFile;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Danny Wong
 */
public class TestDataLogAnalyzer extends Thread
{
    private static final File PREV_TESTDATA_FOLDER = new File("\\\\gilliam\\OPERATIONS\\Production Engineering\\Projects\\2021 New test equipment\\4. Data");
    private static final File PREV_TESTDATA_LAST_CHECK = new File("db", "last_analyze.json");
    private static final File PREV_TESTDATA_LAST_CHECK_BACKUP = new File("db", "last_analyze.backup");

    private static final HashMap<String, Long> lastCheck = new HashMap<>();
    boolean updatePrinted = false;

    //retry check
    private static final HashMap<String, Integer> retryCheck = new HashMap<>();

    private static final SimpleDateFormat logDateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final SimpleDateFormat dbDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static final SimpleDateFormat dayCheckFormat = new SimpleDateFormat("yyyy-MM-dd");

    private void saveStatus () throws Exception
    {//save status
        JsonObject lastCheckJson = new JsonObject();
        for ( Map.Entry<String, Long> set : lastCheck.entrySet() )
        {
            lastCheckJson.addProperty(set.getKey(), set.getValue());
        }
        if ( PREV_TESTDATA_LAST_CHECK.exists() ) PREV_TESTDATA_LAST_CHECK.delete();
        if ( !PREV_TESTDATA_LAST_CHECK.exists() )
        {
            PREV_TESTDATA_LAST_CHECK.getParentFile().mkdirs();
            PREV_TESTDATA_LAST_CHECK.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK, "rw") )
        {
            check.writeBytes(lastCheckJson.toString());
        }
        if ( PREV_TESTDATA_LAST_CHECK_BACKUP.exists() ) PREV_TESTDATA_LAST_CHECK_BACKUP.delete();
        if ( !PREV_TESTDATA_LAST_CHECK_BACKUP.exists() )
        {
            PREV_TESTDATA_LAST_CHECK_BACKUP.getParentFile().mkdirs();
            PREV_TESTDATA_LAST_CHECK_BACKUP.createNewFile();
        }
        try ( RandomAccessFile check = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK_BACKUP, "rw") )
        {
            check.writeBytes(lastCheckJson.toString());
        }
    }

    public void analyzeLog ()
    {
        //if
        if ( null == RecordReceiver.getInstance() ) return;
        try
        {
            //load status - file end of previous checking
            if ( PREV_TESTDATA_LAST_CHECK.exists() && PREV_TESTDATA_LAST_CHECK.isFile() )
            {
                try ( RandomAccessFile raf = new RandomAccessFile(PREV_TESTDATA_LAST_CHECK, "r") )
                {
                    JsonObject json = new JsonParser().parse(raf.readLine()).getAsJsonObject();
                    for ( String key : json.keySet() ) lastCheck.put(key, json.get(key).getAsLong());
                }
                catch ( Exception e )
                {
                    e.printStackTrace();
                }
            }

            //filter out test data folders
            File[] stationFolders = PREV_TESTDATA_FOLDER.listFiles(new FileFilter()
            {
                public boolean accept ( File pathname ) { return pathname.isDirectory() && pathname.getName().startsWith("CFL") && pathname.getName().endsWith(" Test Data") && pathname.getName().length() == 15; }
            });

            if ( null != stationFolders )   //maybe sometime cannot reach shared folder
            {
                boolean anyFileProcessed = false;
                nextFile:
                for ( File station : stationFolders )
                {
                    File testData = new File(station, "TestData.txt");
                    if ( !testData.exists() || !testData.isFile() )
                    {
                        if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                        {
                            lastCheck.remove(testData.getParentFile().getName());
                            saveStatus();
                            //alert
                            System.out.println("Log pointer reset for " + testData.getParentFile().getName());
                        }
                        continue;
                    }

//                    if ( !testData.getParentFile().getName().contains( "CFL1A" ) ) continue;

                    //check
                    if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                    {
                        if ( lastCheck.get(testData.getParentFile().getName()) < 0 )
                        {
                            lastCheck.remove(testData.getParentFile().getName());
                            saveStatus();
                        }
                        //skip this file if not updated
                        else if ( testData.length() <= lastCheck.get(testData.getParentFile().getName()) )
                            continue;
                    }

                    //read
                    try ( RandomAccessFile raf = new RandomAccessFile(testData, "r") )
                    {
                        //goto last processed
                        if ( lastCheck.containsKey(testData.getParentFile().getName()) )
                            raf.seek(lastCheck.get(testData.getParentFile().getName()));
                        //analyze test file here
                        ArrayList<String> testDetails = new ArrayList<>();
                        String data = "";
                        long beforeThisRecord = -1;
                        int blank = 0;
                        nextRow:
                        while ( null != data )
                        {
                            long beforeThisLine = raf.getFilePointer();
//                            System.out.println( "Before this line : " + beforeThisLine );
                            data = raf.readLine();
                            if ( null == data ) continue;
                            if ( "".equals(data) )
                            {
                                blank++;
                                continue;
                            }
                            blank = 0;
                            if ( testDetails.isEmpty() && data.startsWith("Date & Time : ") )
                            {
                                //start = raf.getFilePointer();
                                beforeThisRecord = beforeThisLine;
//                                System.out.println( "Before this record : " + beforeThisRecord );
                                testDetails.add("Station=" + testData.getParentFile().getName().replace(" Test Data", ""));
                                testDetails.add(data);
                            }
                            else if ( !testDetails.isEmpty() && data.startsWith("Date & Time : ") )
                            {
                                //next record start from this line (Date & Time)
                                raf.seek(beforeThisLine);
//                                System.out.println( "set pointer to line : " + beforeThisLine );
                                //end : pointing back to start of line
                                int anaResult = analyze(testDetails);
//                                System.out.println("Analyze Result " + anaResult);
                                if ( anaResult <= 0 && anaResult != -2 )
                                {
                                    //return false means not processing at this moment, maybe the power bi data not inserted yet, therefore will retry next time
                                    if ( -1 != beforeThisRecord )
                                    {
                                        raf.seek(beforeThisRecord);
//                                        System.out.println( "set pointer to record : " + beforeThisRecord );
                                    }
                                    break nextRow;
                                }
                                testDetails.clear();
                                testDetails.trimToSize();
                            }
                            else if ( !testDetails.isEmpty() )
                            {
                                testDetails.add(data);
                            }
                        }
                        //all data read - complete last record
                        if ( testDetails.size() > 1 && blank > 0 )
                        {
                            int anaResult = analyze(testDetails);
                            System.out.println(testData.getParentFile().getName() + " Analyze Result " + anaResult);
                            if ( anaResult <= 0 && anaResult != -2 )
                            {
                                //return false means not processing at this moment, maybe the power bi data not inserted yet, therefore will retry next time
                                if ( -1 != beforeThisRecord )
                                {
                                    raf.seek(beforeThisRecord);
//                                        System.out.println( "set pointer to record : " + beforeThisRecord );
                                }
                            }
                            else
                            {
                                anyFileProcessed = true;
                                updatePrinted = false;
                            }
                            testDetails.clear();
                            testDetails.trimToSize();
                        }
                        else
                        {
                            if ( -1 != beforeThisRecord ) raf.seek(beforeThisRecord);
                        }

                        if ( -1 != beforeThisRecord )
                        {
                            //remember current row, next time check from this row
                            lastCheck.put(testData.getParentFile().getName(), raf.getFilePointer());
                            saveStatus();
                        }
                    }
                    catch ( Exception e )
                    {
                        e.printStackTrace();
                    }
                }
                if ( !anyFileProcessed && !updatePrinted )
                {
                    System.out.println("All Log imported at " + dbDateFormat.format(new Date()));
                    updatePrinted = true;
                }
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
        }
    }

    //3  = update completed
    //2  = update check completed
    //1  = nothing to update, skip this one
    //0  = power bi record not found
    //-1 = unknown text found
    //-2 = insufficient data for specifying related record
    //-4 = exception occurs
    private int analyze ( ArrayList<String> testdata )
    {
        boolean processUpdate = true;
        try
        {
            String station = "Unknown";
            Date date = null;
            String order = null;
            String operator = null;
            String quantity = null;
            String todo = null;
            String pass = null;
            String fail = null;
            String type = "Unknown";
            String failAt = null;

            for ( String data : testdata )
            {
                if ( data.startsWith("Station=") )
                    station = data.replace("Station=", "");
                else if ( data.startsWith("Date & Time : ") )
                    date = logDateFormat.parse(data.substring(data.indexOf("Date & Time : ") + "Date & Time : ".length()));
                else if ( data.startsWith("W/O No : ") )
                    order = data.substring(data.indexOf("W/O No : ") + "W/O No : ".length());
                else if ( data.startsWith("Operator ID : ") )
                    operator = data.substring(data.indexOf("Operator ID : ") + "Operator ID : ".length());
                else if ( data.startsWith("Quantity : ") )
                    quantity = data.substring(data.indexOf("Quantity : ") + "Quantity : ".length());
                else if ( data.startsWith("Test to do : ") )
                    todo = data.substring(data.indexOf("Test to do : ") + "Test to do : ".length());
                else if ( data.startsWith("Test PASS : ") )
                    pass = data.substring(data.indexOf("Test PASS : ") + "Test PASS : ".length());
                else if ( data.startsWith("Test FAIL : ") )
                    fail = data.substring(data.indexOf("Test FAIL : ") + "Test FAIL : ".length());

                    //get type
                else if ( data.startsWith("-") && data.endsWith("--") && data.contains(". ") && !data.contains("Step") && !data.contains("step") )
                {
                    if ( "Unknown".equals(type) )
                        type = data.substring(data.indexOf(". ") + 2).replace("-", "");
                }
                else if ( data.endsWith("--") && data.contains(". ") && !data.contains("Step") && !data.contains("step") )
                {
                    if ( "Unknown".equals(type) )
                        type = data.substring(data.indexOf(". ") + 2).replace("-", "");
                }
                else if ( "Unknown".equals(type) && data.contains("Basic Test HF--") )
                    type = "Basic Test HF";
                else if ( "Unknown".equals(type) && data.contains("DALI DIM HFY--") )
                    type = "DALI DIM HFY";
                else if ( "Unknown".equals(type) && data.startsWith("Command code received :") )    //get command as type
                    type = "Profile : " + data.substring(data.indexOf("Command code received :") + "Command code received :".length()).replace(" ", "");
                else if ( !"Unknown".equals(type) && data.startsWith("Command code received :") ) {}
                else if ( !"Unknown".equals(type) && data.contains(type) ) {}
                //get fail at
                else if ( data.contains("--Test PASS--") )
                    failAt = "none";
                else if ( data.contains("DALI test Fail") )
                    failAt = "DALI test";
                else if ( data.contains("Obstruction detected") )
                    failAt = "Obstruction detected";
                else if ( data.contains("--Insulation test FAIL--") )
                    failAt = "Insulation test";
                else if ( data.contains("--Initial earth continuity test FAIL--") )
                    failAt = "Initial Earth continuity test";
                else if ( data.contains("Test Fail : Switch Live -Permenant Live reverse") )
                    failAt = "S.Live-P.Live reverse";
                else if ( data.contains("Test Fail : Profile not matched") )
                    failAt = "Profile not matched";
                else if ( data.contains("Test Fail : Profile not matc") )
                    failAt = "Profile not matched";
                else if ( data.contains("Test Fail : Lux Profile not matched ") )
                    failAt = "Lux Profile not matched";
                else if ( data.contains("EM Pro Test Fail") )
                    failAt = "EM Pro test";
                else if ( data.contains("Emergency Lighting Test Fail") )
                    failAt = "Emergency lighting test";
                else if ( data.contains("Test Fail : Organic response not working") )
                    failAt = "Organic response not working";
                else if ( data.contains("Earth  Point---->Fail") )
                    failAt = data.replace("-", "").replace(">", "").replace("Fail", "");
                else if ( data.contains("--Earth continuity test FAIL--") )
                    failAt = "Earth continuity test";
                else if ( data.contains("Stand-alone Continuity Test FAIL") )
                    failAt = "Stand-alone continuity test";
                else if ( data.contains("Test not completed") )
                    failAt = "Test not completed";
                else if ( data.contains("Regulating DIM UP Test Fail") )
                    failAt = "Regulating DIM UP";

                else if ( data.contains("Test Fail :") )
                    failAt = data.replace("Test Fail : ", "");
                else if ( ( data.equals("Fail") || data.contains("Test Fail") ) )
                { if ( null == failAt ) failAt = "unknown"; }


                //get values
                else if ( data.contains("profile in ON state") ) {}
                else if ( data.contains("profile in OFF state") ) {}
                else if ( data.contains("--Voltage") && data.contains("--Current") && data.contains("--Power") && data.contains("--Lux") ) {}
                else if ( data.startsWith("Luminaries Lux:") ) {}
                else if ( data.startsWith("--Voltage:") ) {}
                else if ( data.startsWith("--Frequency:") ) {}
                else if ( data.startsWith("--Effective current") ) {}
                else if ( data.startsWith("--Power:") ) {}
                else if ( data.startsWith("Lux PASS condition") ) {}
                else if ( data.contains("PF:") ) {}
                else if ( data.startsWith("--Voltage not available") ) {}
                else if ( data.startsWith("--Frequency not available") ) {}
                else if ( data.startsWith("--Power not available") ) {}
                else if ( data.startsWith("Analog value  :") ) {}
                else if ( data.contains("Analog Value from A0 = ") ) {}
                else if ( data.contains("Analog Value from A7 = ") ) {}
                else if ( data.startsWith("Value of LuxEM") ) {}
                else if ( data.startsWith("Condition 1 : ") ) {}
                else if ( data.startsWith("Condition 2 : ") ) {}
                else if ( data.startsWith("DALI current") ) {}
                else if ( data.startsWith("Switch Dim Off Power") ) {}
                else if ( data.startsWith("Switch Dim On Power") ) {}
                else if ( data.startsWith("P.Live Off Power") ) {}
                else if ( data.startsWith("Sample Value") ) {}
                else if ( data.contains("Calibrated Minimum Earth Value") ) {}
                else if ( data.startsWith("Fail if ") ) {}
                else if ( data.contains("Luminaries Lux: ") ) {}
                else if ( data.contains("DALL 230 V AC at =") ) {}
                else if ( data.startsWith("Pin D8 Status : ") ) {}
                else if ( data.startsWith("Measuring luxCOMET") ) {}

                //tests
                else if ( data.startsWith("L-N short circuit") ) {}
                else if ( data.startsWith("Insulation test between") ) {}
                else if ( data.contains("Insulation test PASS") ) {}
                else if ( data.startsWith("Live-Earth Connection Check") ) {}
                else if ( data.startsWith("Basic test completed") ) {}
                else if ( data.contains("Step 1.") ) {}
                else if ( data.contains("Step 2.") ) {}
                else if ( data.contains("Step 3.") ) {}
                else if ( data.contains("Step 4.") ) {}
                else if ( data.contains("Step 5.") ) {}
                else if ( data.startsWith("--Earth Continuity Omitted--") ) {}
                else if ( data.startsWith("Lux ON test PASS") ) {}
                else if ( data.startsWith("DALI Test") ) {}
                else if ( data.startsWith("--DALI Test--") ) {}
                else if ( data.startsWith("DALI Test (RESET,ON,OFF,ON)") ) {}
                else if ( data.contains("DALL Current Sample Value") ) {}
                else if ( data.startsWith("--DALI RAMP UP--") ) {}
                else if ( data.startsWith("--DALI RAMP DOWN--") ) {}
                else if ( data.contains("DALL Current") ) {}
                else if ( data.startsWith("--DALI LIGHT FULL ON--") ) {}
                else if ( data.startsWith("--DALI Sending commands stop--") ) {}
                else if ( data.startsWith("--Measuring Current Power") ) {}
                else if ( data.startsWith("-Measuring Current Power") ) {}
                else if ( data.startsWith("--Initial Earth continuity test satisfactory--") )
                    failAt = "Initial Earth continuity test";
                else if ( data.startsWith("-Manual Earth Continutiy Test") ) {}
                else if ( data.startsWith("Stand-alone Continuity Test PASS") ) {}
                else if ( data.startsWith("EMPRO Test Active") ) {}
                else if ( data.startsWith("Enable Device Type 1") ) {}
                else if ( data.startsWith("Start Function Test") ) {}
                else if ( data.startsWith("Stop Function Test") ) {}
                else if ( data.contains("OK!") ) {}
                else if ( data.startsWith("LUxEM1 < LuxEM2") ) {}
                else if ( data.startsWith("LUxEM1 > LuxEM2") ) {}
                else if ( data.startsWith("--Em Pro Test PASS--") ) {}
                else if ( data.startsWith("Charging Battery") ) {}
                else if ( data.startsWith("Emergency Lighting Test PASS") ) {}
                else if ( data.startsWith("D8 Status") ) {}
                else if ( data.startsWith("1st Earth Point") ) {}
                else if ( data.startsWith("1st  Earth  Point") ) {}
                else if ( data.startsWith("2nd Earth Point") ) {}
                else if ( data.startsWith("2nd  Earth  Point") ) {}
                else if ( data.startsWith("3rd Earth Point") ) {}
                else if ( data.startsWith("3rd  Earth  Point") ) {}
                else if ( data.startsWith("PASS : Test Completed") ) {}
                else if ( data.startsWith("--Sending high voltage in the cable--") ) {}
                else if ( data.startsWith("Testing organic response sensor") ) {}
                else if ( data.startsWith("Health Check of a circuit detecting Live in Earth cable") ) {}
                else if ( data.startsWith("Probe 1 Pass!") )
                    failAt = "Earth continuity test - Probe 2";
                else if ( data.startsWith("Probe 2 Pass!") )
                    failAt = "Earth continuity test - Probe 3";
                else if ( data.startsWith("Probe 3 Pass!") ) {}
                else if ( data.contains("* SWITCH DIM OFF *") ) {}
                else if ( data.contains("* SWITCH DIM ON *") ) {}
                else if ( data.contains("* SWITCH DIM TEST RESULT *") ) {}
                else if ( data.contains("*DIM DOWN*") ) {}
                else if ( data.contains("*DIM UP*") ) {}
                else if ( data.contains("Switch Dim test PASS") ) {}
                else if ( data.contains("Switching Dim test PASS") ) {}
                else if ( data.startsWith("P.Live off test PASS") ) {}
                else if ( data.startsWith("Organic Sensor PASS as") ) {}
                else if ( data.startsWith("--Earth Continuity Test--") ) {}
                else if ( data.startsWith("Emergency Test Pass") ) {}
                else if ( data.startsWith("Voltage detection") ) {}
                else if ( data.startsWith("--EM Pro test--") ) {}
                else if ( data.startsWith("--Regulating DIM UP--") ) {}
                else if ( data.startsWith("--E2 Earth Continuity Test--") ) {}
                else if ( data.endsWith("Insulation test between E - Combine (N-L-PL) ") ) {}
                else if ( data.startsWith("COMET Test") ) {}
                else if ( data.contains("Emergency light") && data.contains("seconds settle down period : ") ) {}
                else if ( data.startsWith("COMET Test Pass") ) {}
                else if ( data.startsWith("file in OFF state") ) {}
                else if ( data.startsWith("End DALI test") ) {}
                else if ( data.startsWith("--Luminaries Lux test--") ) {}

                //ignore
                else if ( data.startsWith("Wire ID : ") ) {}
                else if ( data.startsWith("Time : ") ) {}
                else if ( data.startsWith("Test Results  :") ) {}
                else if ( data.contains("Entering in setup mode") ) {}
                else if ( data.startsWith("Enter") ) {}
                else if ( data.startsWith("System ready for testing now") ) {}
                else if ( data.startsWith("System is ready for testing") ) {}
                else if ( data.startsWith("System is ready") ) {}
                else if ( data.startsWith("System status : RESET") ) {}
                else if ( data.startsWith("Voltage detection  status") ) {}
                else if ( data.contains("Lux meter trigger port") ) {}
                else if ( data.contains("resistance trigger port") ) {}
                else if ( data.startsWith("--Light sensor detected--") ) {}
                else if ( data.contains("leave the test zone now") ) {}
                else if ( data.startsWith("Selected Safety System:") ) {}
                else if ( data.startsWith("Saftey box calibration required") ) {}
                else if ( data.startsWith("Make sure the LED lit up for successful calibration") ) {}
                else if ( data.startsWith("--Light sensor not detected, please consult an engineer--") )
                {
                    failAt = "Light sensor not detected";
                }
                else if ( data.startsWith("Seconds before test starts:") ) {}
                else if ( data.contains("Waiting for test zone to be clear") ) {}
                else if ( data.startsWith("Leave test zone for ") ) {}
                else if ( data.contains("Remove Battery Connection") ) {}
                else if ( data.startsWith("--Ready") ) {}
                else if ( data.startsWith("Wait!") ) {}
                else if ( data.startsWith("Now carry on...") ) {}
                else if ( data.contains("received : ") ) {}
                //incomplete = trash, just added under development, will be ignored in normal operation
                else if ( data.equals("rt found ") ) {}
                else if ( data.replace(".", "").isEmpty() ) {}
                else if ( data.replace(".", "").replace("-", "").isEmpty() ) {}
                else if ( data.replace("1", "").isEmpty() ) {}
                else if ( data.replace("0", "").isEmpty() ) {}
                else if ( data.replace(" ", "").isEmpty() ) {}
                else if ( data.startsWith("easuring") ) {}
                else if ( data.endsWith("Emergency--") ) {}
                else if ( data.startsWith(", VDC ON") ) {}
                else if ( data.startsWith(" E") ) {}
                else if ( data.startsWith("ng") ) {}
                else if ( data.startsWith("or testing") ) {}
                else if ( data.startsWith("ALI DIM HFY--") ) {}
                else if ( data.startsWith("LiveKQX.") ) {}
                else if ( data.endsWith("Check") ) {}
                else if ( data.endsWith("zone now") ) {}
                else if ( data.endsWith("now--") ) {}
                else if ( data.endsWith(" found ") ) {}
                else if ( data.endsWith("HFY--") ) {}
                else if ( data.endsWith("HFEM--") ) {}
                else if ( data.endsWith("HFYEM--") ) {}
                else if ( data.endsWith("ct + OR3--") ) {}
                else if ( data.endsWith("detected--") ) {}
                else if ( data.endsWith("Current Power--") ) {}
                else if ( data.contains("mode ...") ) {}
                else if ( data.contains("--Lux") ) {}
                else if ( data.contains("PRO --") ) {}
                else if ( data.endsWith("testing") ) {}
                else if ( data.endsWith("HFEM--") ) {}
                else if ( data.startsWith("Sys") ) {}
                else if ( data.contains("TTWC") ) {}
                else if ( data.contains("ived : ") ) {}
                else if ( data.endsWith("OFF state...--") ) {}
                else if ( data.endsWith("ate...--") ) {}

                //not implements
                else
                {
//                    System.out.println( station + ":" + data );
//                    processUpdate = false;
                }
            }

            //check record exists
//            System.out.println("Log Data : " + testdata.toString());
            if ( !processUpdate ) return -1;
            if ( null != todo && todo.contains("-") ) return 1;
            if ( null == type && null == failAt ) return 1; //row not presence or nothing to update
//            System.out.println("type : " + type);
//            System.out.println("fail : " + failAt);
            if ( null != date && null != order && null != operator && null != quantity && null != todo && null != pass && null != fail && ( null != type || null != failAt ) &&
                    !order.isEmpty() && !operator.isEmpty() && !quantity.isEmpty() && !todo.isEmpty() && !pass.isEmpty() && !fail.isEmpty() )
            {
                //id, test_datetime, test_station, test_operator, test_wire_1, test_wire_2, test_order, test_type, test_class, test_quantity, test_todo, test_pass, test_fail, test_fail_at, profile_lux_on, profile_voltage_on, profile_current_on, profile_power_on, profile_lux_off, profile_voltage_off, profile_current_off, profile_power_off, value_insulation, value_earth_1, value_earth_2, value_earth_3, value_lux_on, value_lux_off, value_voltage_on, value_current_on, value_power_on, value_power_factor_on, value_frequency_on, value_voltage_off, value_current_off, value_power_off, value_power_factor_off, value_frequency_off, value_dali_current_1, value_dali_current_2
                String sql = "SELECT id FROM testdata WHERE test_station='" + station + "' AND test_datetime>='" + dbDateFormat.format(date) + "' AND test_order='" + order + "' AND test_quantity=" + quantity + " AND test_pass=" + pass + " AND test_fail=" + fail + " AND test_todo=" + todo + " AND test_operator='" + operator + "' ORDER BY test_datetime LIMIT 1";
                //System.out.println(sql);
                int id = 0;
                try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, "Analyze", "Import") ; ResultSet rs = pdb.getDb().querySQL(sql) )
                {
                    if ( rs.next() ) id = rs.getInt("id");
                }
                if ( id == 0 )
                {
                    //System.out.println("cannot match powerbi record: " + testdata.toString());
                    if ( dayCheckFormat.format(date).equals(dayCheckFormat.format(new Date())) )
                    {
                        if ( !retryCheck.containsKey(testdata.toString()) )
                        {
                            retryCheck.put(testdata.toString(), 0);
                            return 0;
                        }
                        else
                        {
                            retryCheck.put(testdata.toString(), retryCheck.get(testdata.toString()) + 1);
                            if ( retryCheck.get(testdata.toString()) > 30 )
                            {
                                //give up
                                //log to file
                                retryCheck.remove(testdata.toString());
                                return 1;
                            }
                            else return 0;
                        }
                    }
                    return 1;
                }

                //update
                sql = "";
                if ( null != type ) sql += "test_type='" + type + "',";
                if ( null != failAt ) sql += "test_fail_at='" + failAt + "',";
                if ( sql.length() > 1 ) sql = sql.substring(0, sql.length() - 1);
                sql = "UPDATE testdata SET " + sql + " WHERE id=" + id;
                try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, "Analyze", "Import") )
                {
                    //System.out.println( sql );
                    if ( pdb.getDb().updateSQL(sql) > 0 )
                    {
                        System.out.println("import " + station + " Log Data : " + ( null != type ? "type=" + type : "" ) + ( null != failAt ? " failAt=" + failAt : "" ) + " to record id " + id + " > OK ");
                        return 3;
                    }
                }
                return 2;
            }
            else
            {
                System.out.println("insufficient data for specifying related record: " + testdata.toString());
                return -2; //not continued
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            return -4;   //not processed
        }
    }

    public void run ()
    {
        try
        {
            setPriority(MIN_PRIORITY);
            sleep(2500);
        }
        catch ( Throwable t )
        {
            t.printStackTrace();
        }

        while ( true )
        {
            try
            {
                sleep(5000); //waiting interval between each call
                analyzeLog();
            }
            catch ( Throwable t )
            {
                t.printStackTrace();
            }
        }
    }
}
