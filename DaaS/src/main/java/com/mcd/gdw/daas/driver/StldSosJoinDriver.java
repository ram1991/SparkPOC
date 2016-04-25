package com.mcd.gdw.daas.driver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.LazyOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.MultipleOutputs;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import com.mcd.gdw.daas.DaaSConstants;
import com.mcd.gdw.daas.abac.ABaC;
import com.mcd.gdw.daas.mapreduce.StldSosJoinPartitioner;
import com.mcd.gdw.daas.util.DaaSConfig;
import com.mcd.gdw.daas.util.HDFSUtil;
/**
 * 
 * @author Sateesh Pula
 * This is the driver class to join STLD/HDR* records to SOS records.
 * hadoop jar /home/mc32445/scripts/daasmapreduce.jar com.mcd.gdw.daas.driver.StldSosJoinDriver -Dmapred.job.queue.name=default \
 * /user/mc32445/poc/stldextract38tfiles/stld/HDR-m*,/user/mc32445/poc/sosextract38tfiles/sos \
 * /user/mc32445/poc/stldsosjoinoutput38tfiles \
 * $1 -- this is the number of reducers
 */

public class StldSosJoinDriver extends Configured implements Tool{

	DaaSConfig daasConfig;
	int prevJobGroupId = 0;
	int prevjobSeqNbr = 0;
	ABaC abac = null;
	private String createJobDetails = "true";
	
	
	public static class HDRSOSPathFilter implements PathFilter{

