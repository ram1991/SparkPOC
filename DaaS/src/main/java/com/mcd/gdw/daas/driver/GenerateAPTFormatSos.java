package com.mcd.gdw.daas.driver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured; 
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import com.mcd.gdw.daas.DaaSConstants;
import com.mcd.gdw.daas.abac.ABaC;
import com.mcd.gdw.daas.util.DaaSConfig;
import com.mcd.gdw.daas.util.HDFSUtil;
import com.mcd.gdw.daas.mapreduce.NpAPTSosXmlMapper;



/**
 * @author KhajaAsmath This MapReduce job extracts the Cook and Wait Time for US
 *         Stores. It uses NpAPTSosXmlMapper and NpAPTSosXmlReducer to implement
 *         the Map and Reduce steps, respectively. When you run this class you
 *         must supply it with three parameters: -c config.xml,-t POS_XML and -d
 *         WORK:840 The output will be stored in Pipe seperated file format.
 */

public class GenerateAPTFormatSos extends Configured implements Tool {

	private ArrayList<String> workTerrCodeList = new ArrayList<String>();
	// private String createJobDetails = "FALSE";
	private final static String APT_EXTRACT_JOBGROUP_NAME = "APT Cook and Wait Time Extract";
	private final static String APT_EXTRACT_HISTORY_JOBGROUP_NAME = "APT Cook and Wait Time History Extract";
	private String fileSeperator = "/";
	private String fileNameSeperator = "_";
	private int jobSeqNbr = 1;
	private int jobGroupId = 0;
	int jobId = 0;
	int totalRecordsInOutputFile = -1;
	//Set<String> terrCodes = new HashSet<String>();
	static final String JOB_SUCCESSFUL_CD = "SUCCESSFUL";
	String cacheFile = "";
	private FsPermission newFilePremission = null;
	private int jobStatus = 0;
	public static String timeStamp;
	public static String dateInOutputPath;
	public static final String OUTPUT_FILE_TYPE = "Pipe Separated File";
	public static final String OUTPUT_FILE_DETAIL_NAME = "APT - Cook and Wait Time Detail";
	public static final String APTUS_STLD_TDA_Header = "APTSTLDHDR";
	Path aptOutputPath = null;
	String filterOnStoreId = "FALSE";
	public static final String STORE_FILTER_LIST = "STORE_FILTER.txt";
	public static final String APTCOOK_COUNTER = "count";
	public static String UNDERSCORE_DELIMITER   = "_";
	public static String HYPHEN_DELIMITER="-";
	public static String TILDE_DELIMITER   = "~";
	public static final String APTUS_SOS_TDA_Detail = "APTUS_CookWaitTime";
	public static final String APTUS_FILE_STATUS="FileStatus";
	HashMap<String, String> recordCTStoreMap = new HashMap<String, String>();
	String configXmlFile = "";
	String fileType = "";
	String terrDate = "";
	// String terrDateFile = "";
	String owshFltr = "*";
	String[] args;
	

	public GenerateAPTFormatSos() {
		Date currentDate = new Date();
		SimpleDateFormat customDateTimeFormat = DaaSConstants.SDF_yyyyMMddHHmmssSSSnodashes;
		timeStamp = customDateTimeFormat.format(currentDate);

		SimpleDateFormat customDateFormat = DaaSConstants.SDF_yyyyMMdd;
		dateInOutputPath = customDateFormat.format(currentDate);
	}

	public static void main(String[] args) throws Exception {

		ToolRunner.run(new Configuration(), new GenerateAPTFormatSos(), args);
	}

	@Override
	public int run(String[] argsall) throws Exception {

		GenericOptionsParser gop = new GenericOptionsParser(argsall);

		args = gop.getRemainingArgs();

		for (int idx = 0; idx < args.length; idx++) {
			if (args[idx].equals("-c") && (idx + 1) < args.length) {
				configXmlFile = args[idx + 1];
			}

			if (args[idx].equals("-t") && (idx + 1) < args.length) {
				fileType = args[idx + 1];
			}

			if (args[idx].equals("-d") && (idx + 1) < args.length) {
				terrDate = args[idx + 1];
			}			
				
			if (args[idx].equalsIgnoreCase("-dc")
					&& (idx + 1) < args.length) {
				cacheFile = args[idx + 1];
			}
			if (args[idx].equals("-owshfltr") && (idx + 1) < args.length) {
				owshFltr = args[idx + 1];
			}
			if (args[idx].equals("-filterOnStoreId") && (idx + 1) < args.length) {
				filterOnStoreId = args[idx + 1];
			}

		}

		if (configXmlFile.length() == 0 || fileType.length() == 0
				|| terrDate.length() == 0) {
			System.err.println("Invalid parameters");
			System.err
					.println("Usage: GenerateAPTFormatSos -c config.xml -t filetype -d territoryDateParms");
			System.exit(8);
		}

		DaaSConfig daasConfig = new DaaSConfig(configXmlFile, fileType);
		newFilePremission = new FsPermission(FsAction.ALL, FsAction.ALL,
				FsAction.READ_EXECUTE);

		if (daasConfig.configValid()) {

			runMrAPTSOSExtract(daasConfig, fileType, getConf(), terrDate,
					owshFltr);

		} else {
			System.err.println("Invalid config.xml and/or filetype");
			System.err.println("Config File = " + configXmlFile);
			System.err.println("File Type   = " + fileType);
			System.exit(8);
		}

		return (0);
	}

