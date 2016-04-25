package com.mcd.gdw.test.daas.driver;

import java.util.ArrayList;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.mcd.gdw.daas.util.DaaSConfig;
import com.mcd.gdw.daas.util.HDFSUtil;
import com.mcd.gdw.test.daas.mapreduce.VerifyXMLMapper;

public class VerifyXML extends Configured implements Tool {

	private FileSystem fileSystem = null;
	private Configuration hdfsConfig = null;
	private Path baseOutputPath = null;

	public static void main(String[] args) throws Exception  {
		
		Configuration hdfsConfig = new Configuration();
		
		int retval = ToolRunner.run(hdfsConfig,new VerifyXML(), args);

		System.out.println(" return value : " + retval);

	}
	
	public int run(String[] args) throws Exception {

		String configXmlFile = "";
		String fileType = "";
		String terrDate = "";

		for ( int idx=0; idx < args.length; idx++ ) {
			if ( args[idx].equals("-c") && (idx+1) < args.length ) {
				configXmlFile = args[idx+1];
			}

			if ( args[idx].equals("-t") && (idx+1) < args.length ) {
				fileType = args[idx+1];
			}

			if ( args[idx].equals("-d") && (idx+1) < args.length ) {
				terrDate = args[idx+1];
			}
		}

		if ( configXmlFile.length() == 0 || fileType.length() == 0 || terrDate.length() == 0 ) {
			System.err.println("Missing config.xml (-c), filetype (t), territoryDateParms (-d)");
			System.err.println("Usage: VerifyXML -c config.xml -t filetype -d territoryDateParms");
			System.err.println("where territoryDateParm is a comma separated list of territory codes and dates separated by colons(:)");
			System.err.println("for example, 840:2012-07-01:2012-07-07 is territory 840 from July 1st, 2012 until July 7th, 2012.");
			System.err.println("the date format is either ISO YYYY-MM-DD or YYYYMMDD (both are valid)");
			System.err.println("If only one date is supplied then a single day is used for that territory");
			System.err.println("Multiple territoryDateParm can be specified as comma separated values: 840:20120701,840:2012-07-05:2012-07-08,250:2012-08-01");
			System.err.println("This will get a total of 3 days for 840 and 1 day from 250");
			System.exit(8);
		}

		DaaSConfig daasConfig = new DaaSConfig(configXmlFile, fileType);

		if ( daasConfig.configValid() ) {
			
			if ( daasConfig.displayMsgs()  ) {
				System.out.println(daasConfig.toString());
			}

			hdfsConfig = getConf();
			fileSystem = FileSystem.get(hdfsConfig);

			runJob(daasConfig,fileType,terrDate);
			
		} else {
			System.err.println("Invalid Config XML file, stopping");
			System.err.println(daasConfig.errText());
			System.exit(8);
		}
	
		return(0);
	}
	
	private void runJob(DaaSConfig daasConfig
                       ,String fileType
                       ,String terrDate) throws Exception {
		
		Job job;
		ArrayList<Path> requestedPaths;

		job = Job.getInstance(hdfsConfig, "Verify XML");
		
		baseOutputPath = new Path(daasConfig.hdfsRoot() + Path.SEPARATOR + daasConfig.hdfsWorkSubDir() + Path.SEPARATOR + "VerifyXML");
		HDFSUtil.removeHdfsSubDirIfExists(fileSystem, baseOutputPath,daasConfig.displayMsgs());

		if ( daasConfig.displayMsgs() ) {
			System.out.println("\nOutput path = " + baseOutputPath.toString() + "\n");
		}
		
		hdfsConfig.set("mapred.child.java.opts", daasConfig.fileMapReduceJavaHeapSizeParm()); 
		
		requestedPaths = getVaildFilePaths(daasConfig,fileType,terrDate);
		
		for (Path addPath : requestedPaths ) {
			FileInputFormat.addInputPath(job, addPath);
		}

		LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
		
		job.setJarByClass(VerifyXML.class);
		job.setMapperClass(VerifyXMLMapper.class);
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		//job.setOutputKeyClass(NullWritable.class);
		job.setOutputKeyClass(Text.class);
		TextOutputFormat.setOutputPath(job, baseOutputPath);

		if ( ! job.waitForCompletion(true) ) {
			System.err.println("Error occured in MapReduce process, stopping");
			System.exit(8);
		}
		
	}
	private ArrayList<Path> getVaildFilePaths(DaaSConfig daasConfig
            ,String fileType
            ,String requestedTerrDateParms) {

		ArrayList<Path> retPaths = new ArrayList<Path>();

		try {

			Path[] requestPaths = HDFSUtil.requestedArgsPaths(fileSystem, daasConfig, requestedTerrDateParms, "STLD", "DetailedSOS", "MenuItem", "SecurityData", "Store-Db", "Product-Db");

			if ( requestPaths == null ) {
				System.err.println("Stopping, No valid territory/date params provided");
				System.exit(8);
			}

			int validCount = 0;

			for ( int idx=0; idx < requestPaths.length; idx++ ) {
				if ( fileSystem.exists(requestPaths[idx]) ) {
					retPaths.add(requestPaths[idx]);
					validCount++;

					if ( daasConfig.verboseLevel == DaaSConfig.VerboseLevelType.Maximum ) {
						System.out.println("Found valid path = " + requestPaths[idx].toString());
					}
				} else {
					System.err.println("Invalid path \"" + requestPaths[idx].toString() + "\" skipping.");
				}
			}
			
			if ( validCount == 0 ) {
				System.err.println("Stopping, No valid files found");
				System.exit(8);
			}
			
			if ( daasConfig.displayMsgs() ) {
				System.out.print("\nFound " + validCount + " HDFS path");
				if ( validCount > 1 ) {
					System.out.print("s");
				}
				System.out.print(" from " + requestPaths.length + " path");
				if ( requestPaths.length > 1 ) {
					System.out.println("s.");
				} else {
					System.out.println(".");
				}
			}
			
			if ( daasConfig.displayMsgs() ) {
				System.out.println("\n");
			}
			
		} catch (Exception ex) {
			System.err.println("Error occured in VerifyXML.getVaildFilePaths:");
			System.err.println(ex.toString());
			ex.printStackTrace();
			System.exit(8);
		}

		return(retPaths);

	}
}