		@Override
		public boolean accept(Path path) {
			
			String pathName = path.toString().toUpperCase();
			String fileName = path.getName().toUpperCase();
			
			if( (pathName.endsWith("STLD") || fileName.startsWith("HDR") || fileName.startsWith("SOS") || pathName.endsWith("SOS") ))
				 return true;
			return false;
		}
		

	}
	
	
	@Override
	public int run(String[] argsall) throws Exception {
		
		
		int retCode = 0;
		try{
		
		GenericOptionsParser gop = new GenericOptionsParser(argsall);
		String[] args = gop.getRemainingArgs();
		Configuration conf = this.getConf();
//		
//		conf.addResource("/etc/hadoop/conf/core-site.xml");
//		conf.addResource("/etc/hadoop/conf/hdfs-site.xml");
//		conf.addResource("/etc/hadoop/conf/mapred-site.xml");
//		
		//AWS START
		//FileSystem fileSystem = FileSystem.get(conf);
		//AWS END
	
		int jobId = 0;
		
		
		String configXmlFile = "";
		String fileType = "";
		
		
		
		System.out.println( " gop.getRemainingArgs().length " + gop.getRemainingArgs().length);
		System.out.println(args.length);
		
		for (int idx2 = 0; idx2 < args.length; idx2++) {
			System.out.println (idx2 + " : "+args[idx2]);
		}
		
		String inputpath 	 	 = args[0];
		Path outputPath 	 	 = new Path(args[1]);
		int numReduceTasks   	 = Integer.parseInt(args[2]);
		configXmlFile 		 	 = args[3];
		fileType 	 		 	 = args[4];
		String salesandpmixpath  = args[5];
		createJobDetails 		 = args[6];
		String writeToPartitions = args[7];
		
		//AWS START
		System.out.println("Input Patameters:");
		System.out.println("inputpath         = " + inputpath);
		System.out.println("outputPath        = " + inputpath);
		System.out.println("numReduceTasks    = " + numReduceTasks);
		System.out.println("configXmlFile     = " + configXmlFile);
		System.out.println("fileType          = " + fileType);
		System.out.println("salesandpmixpath  = " + salesandpmixpath);
		System.out.println("createJobDetails  = " + createJobDetails);
		System.out.println("writeToPartitions = " + writeToPartitions + "\n\n");
		//AWS END
		
		daasConfig = new DaaSConfig(configXmlFile,fileType);

		//AWS START
		FileSystem fileSystem = HDFSUtil.getFileSystem(daasConfig, conf);
		//AWS END
		
		Job job = new Job(conf,"StldSosJoin");
		

		
//		if(args.length >=8 && args[7] != null){
//			prevjobSeqNbr = Integer.parseInt(args[7]);
//		}else{
//			prevjobSeqNbr = 2;
//		}
		
		 
		if(StringUtils.isBlank(createJobDetails)){
			createJobDetails = "TRUE";
		}
		
		if("TRUE".equalsIgnoreCase(createJobDetails)){
			
			abac = new ABaC(daasConfig);
//			
//			if(args.length >= 9 && args[8] != null){
//				prevJobGroupId = Integer.parseInt(args[8]);
//			}else{
//				
				prevJobGroupId = abac.getOpenJobGroupId(DaaSConstants.TDA_EXTRACT_JOBGROUP_NAME);
				if(prevJobGroupId == -1)
					prevJobGroupId = abac.createJobGroup(DaaSConstants.TDA_EXTRACT_JOBGROUP_NAME);
//			}
		
			jobId = abac.createJob(prevJobGroupId,++prevjobSeqNbr,job.getJobName());
		}
		job.setMapperClass(com.mcd.gdw.daas.mapreduce.StldSosJoinMapper.class);
		job.setReducerClass(com.mcd.gdw.daas.mapreduce.StldSosJoinReducer.class);
		job.setJarByClass(com.mcd.gdw.daas.driver.StldSosJoinDriver.class);
		
		job.setMapOutputKeyClass(Text.class);
		job.setMapOutputValueClass(Text.class);
		
		job.setOutputKeyClass(NullWritable.class);
		job.setOutputValueClass(Text.class);
		
		
		job.setInputFormatClass(TextInputFormat.class);
	
	
		
		FileInputFormat.setInputPathFilter(job,HDRSOSPathFilter.class);
		
		//loop thru comma separated input directories and set input paths
		String[] inputstrs = inputpath.split(",");
		Path[] inputpaths  = new Path[inputstrs.length];
		
		for(int i=0;i<inputstrs.length;i++){
			inputpaths[i] = new Path(inputstrs[i]);
			
			if(fileSystem.exists(new Path(inputstrs[i])))
				System.out.println(" path exists " + new Path(inputstrs[i]).toString());
			
		}
		FileInputFormat.setInputPaths(job, inputpaths);
//		FileInputFormat.addInputPath(job, new Path(inputpaths[0].toString()+"/HDR*"));
//		FileInputFormat.addInputPath(job, new Path(inputpaths[1].toString()+"/SOS*"));
		
//		FileInputFormat.addInputPaths(job, inputpath);
		
		HDFSUtil.removeHdfsSubDirIfExists(fileSystem, outputPath,true);

		//set output path
		FileOutputFormat.setOutputPath(job, outputPath);
		
		
		//set number of reducer tasks
		job.setNumReduceTasks(numReduceTasks);
		job.setPartitionerClass(StldSosJoinPartitioner.class);
		
		MultipleOutputs.addNamedOutput(job, "HDRSOSNOMatch", TextOutputFormat.class,
				Text.class, Text.class);
		
		FileStatus[] fstatus = null;
		FileStatus[] fstatustmp = null;
		String[] inputpathstrs = inputpath.split(",");
		
		
		for(String cachepathstr:inputpathstrs){
			
			fstatustmp = fileSystem.listStatus(new Path(cachepathstr), new HDRSOSPathFilter());
			fstatus = (FileStatus[])ArrayUtils.addAll(fstatus, fstatustmp);

		}
		
		String filepath;
		String datepart;
		String terrCdDatepart;
		HashSet<String> teddCdDtset = new HashSet<String>();
		String[] fileNameParts;
		String terrCd = "";
		for(FileStatus fstat:fstatus){
			filepath = fstat.getPath().toString();
			
			fileNameParts = filepath.split("RxD126");
			
			terrCd   = fileNameParts[1];
			datepart = fileNameParts[2].substring(0,8);
			
//			int lastindx = filepath.lastIndexOf("/");
//			
//			datepart = filepath.substring(lastindx+4,lastindx+12);
			teddCdDtset.add(terrCd+DaaSConstants.SPLCHARTILDE_DELIMITER+datepart);
		}
		Iterator<String> it = teddCdDtset.iterator();
		
		while(it.hasNext()){
			terrCdDatepart = it.next();
			System.out.println("datepart  "+terrCdDatepart);
			if(Integer.parseInt(terrCd) != 156){
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"0",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"1",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"2",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"3",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"4",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"5",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"6",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"7",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"8",TextOutputFormat.class, Text.class, Text.class);
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart+DaaSConstants.SPLCHARTILDE_DELIMITER+"9",TextOutputFormat.class, Text.class, Text.class);
			}else{
				MultipleOutputs.addNamedOutput(job,"SALES"+DaaSConstants.SPLCHARTILDE_DELIMITER+terrCdDatepart,TextOutputFormat.class, Text.class, Text.class);
			}
			
		}
		

		LazyOutputFormat.setOutputFormatClass(job, TextOutputFormat.class);
		
		retCode = job.waitForCompletion(true) ? 0 : 1;
		
		//move files
		fstatustmp = fileSystem.listStatus(outputPath,new PathFilter() {
			
			@Override
			public boolean accept(Path pathname) {
				if(pathname.getName().startsWith("SALES"))
					return true;
				return false;
			}
		});
		System.out.println(" num of output files at " + outputPath.toString() + " " +fstatustmp.length);
		String fileName;
		
		FsPermission fspermission =  new FsPermission(FsAction.ALL,FsAction.ALL,FsAction.ALL);
		
		fileSystem.setPermission(new Path(salesandpmixpath), fspermission);
		
		StringBuffer newfileName = new StringBuffer();
		