	/**
	 * Method which runs Map Reduce job.
	 * 
	 * @param daasConfig
	 *            : Configuration object which loads ABaC, teradata and Hadoop
	 *            Paths
	 * @param fileType
	 *            : File type for this job. POS_XML
	 * @param hdfsConfig
	 *            : Map Reduce Configuration Object
	 * @param terrDate
	 *            : Parameter for generating Input Path i.e. either Work or Gold
	 *            Layer
	 * @param owshFltr
	 *            : Ownership filter
	 * @throws Exception
	 *             :
	 */
	private void runMrAPTSOSExtract(DaaSConfig daasConfig, String fileType,
			Configuration hdfsConfig, String terrDate, String owshFltr)
			throws Exception {

		Job job;
		ArrayList<Path> requestedPaths = null;

		ArrayList<String> subTypeList = new ArrayList<String>();
		subTypeList.add("DetailedSOS");
		// subTypeList.add("STLD");

		ABaC abac = null;
		try {

			hdfsConfig.set(DaaSConstants.JOB_CONFIG_PARM_OWNERSHIP_FILTER,
					owshFltr);
			String jobTitle = "";

			if (!terrDate.toUpperCase().startsWith("WORK")) {
				jobTitle = APT_EXTRACT_HISTORY_JOBGROUP_NAME;
			} else {
				jobTitle = APT_EXTRACT_JOBGROUP_NAME;
			}

			System.out.println("\nCreate APT File Format\n");

			//AWS START
			//FileSystem fileSystem = FileSystem.get(hdfsConfig);
			FileSystem fileSystem = HDFSUtil.getFileSystem(daasConfig, hdfsConfig);
			//AWS END


			// Define output path based on input path i.e. Work or Gold Layer.

			aptOutputPath = new Path(daasConfig.hdfsRoot() + Path.SEPARATOR
					+ daasConfig.hdfsWorkSubDir() + Path.SEPARATOR
					+ "APTCookTimeSosExtract");

			HDFSUtil.removeHdfsSubDirIfExists(fileSystem, aptOutputPath,
					daasConfig.displayMsgs());

			/*
			 * M-1939 : Add Input Paths based on the Parameter. Path for Work
			 * Layer: WORK:840 Path for History Extract:
			 * 840:20120701,840:2012-07-05:2012-07-08,250:2012-08-01
			 */
			if (terrDate.toUpperCase().startsWith("WORK")) {
				String[] workParts = (terrDate + ":").split(":");
				String filterTerrCodeList = workParts[1];

				if (filterTerrCodeList.length() > 0) {
					System.out
							.println("Work Layer using only the following Territory Codes:");
					String[] parts = filterTerrCodeList.split(",");
					for (String addTerrCode : parts) {
						System.out.println("    " + addTerrCode);
						workTerrCodeList.add(addTerrCode);
					}
				}
				requestedPaths = getVaildFilePaths(daasConfig, fileSystem,
						fileType, subTypeList);
				/*System.out
						.println("Total number of Input Paths from Work Layer : "
								+ requestedPaths.size());
				terrCodes = new HashSet<String>(workTerrCodeList);*/
			} else {
				requestedPaths = getVaildFilePaths(daasConfig, fileSystem,
						fileType, terrDate, subTypeList);
				System.out
						.println("Total number of Input Paths from Gold Layer : "
								+ requestedPaths.size());
			/*	ArrayList<String> goldTerrCodes = goldTerrCodeList(daasConfig,
						fileSystem, terrDate, subTypeList);
				terrCodes = new HashSet<String>(goldTerrCodes);*/
			}

			Configuration conf = getConf();
			conf.set(DaaSConstants.JOB_CONFIG_PARM_STORE_FILTER,
					filterOnStoreId);
			
			job = Job.getInstance(conf, jobTitle);

			Path storeFilerDistCache = new Path(daasConfig.hdfsRoot()
					+ Path.SEPARATOR + "distcachefiles" + Path.SEPARATOR
					+ "STORE_FILTER.txt");
			job.addCacheFile(new URI(storeFilerDistCache.toString() + "#"
					+ storeFilerDistCache.getName()));
			job.setJarByClass(GenerateAPTFormatSos.class);
			job.setMapperClass(NpAPTSosXmlMapper.class);
			job.setNumReduceTasks(0);
			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);
			TextOutputFormat.setOutputPath(job, aptOutputPath);
			LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
			abac = new ABaC(daasConfig);
			try {
				jobGroupId = abac.createJobGroup(jobTitle);
				jobId = abac.createJob(jobGroupId, jobSeqNbr, job.getJobName());
			} catch (Exception ex) {
				abac.closeJobGroup(jobGroupId, DaaSConstants.JOB_FAILURE_ID,
						DaaSConstants.JOB_FAILURE_CD);
				System.out.println(ex.getMessage());
				System.exit(1);
			}

			/*HashSet<String> terrCdDtset = new HashSet<String>();

			String datepart;
			String terrCdDatepart;
			String[] fileNameParts;
			FileStatus[] fstatus = null;
			FileStatus[] fstatustmp = null;

			for (Path cachepathstr : requestedPaths) {
				FileInputFormat.addInputPath(job, cachepathstr);
				fstatustmp = fileSystem.globStatus(new Path(cachepathstr
						+ "/DetailedSOS*"));
				fstatus = (FileStatus[]) ArrayUtils.addAll(fstatus, fstatustmp);

			}

			for (FileStatus fstat : fstatus) {
				String fileName = fstat.getPath().getName().toUpperCase();
				System.out.println(" file name " + fileName);

				if (fstat.isDirectory())
					continue;
				fileNameParts = HDFSUtil.restoreMultiOutSpecialChars(fileName).split(TILDE_DELIMITER);
				String terrCdfrmFileName = fileNameParts[1];
				datepart = fileNameParts[2].substring(0, 8);
				terrCdDtset.add(terrCdfrmFileName
						+ TILDE_DELIMITER + datepart);

			}

			if (terrCdDtset.size() == 0) {
				System.err.println("Stopping, No valid files found");
				System.exit(8);
			}

			Iterator<String> it = terrCdDtset.iterator();

			String terrCd = "";
			while (it.hasNext()) {
				// datepart = it.next();
				terrCdDatepart = it.next();
				terrCd = terrCdDatepart
						.split(TILDE_DELIMITER)[0];
				datepart = terrCdDatepart
						.split(TILDE_DELIMITER)[1];
				
				MultipleOutputs.addNamedOutput(job,HDFSUtil.replaceMultiOutSpecialChars(APTUS_SOS_TDA_Detail
								+ UNDERSCORE_DELIMITER + terrCd
								+ UNDERSCORE_DELIMITER 
								+ datepart), TextOutputFormat.class, Text.class,
						Text.class);

			}		*/
			for (Path outPath : requestedPaths) {
				FileInputFormat.addInputPath(job, outPath);
			}
			
			MultipleOutputs.addNamedOutput(job,HDFSUtil.replaceMultiOutSpecialChars(APTUS_SOS_TDA_Detail), TextOutputFormat.class, Text.class,
			Text.class);
			MultipleOutputs.addNamedOutput(job,HDFSUtil.replaceMultiOutSpecialChars(APTUS_FILE_STATUS), TextOutputFormat.class, Text.class,
					Text.class);
			jobStatus = job.waitForCompletion(true) ? 0 : 1;

			if (jobStatus == 0) {
				FileStatus fs[] = fileSystem.listStatus(aptOutputPath);
							
				int countIndex=1;
				for (int fileCounter = 0; fileCounter < fs.length; fileCounter++) {					
					if (HDFSUtil.restoreMultiOutSpecialChars(fs[fileCounter].getPath().getName())
							.startsWith(APTUS_FILE_STATUS)) {
						//BufferedReader br =  new BufferedReader(new FileReader("./" + aptOutputPath+fileSeperator+fs[fileCounter].getPath().getName().toString()));
						BufferedReader br = new BufferedReader(new InputStreamReader(fileSystem.open(fs[fileCounter].getPath())));
						addKeyValuestoMap(recordCTStoreMap, br);
						
						
					}
				}						
				for (int fileCounter = 0; fileCounter < fs.length; fileCounter++) {					
					if (HDFSUtil.restoreMultiOutSpecialChars(fs[fileCounter].getPath().getName())
							.startsWith(APTUS_SOS_TDA_Detail)) {																	
						String outputFile = HDFSUtil.restoreMultiOutSpecialChars(fs[fileCounter].getPath().getName());
						String outputFileName=outputFile.split(GenerateAPTFormatSos.HYPHEN_DELIMITER)[0]+ fileNameSeperator + timeStamp
								+ fileNameSeperator + countIndex
								+ ".psv";
						int storeCount;
						int recordCount;	
						recordCount=Integer.parseInt(recordCTStoreMap.get(outputFile).split("\\|",-1)[0]);
						storeCount=Integer.parseInt(recordCTStoreMap.get(outputFile).split("\\|",-1)[1]);
						fileSystem.rename(fs[fileCounter].getPath(), new Path(aptOutputPath + fileSeperator+outputFileName));
						abac.insertExecutionTargetFile(jobId, countIndex,outputFileName,
								OUTPUT_FILE_DETAIL_NAME, OUTPUT_FILE_TYPE,recordCount,storeCount);
						countIndex++;
						
					} else {
						// fileSystem.delete(fs[fileCounter].getPath());
						fileSystem.delete(fs[fileCounter].getPath(), false);
					}
				}

				fileSystem.setPermission(aptOutputPath, newFilePremission);
				

			}

		} catch (Exception ex) {
			ex.printStackTrace();
			abac.closeJobGroup(jobGroupId, DaaSConstants.JOB_FAILURE_ID,
					DaaSConstants.JOB_FAILURE_CD);
			abac.closeJob(jobId, DaaSConstants.JOB_FAILURE_ID,
					DaaSConstants.JOB_FAILURE_CD);

			System.exit(1);
		} finally {
			if (abac != null) {
				abac.closeJob(jobId, DaaSConstants.JOB_SUCCESSFUL_ID,
						DaaSConstants.JOB_SUCCESSFUL_CD);
				abac.closeJobGroup(jobGroupId, DaaSConstants.JOB_SUCCESSFUL_ID,
						DaaSConstants.JOB_SUCCESSFUL_CD);
				abac.dispose();
				
			}
		}

	}

	/**
	 * This Method gets valid File Paths from the Work layer. -d WORK:840 gets
	 * all the files from WorkLayer whose terrcode is 840.
	 * 
	 * @param daasConfig
	 *            :Configuration object which loads ABaC, teradata and Hadoop
	 *            Paths
	 * @param fileSystem
	 *            Hadoop FileSystem
	 * @param fileType
	 *            FileType for the paths i.e. POS_XML
	 * @param subTypeCodes
	 *            STLD files for Offer Redemption Extract. Other codes are
	 *            ignored.
	 * @return
	 */
	private ArrayList<Path> getVaildFilePaths(DaaSConfig daasConfig,
			FileSystem fileSystem, String fileType,
			ArrayList<String> subTypeCodes) {

		ArrayList<Path> retPaths = new ArrayList<Path>();
		String filePath;
		boolean useFilePath;
		boolean removeFilePath;
		String[] fileNameParts;
		String fileTerrCode;

		Path listPath = new Path(daasConfig.hdfsRoot() + Path.SEPARATOR
				+ daasConfig.hdfsWorkSubDir() + Path.SEPARATOR
				+ daasConfig.fileSubDir() + Path.SEPARATOR + "step1");

		try {
			FileStatus[] fstus = fileSystem.listStatus(listPath);

			for (int idx = 0; idx < fstus.length; idx++) {
				filePath = HDFSUtil.restoreMultiOutSpecialChars(fstus[idx]
						.getPath().getName());

				useFilePath = false;
				for (int idxCode = 0; idxCode < subTypeCodes.size(); idxCode++) {
					if (filePath.startsWith(subTypeCodes.get(idxCode))) {
						useFilePath = true;
					}
				}

				if (useFilePath && workTerrCodeList.size() > 0) {
					fileNameParts = filePath.split("~");
					fileTerrCode = fileNameParts[1];

					removeFilePath = true;

					for (String checkTerrCode : workTerrCodeList) {
						if (fileTerrCode.equals(checkTerrCode)) {
							removeFilePath = false;
						}
					}

					if (removeFilePath) {
						useFilePath = false;
					}
				}

				// if ( filePath.startsWith("STLD") ||
				// filePath.startsWith("DetailedSOS") ||
				// filePath.startsWith("MenuItem") ||
				// filePath.startsWith("SecurityData") ||
				// filePath.startsWith("store-db") ||
				// filePath.startsWith("product-db") ) {
				if (useFilePath) {
					retPaths.add(fstus[idx].getPath());

					if (daasConfig.verboseLevel == DaaSConfig.VerboseLevelType.Maximum) {
						System.out.println("Added work source file ="
								+ filePath);
					}
				}
			}

		} catch (Exception ex) {
			System.err
					.println("Error occured in GenerateAPTFormatSos.getVaildFilePaths:");
			ex.printStackTrace(System.err);
			System.exit(8);
		}

		if (retPaths.size() == 0) {
			System.err.println("Stopping, No valid files found");
			System.exit(8);
		}

		return (retPaths);
	}

	/**
	 * This Method gets valid File Paths from the Gold layer.
	 * 840:20120701,840:2012-07-05:2012-07-08,250:2012-08-01. This will get a
	 * total of 3 days for 840 and 1 day from 250
	 * 
	 * @param daasConfig
	 *            Configuration object which loads ABaC, teradata and Hadoop
	 *            Paths
	 * @param fileSystem
	 *            Hadoop FileSystem
	 * @param fileType
	 *            FileType for the paths i.e. POS_XML
	 * @param requestedTerrDateParms
	 * @param subTypeCodes
	 * @return
	 */
	private ArrayList<Path> getVaildFilePaths(DaaSConfig daasConfig,
			FileSystem fileSystem, String fileType,
			String requestedTerrDateParms, ArrayList<String> subTypeCodes) {

		ArrayList<Path> retPaths = new ArrayList<Path>();

		try {

			// Path[] requestPaths = HDFSUtil.requestedArgsPaths(fileSystem,
			// daasConfig, requestedTerrDateParms, "STLD", "DetailedSOS",
			// "MenuItem", "SecurityData","store-db","product-db");
			Path[] requestPaths = HDFSUtil.requestedArgsPaths(fileSystem,
					daasConfig, requestedTerrDateParms, subTypeCodes);

			if (requestPaths == null) {
				System.err
						.println("Stopping, No valid territory/date params provided");
				System.exit(8);
			}

			int validCount = 0;

			for (int idx = 0; idx < requestPaths.length; idx++) {
				if (fileSystem.exists(requestPaths[idx])) {
					retPaths.add(requestPaths[idx]);
					validCount++;

					if (daasConfig.verboseLevel == DaaSConfig.VerboseLevelType.Maximum) {
						System.out.println("Found valid path = "
								+ requestPaths[idx].toString());
					}
				} else {
					System.err.println("Invalid path \""
							+ requestPaths[idx].toString() + "\" skipping.");
				}
			}

			if (validCount == 0) {
				System.err.println("Stopping, No valid files found");
				System.exit(8);
			}

			if (daasConfig.displayMsgs()) {
				System.out.print("\nFound " + validCount + " HDFS path");
				if (validCount > 1) {
					System.out.print("s");
				}
				System.out.print(" from " + requestPaths.length + " path");
				if (requestPaths.length > 1) {
					System.out.println("s.");
				} else {
					System.out.println(".");
				}
			}

			if (daasConfig.displayMsgs()) {
				System.out.println("\n");
			}

		} catch (Exception ex) {
			System.err
					.println("Error occured in GenerateAPTFormatSos.getVaildFilePaths:");
			ex.printStackTrace(System.err);
			System.exit(8);
		}

		return (retPaths);
	}

	
	
	//Adds key values pairs to the provided hashmap and closes the buffered reader
	  private void addKeyValuestoMap(HashMap<String,String> keyvalMap,BufferedReader br){
		  
		  
		  try { 
              
		      String line = "";
		      while ( (line = br.readLine() )!= null) {
		        String[] flds = line.split("\\|");
		        if (flds.length == 3) {
		        	keyvalMap.put(flds[0].trim(), flds[1].trim()+DaaSConstants.PIPE_DELIMITER+flds[2].trim());
		        }   
		      }
		    } catch (IOException e) { 
		      e.printStackTrace();
		      System.out.println("read from distributed cache: read length and instances");
		    }finally{
		    	try{
		    		if(br != null)
		    			br.close();
		    	}catch(Exception ex){
		    		ex.printStackTrace();
		    	}
		    }
	  }


}