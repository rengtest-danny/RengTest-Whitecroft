package proj.rengserver.background;

import danny.dbconn.DBConnectionType;

import java.sql.*;
import java.util.*;

/**
 * check database when program starts
 * create required database tables
 *
 * @author Danny Wong
 */
public class RengDBAutoGen
{
    static void checkDB () throws Exception
    {
        try ( PoolDBConnection pdb = new PoolDBConnection(DBConnectionType.MYSQL, "Server", "AutoCheckDB") )
        {
            final String engineAndLang = "ENGINE=MyISAM DEFAULT CHARSET=utf8";
            ArrayList<String> column = new ArrayList<String>();

            //check if cdr table exists
            if ( !tableFound(pdb, "testdata") )
            {
                column.clear();
                column.add("`id` int(10) unsigned NOT NULL auto_increment");
                column.add("`test_datetime` datetime NOT NULL default CURRENT_TIMESTAMP");
                column.add("`test_station` varchar(20) default NULL");
                column.add("`test_operator` varchar(10) default NULL");
                column.add("`test_wire_1` varchar(10) default NULL");
                column.add("`test_wire_2` varchar(10) default NULL");

                column.add("`test_order` varchar(20) default NULL");
                column.add("`test_type` varchar(30) default NULL");
                column.add("`test_class` varchar(5) default NULL");
                column.add("`test_quantity` int(10) unsigned NOT NULL default '0'");
                column.add("`test_todo` int(10) unsigned NOT NULL default '0'");
                column.add("`test_pass` int(10) unsigned NOT NULL default '0'");
                column.add("`test_fail` int(10) unsigned NOT NULL default '0'");
                column.add("`test_fail_at` varchar(50) default NULL");

                column.add("`profile_lux_on` int(10) signed NOT NULL default '-1'");
                column.add("`profile_voltage_on` double signed NOT NULL default '-1'");
                column.add("`profile_current_on` double signed NOT NULL default '-1'");
                column.add("`profile_power_on` double signed NOT NULL default '-1'");
                column.add("`profile_lux_off` int(10) signed NOT NULL default '0'");
                column.add("`profile_voltage_off` double signed NOT NULL default '-1'");
                column.add("`profile_current_off` double signed NOT NULL default '-1'");
                column.add("`profile_power_off` double signed NOT NULL default '-1'");

                column.add("`value_insulation` double signed NOT NULL default '-1'");
                column.add("`value_earth_1` double signed NOT NULL default '-1'");
                column.add("`value_earth_2` double signed NOT NULL default '-1'");
                column.add("`value_earth_3` double signed NOT NULL default '-1'");
                column.add("`value_lux_on` int(10) signed NOT NULL default '0'");
                column.add("`value_lux_off` int(10) signed NOT NULL default '0'");
                column.add("`value_voltage_on` double signed NOT NULL default '-1'");
                column.add("`value_current_on` double signed NOT NULL default '-1'");
                column.add("`value_power_on` double signed NOT NULL default '-1'");
                column.add("`value_power_factor_on` double signed NOT NULL default '-1'");
                column.add("`value_frequency_on` double signed NOT NULL default '-1'");
                column.add("`value_voltage_off` double signed NOT NULL default '-1'");
                column.add("`value_current_off` double signed NOT NULL default '-1'");
                column.add("`value_power_off` double signed NOT NULL default '-1'");
                column.add("`value_power_factor_off` double signed NOT NULL default '-1'");
                column.add("`value_frequency_off` double signed NOT NULL default '-1'");
                column.add("`value_dali_current_1` double signed NOT NULL default '-1'");
                column.add("`value_dali_current_2` double signed NOT NULL default '-1'");

                column.add("PRIMARY KEY  (`id`)");
                column.add("KEY `Index_time`(`test_datetime`,`test_order`)");
                column.add("KEY `Index_result`(`test_station`,`test_order`,`test_quantity`,`test_todo`,`test_pass`,`test_fail`,`test_datetime`)");
                pdb.getDb().createTable("testdata", column, engineAndLang);
            }

            if ( !tableFound(pdb, "accesslog") )
            {
                column.clear();
                column.add("`id` int(10) unsigned NOT NULL auto_increment");
                column.add("`date` datetime NOT NULL default CURRENT_TIMESTAMP");
                column.add("`ip` varchar(15) NOT NULL DEFAULT ''");
                column.add("`host` varchar(45) NOT NULL DEFAULT ''");
                column.add("`first` datetime NOT NULL default CURRENT_TIMESTAMP");
                column.add("`number` int(10) unsigned NOT NULL default '1'");
                column.add("PRIMARY KEY  (`id`)");
                column.add("KEY `Index_date`(`date`,`ip`)");
                pdb.getDb().createTable("accesslog", column, engineAndLang);
            }

            if ( !tableFound(pdb, "message") )
            {
                column.clear();
                column.add("`id` int(10) unsigned NOT NULL auto_increment");
                column.add("`date_issue` date NOT NULL default '0000-00-00'");
                column.add("`message` varchar(200) NOT NULL DEFAULT ''");
                column.add("PRIMARY KEY  (`id`)");
                pdb.getDb().createTable("message", column, engineAndLang);
            }

            //issue report
            if ( !tableFound(pdb, "issuedata") )
            {
                column.clear();
                column.add("`id` int(10) unsigned NOT NULL auto_increment");
                column.add("`report_datetime` datetime NOT NULL default CURRENT_TIMESTAMP");
                column.add("`report_station` varchar(20) default ''");
                column.add("`report_operator` varchar(10) default ''");
                column.add("`report_issue` varchar(100) default ''");
                column.add("`report_program` varchar(20) default ''");
                column.add("`report_reason` varchar(200) default ''");
                column.add("`report_screenshot` varchar(100) default ''");

                column.add("PRIMARY KEY  (`id`)");
                column.add("KEY `Index_time`(`report_datetime`)");
            }


            //create views
            if ( !viewFound(pdb, "today_record") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_record` AS select `testdata`.`test_station` AS `station`,`testdata`.`test_datetime` AS `datetime`,`testdata`.`test_order` AS `order`,`testdata`.`test_quantity` AS `quantity`,`testdata`.`test_todo` AS `todo`,`testdata`.`test_pass` AS `pass`,`testdata`.`test_fail` AS `fail`,`testdata`.`id` AS `id` from `testdata` where (cast(`testdata`.`test_datetime` as date) = cast(now() as date)) order by `testdata`.`test_station`,`testdata`.`test_datetime`;");
            }

            if ( !viewFound(pdb, "today_order") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_order` AS select `today_record`.`station` AS `station`,`today_record`.`order` AS `order`,`today_record`.`quantity` AS `quantity`,max(`today_record`.`pass`) AS `pass`,max(`today_record`.`fail`) AS `fail` from `today_record` group by `today_record`.`order`,`today_record`.`quantity`,`today_record`.`station`;");
            }

            if ( !viewFound(pdb, "today_order_combine") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_order_combine` AS select `today_order`.`station` AS `station`,`today_order`.`order` AS `order`,max(`today_order`.`quantity`) AS `quantity`,sum(`today_order`.`pass`) AS `pass`,sum(`today_order`.`fail`) AS `fail` from `today_order` group by `today_order`.`order`,`today_order`.`station`;");
            }

            if ( !viewFound(pdb, "today_record_with_last") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_record_with_last` AS select `r`.`station` AS `station`,`r`.`datetime` AS `datetime`,`r`.`order` AS `order`,`r`.`quantity` AS `quantity`,`r`.`todo` AS `todo`,`r`.`pass` AS `pass`,`r`.`fail` AS `fail`,`r`.`id` AS `id`,(select `d`.`test_datetime` from `testdata` `d` where ((`r`.`station` = `d`.`test_station`) and (`r`.`order` = `d`.`test_order`) and (`r`.`quantity` = `d`.`test_quantity`) and ((((`r`.`todo` + 1) = `d`.`test_todo`) and (`r`.`pass` = (`d`.`test_pass` + 1)) and (`r`.`fail` = `d`.`test_fail`)) or ((`r`.`todo` = `d`.`test_todo`) and (`r`.`pass` = `d`.`test_pass`) and (`r`.`fail` = (`d`.`test_fail` + 1)))) and (`d`.`test_datetime` >= (`r`.`datetime` - interval 7 day)) and (`d`.`test_datetime` < `r`.`datetime`)) order by `d`.`id` desc limit 1) AS `last_datetime`,(select `d`.`test_pass` from `testdata` `d` where ((`r`.`station` = `d`.`test_station`) and (`r`.`order` = `d`.`test_order`) and (`r`.`quantity` = `d`.`test_quantity`) and ((((`r`.`todo` + 1) = `d`.`test_todo`) and (`r`.`pass` = (`d`.`test_pass` + 1)) and (`r`.`fail` = `d`.`test_fail`)) or ((`r`.`todo` = `d`.`test_todo`) and (`r`.`pass` = `d`.`test_pass`) and (`r`.`fail` = (`d`.`test_fail` + 1)))) and (`d`.`test_datetime` >= (`r`.`datetime` - interval 7 day)) and (`d`.`test_datetime` < `r`.`datetime`)) order by `d`.`id` desc limit 1) AS `last_pass`,(select `d`.`test_fail` from `testdata` `d` where ((`r`.`station` = `d`.`test_station`) and (`r`.`order` = `d`.`test_order`) and (`r`.`quantity` = `d`.`test_quantity`) and ((((`r`.`todo` + 1) = `d`.`test_todo`) and (`r`.`pass` = (`d`.`test_pass` + 1)) and (`r`.`fail` = `d`.`test_fail`)) or ((`r`.`todo` = `d`.`test_todo`) and (`r`.`pass` = `d`.`test_pass`) and (`r`.`fail` = (`d`.`test_fail` + 1)))) and (`d`.`test_datetime` >= (`r`.`datetime` - interval 7 day)) and (`d`.`test_datetime` < `r`.`datetime`)) order by `d`.`id` desc limit 1) AS `last_fail` from `today_record` `r` order by `r`.`station`,`r`.`datetime`;");
            }

            if ( !viewFound(pdb, "today_test_result") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_test_result` AS select `today_record_with_last`.`station` AS `station`,`today_record_with_last`.`datetime` AS `datetime`,`today_record_with_last`.`order` AS `order`,`today_record_with_last`.`quantity` AS `quantity`,`today_record_with_last`.`todo` AS `todo`,`today_record_with_last`.`pass` AS `pass`,`today_record_with_last`.`fail` AS `fail`,`today_record_with_last`.`id` AS `id`,`today_record_with_last`.`last_datetime` AS `last_datetime`,`today_record_with_last`.`last_pass` AS `last_pass`,`today_record_with_last`.`last_fail` AS `last_fail`,if((`today_record_with_last`.`pass` > coalesce(`today_record_with_last`.`last_pass`,0)),'Pass',if((`today_record_with_last`.`fail` > coalesce(`today_record_with_last`.`last_fail`,0)),'Fail','Unknown')) AS `result` from `today_record_with_last`;");
            }

            if ( !viewFound(pdb, "today_order_avg") )
            {
                pdb.getDb().updateSQL("DROP VIEW IF EXISTS `reng`.`today_order_avg`;\n" +
                                              "CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`today_order_avg` AS select `today_test_result`.`order` AS `order`,`today_test_result`.`station` AS `station`,`today_test_result`.`quantity` AS `quantity`,min(`today_test_result`.`datetime`) AS `start`,max(`today_test_result`.`datetime`) AS `end`,if((max(`today_test_result`.`pass`) >= 2),sec_to_time(ceiling((time_to_sec(timediff(max(`today_test_result`.`datetime`),min(`today_test_result`.`datetime`))) / (max(`today_test_result`.`pass`) - 1)))),0) AS `avg`,if((max(`today_test_result`.`pass`) >= 2),ceiling((time_to_sec(timediff(max(`today_test_result`.`datetime`),min(`today_test_result`.`datetime`))) / (max(`today_test_result`.`pass`) - 1))),0) AS `avgsec`,min(`today_test_result`.`todo`) AS `todo` from `today_test_result` where (`today_test_result`.`result` = 'Pass') group by `today_test_result`.`order`,`today_test_result`.`quantity`,`today_test_result`.`station`;");
            }


            if ( !viewFound(pdb, "testdata_thisweekcountrecord") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_thisweekcountrecord` AS select `testdata`.`id` AS `id`,`testdata`.`test_datetime` AS `test_datetime`,`testdata`.`test_station` AS `test_station`,`testdata`.`test_operator` AS `test_operator`,`testdata`.`test_wire_1` AS `test_wire_1`,`testdata`.`test_wire_2` AS `test_wire_2`,`testdata`.`test_order` AS `test_order`,`testdata`.`test_type` AS `test_type`,`testdata`.`test_class` AS `test_class`,`testdata`.`test_quantity` AS `test_quantity`,`testdata`.`test_todo` AS `test_todo`,`testdata`.`test_pass` AS `test_pass`,`testdata`.`test_fail` AS `test_fail`,`testdata`.`test_fail_at` AS `test_fail_at`,`testdata`.`profile_lux_on` AS `profile_lux_on`,`testdata`.`profile_voltage_on` AS `profile_voltage_on`,`testdata`.`profile_current_on` AS `profile_current_on`,`testdata`.`profile_power_on` AS `profile_power_on`,`testdata`.`profile_lux_off` AS `profile_lux_off`,`testdata`.`profile_voltage_off` AS `profile_voltage_off`,`testdata`.`profile_current_off` AS `profile_current_off`,`testdata`.`profile_power_off` AS `profile_power_off`,`testdata`.`value_insulation` AS `value_insulation`,`testdata`.`value_earth_1` AS `value_earth_1`,`testdata`.`value_earth_2` AS `value_earth_2`,`testdata`.`value_earth_3` AS `value_earth_3`,`testdata`.`value_lux_on` AS `value_lux_on`,`testdata`.`value_lux_off` AS `value_lux_off`,`testdata`.`value_voltage_on` AS `value_voltage_on`,`testdata`.`value_current_on` AS `value_current_on`,`testdata`.`value_power_on` AS `value_power_on`,`testdata`.`value_power_factor_on` AS `value_power_factor_on`,`testdata`.`value_frequency_on` AS `value_frequency_on`,`testdata`.`value_voltage_off` AS `value_voltage_off`,`testdata`.`value_current_off` AS `value_current_off`,`testdata`.`value_power_off` AS `value_power_off`,`testdata`.`value_power_factor_off` AS `value_power_factor_off`,`testdata`.`value_frequency_off` AS `value_frequency_off`,`testdata`.`value_dali_current_1` AS `value_dali_current_1`,`testdata`.`value_dali_current_2` AS `value_dali_current_2` from `testdata` where (yearweek(`testdata`.`test_datetime`,0) = yearweek(now(),0));");
            }

            if ( !viewFound(pdb, "testdata_thisweekworkorder") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_thisweekworkorder` AS select `testdata_thisweekcountrecord`.`test_order` AS `test_order`,`testdata_thisweekcountrecord`.`test_station` AS `test_station`,max(`testdata_thisweekcountrecord`.`test_datetime`) AS `datetime`,`testdata_thisweekcountrecord`.`test_quantity` AS `test_quantity`,max(`testdata_thisweekcountrecord`.`test_pass`) AS `pass`,max(`testdata_thisweekcountrecord`.`test_fail`) AS `fail` from `testdata_thisweekcountrecord` group by `testdata_thisweekcountrecord`.`test_order`,`testdata_thisweekcountrecord`.`test_quantity`,`testdata_thisweekcountrecord`.`test_station` order by min(`testdata_thisweekcountrecord`.`test_datetime`);");
            }

            if ( !viewFound(pdb, "testdata_thisweekcombineorder") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_thisweekcombineorder` AS select `testdata_thisweekworkorder`.`test_order` AS `test_order`,`testdata_thisweekworkorder`.`test_station` AS `test_station`,max(`testdata_thisweekworkorder`.`test_quantity`) AS `quantity`,sum(`testdata_thisweekworkorder`.`pass`) AS `pass`,sum(`testdata_thisweekworkorder`.`fail`) AS `fail` from `testdata_thisweekworkorder` group by `testdata_thisweekworkorder`.`test_order`,`testdata_thisweekworkorder`.`test_station`;");
            }


            if ( !viewFound(pdb, "testdata_lastweekcountrecord") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_lastweekcountrecord` AS select `testdata`.`id` AS `id`,`testdata`.`test_datetime` AS `test_datetime`,`testdata`.`test_station` AS `test_station`,`testdata`.`test_operator` AS `test_operator`,`testdata`.`test_wire_1` AS `test_wire_1`,`testdata`.`test_wire_2` AS `test_wire_2`,`testdata`.`test_order` AS `test_order`,`testdata`.`test_type` AS `test_type`,`testdata`.`test_class` AS `test_class`,`testdata`.`test_quantity` AS `test_quantity`,`testdata`.`test_todo` AS `test_todo`,`testdata`.`test_pass` AS `test_pass`,`testdata`.`test_fail` AS `test_fail`,`testdata`.`test_fail_at` AS `test_fail_at`,`testdata`.`profile_lux_on` AS `profile_lux_on`,`testdata`.`profile_voltage_on` AS `profile_voltage_on`,`testdata`.`profile_current_on` AS `profile_current_on`,`testdata`.`profile_power_on` AS `profile_power_on`,`testdata`.`profile_lux_off` AS `profile_lux_off`,`testdata`.`profile_voltage_off` AS `profile_voltage_off`,`testdata`.`profile_current_off` AS `profile_current_off`,`testdata`.`profile_power_off` AS `profile_power_off`,`testdata`.`value_insulation` AS `value_insulation`,`testdata`.`value_earth_1` AS `value_earth_1`,`testdata`.`value_earth_2` AS `value_earth_2`,`testdata`.`value_earth_3` AS `value_earth_3`,`testdata`.`value_lux_on` AS `value_lux_on`,`testdata`.`value_lux_off` AS `value_lux_off`,`testdata`.`value_voltage_on` AS `value_voltage_on`,`testdata`.`value_current_on` AS `value_current_on`,`testdata`.`value_power_on` AS `value_power_on`,`testdata`.`value_power_factor_on` AS `value_power_factor_on`,`testdata`.`value_frequency_on` AS `value_frequency_on`,`testdata`.`value_voltage_off` AS `value_voltage_off`,`testdata`.`value_current_off` AS `value_current_off`,`testdata`.`value_power_off` AS `value_power_off`,`testdata`.`value_power_factor_off` AS `value_power_factor_off`,`testdata`.`value_frequency_off` AS `value_frequency_off`,`testdata`.`value_dali_current_1` AS `value_dali_current_1`,`testdata`.`value_dali_current_2` AS `value_dali_current_2` from `testdata` where (yearweek(`testdata`.`test_datetime`,0) = yearweek((now() - interval 1 week),0));");
            }

            if ( !viewFound(pdb, "testdata_lastweekworkorder") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_lastweekworkorder` AS select `testdata_lastweekcountrecord`.`test_order` AS `test_order`,`testdata_lastweekcountrecord`.`test_station` AS `test_station`,max(`testdata_lastweekcountrecord`.`test_datetime`) AS `datetime`,`testdata_lastweekcountrecord`.`test_quantity` AS `test_quantity`,max(`testdata_lastweekcountrecord`.`test_pass`) AS `pass`,max(`testdata_lastweekcountrecord`.`test_fail`) AS `fail` from `testdata_lastweekcountrecord` group by `testdata_lastweekcountrecord`.`test_order`,`testdata_lastweekcountrecord`.`test_quantity`,`testdata_lastweekcountrecord`.`test_station` order by min(`testdata_lastweekcountrecord`.`test_datetime`);");
            }

            if ( !viewFound(pdb, "testdata_lastweekcombineorder") )
            {
                pdb.getDb().updateSQL("CREATE ALGORITHM=UNDEFINED DEFINER=`root`@`localhost` SQL SECURITY DEFINER VIEW  `reng`.`testdata_lastweekcombineorder` AS select `testdata_lastweekworkorder`.`test_order` AS `test_order`,`testdata_lastweekworkorder`.`test_station` AS `test_station`,max(`testdata_lastweekworkorder`.`test_quantity`) AS `quantity`,sum(`testdata_lastweekworkorder`.`pass`) AS `pass`,sum(`testdata_lastweekworkorder`.`fail`) AS `fail` from `testdata_lastweekworkorder` group by `testdata_lastweekworkorder`.`test_order`,`testdata_lastweekworkorder`.`test_station`;");
            }

//            if ( !viewFound( pdb, "today_record" ) )
//            {
//                pdb.getDb().updateSQL("");
//            }
        }
    }

    static boolean tableFound ( PoolDBConnection pdb, String table ) throws Exception
    {
        try ( ResultSet rs = pdb.getDb().querySQL("SHOW TABLES LIKE '" + table + "';") )
        {
            while ( rs.next() )
            {
                if ( table.equals(rs.getString(1)) ) return true;
            }
        }
        return false;
    }

    static boolean viewFound ( PoolDBConnection pdb, String view ) throws Exception
    {
        try ( ResultSet rs = pdb.getDb().querySQL("SELECT TABLE_NAME FROM information_schema.VIEWS WHERE TABLE_SCHEMA LIKE 'reng' AND TABLE_NAME = '" + view + "';") )
        {
            while ( rs.next() )
            {
                if ( view.equals(rs.getString(1)) ) return true;
            }
        }
        return false;
    }
}