		HashSet<String> uniqueSet = new HashSet<String>();
		for(FileStatus fstat:fstatustmp){
//			fileName  = fstat.getPath().getName().replace("SALES", "SALES-");
			
			fileName  = fstat.getPath().getName();
			
			System.out.println(" fileName  " + fileName);
			
			newfileName.setLength(0);
			
			fileNameParts = fileName.split(DaaSConstants.SPLCHARTILDE_DELIMITER);
			
//			newfileName.append("SALES-").append(fileName.substring(5,13)).append("-").append(fileName.substring(13,14));
			newfileName.append("SALES-").append(fileNameParts[1]).append("-").append(fileNameParts[2]).append("-").append(fileNameParts[3].substring(0,1));
			
			System.out.println(" newfileName  " + newfileName.toString());
			
			fileName = newfileName.toString();
			
			String finalPath = salesandpmixpath+fileName;
			String datewithdashes = formatDateAsTsDtOnly(fileNameParts[2]);
			
			if("TRUE".equalsIgnoreCase(writeToPartitions)){
				if(!uniqueSet.contains(fileNameParts[1]+"-"+fileNameParts[2])){
				
					if(fileSystem.exists(new Path(salesandpmixpath+"type=SALES"+Path.SEPARATOR+"terr_cd="+fileNameParts[1]+Path.SEPARATOR+"pos_busn_dt="+datewithdashes))){
						fileSystem.delete(new Path(salesandpmixpath+"type=SALES"+Path.SEPARATOR+"terr_cd="+fileNameParts[1]+Path.SEPARATOR+"pos_busn_dt="+datewithdashes),true);
					}
					fileSystem.mkdirs(new Path(salesandpmixpath+"type=SALES"+Path.SEPARATOR+"terr_cd="+fileNameParts[1]+Path.SEPARATOR+"pos_busn_dt="+datewithdashes));
				}
				
				finalPath = salesandpmixpath+"type=SALES"+Path.SEPARATOR+"terr_cd="+fileNameParts[1]+Path.SEPARATOR+"pos_busn_dt="+datewithdashes+Path.SEPARATOR+fileName;
				uniqueSet.add(fileNameParts[1]+"-"+fileNameParts[2]);
			}
			
			if(!fileSystem.rename(fstat.getPath(), new Path(finalPath))){
			
				System.out.println("could not rename " + fstat.getPath().toString() + " to " +finalPath);
			}else {
				System.out.println("renamed " + fstat.getPath().toString() + " to " +finalPath);
				fileSystem.setPermission(new Path(finalPath), fspermission);
			}
		}
		
		
		
		//handle China APT file name
		fstatustmp = fileSystem.listStatus(new Path(salesandpmixpath),new PathFilter() {
			
			@Override
			public boolean accept(Path pathname) {
				if(pathname.getName().startsWith("GTDA"))
					return true;
				return false;
			}
		});
		System.out.println(" num of china output files at " + outputPath.toString() + " " +fstatustmp.length);
		Path destPath ;
		
		for(FileStatus fstat:fstatustmp){
//			fileName  = fstat.getPath().getName().replace("SALES", "SALES-");
			
			destPath = fstat.getPath().getParent();
			fileName  = fstat.getPath().getName();
			
			System.out.println(" fileName  " + fileName);
			
			newfileName.setLength(0);
			
			fileNameParts = fileName.split("-");
			
			newfileName.append(fileNameParts[0]);
			
			if(!fileSystem.rename(fstat.getPath(), new Path(destPath.toString()+"/"+newfileName.toString()))){
				
				System.out.println("could not rename " + fstat.getPath().toString() + " to " +destPath.toString()+"/"+newfileName.toString());
			}else {
				System.out.println(" renamed " + fstat.getPath().toString() + " to " +destPath.toString()+"/"+newfileName.toString());
				fileSystem.setPermission(new Path(destPath.toString()+"/"+newfileName.toString()), fspermission);
			}
			
			
		}
		
		
		if("TRUE".equalsIgnoreCase(createJobDetails)){
			abac.closeJob(jobId, DaaSConstants.JOB_SUCCESSFUL_ID, DaaSConstants.JOB_SUCCESSFUL_CD);
			
		}
		}catch(Exception ex){
			ex.printStackTrace();
		}finally{
			if(abac != null)
				abac.dispose();
		}
		
		return retCode;
	}
	
	private String formatDateAsTsDtOnly(String in) {

		String retTs = StringUtils.EMPTY;
		
		if ( in.length() >= 8 ) {
			retTs = in.substring(0, 4) + "-" + in.substring(4, 6) + "-" + in.substring(6, 8);
		}

		return(retTs);
		
	}
	
	public static void main(String[] args) throws Exception{
		int returnval = ToolRunner.run( new StldSosJoinDriver(), args);
		
		
		
		System.out.println( returnval);
	}

}