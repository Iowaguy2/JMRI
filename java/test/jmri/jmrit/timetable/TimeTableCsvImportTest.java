package jmri.jmrit.timetable;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import jmri.util.FileUtil;
import jmri.util.JUnitUtil;
import org.junit.*;

/**
 * Tests for the TimeTableCsvImport Class
 * @author Dave Sand Copyright (C) 2019
 */
public class TimeTableCsvImportTest {

    @Test
    public void testImport() {
        TimeTableDataManager dm = new TimeTableDataManager(false);
        TimeTableCsvImport imp = new TimeTableCsvImport();
        List<String> feedback = new ArrayList<>();
        try {
            File file = FileUtil.getFile("preference:TestCsvImport.csv");  // NOI18N
            createCsvFile(file);
            feedback = imp.importCsv(file);
        } catch (IOException ex) {
            log.error("Unable to test the CSV export process");  // NOI18N
            return;
        }

        // Verify results
        int layoutCount = 0;
        int typeCount = 0;
        int segmentCount = 0;
        int stationCount = 0;
        int scheduleCount = 0;
        int trainCount = 0;
        int stopCount = 0;

        for (Layout layout : dm.getLayouts(true)) {
            layoutCount++;
            int layoutId = layout.getLayoutId();
            for (TrainType type : dm.getTrainTypes(layoutId, true)) {
                typeCount++;
            }
            for (Segment segment : dm.getSegments(layoutId, true)) {
                segmentCount++;
                for (Station station : dm.getStations(segment.getSegmentId(), true)) {
                    stationCount++;
                }
            }
            for (Schedule schedule : dm.getSchedules(layoutId, true)) {
                scheduleCount++;
                for (Train train : dm.getTrains(schedule.getScheduleId(), 0, true)) {
                    trainCount++;
                    for (Stop stop : dm.getStops(train.getTrainId(), 0, true)) {
                        stopCount++;
                    }
                }
            }
            Assert.assertEquals("Layouts:", 1, layoutCount);
            Assert.assertEquals("Types:", 1, typeCount);
            Assert.assertEquals("Segments:", 1, segmentCount);
            Assert.assertEquals("Stations:", 3, stationCount);
            Assert.assertEquals("Schedules:", 1, scheduleCount);
            Assert.assertEquals("Trains:", 2, trainCount);
            Assert.assertEquals("Stops:", 6, stopCount);
        }
    }

    public void createCsvFile(File file) {
        // Create a test CSV file for the import test
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file.getAbsolutePath()), "utf-8"))) {
           writer.write("Layout,Test Layout,N,6,0,Yes\n");
           writer.write("TrainType,Freight,123123\n");
           writer.write("Segment,Mainline\n");
           writer.write("Station\n");
           writer.write("Station,Station 2,12,Yes,1,0\n");
           writer.write("Station,Station 3,32,No,1,0\n");
           writer.write("Schedule,,,6,12\n");
           writer.write("Train,XYZ,Test 1,1,30,420\n");
           writer.write("Stop,1,,25\n");
           writer.write("Stop,2,30,Meet QRS\n");
           writer.write("Stop,3\n");
           writer.write("Train,QRS,Test 2,1,30,420\n");
           writer.write("Stop,3,,25\n");
           writer.write("Stop,2,30,Meet XYZ\n");
           writer.write("Stop,1\n");
           writer.close();
        } catch (IOException ex) {
            log.warn("Unable to create the test import CSV file ", ex);
        }
    }

    @Before
    public void setUp() {
        jmri.util.JUnitUtil.setUp();

        JUnitUtil.resetInstanceManager();
        JUnitUtil.resetProfileManager();
    }

    @After
    public void tearDown() {
        jmri.util.JUnitUtil.tearDown();
    }
    private final static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TimeTableCsvImportTest.class);
}
